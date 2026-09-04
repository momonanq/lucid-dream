package com.luciddream.wear.service

import com.luciddream.algorithm.NightCueDecisionEngine
import com.luciddream.algorithm.RemConfidenceEngine
import com.luciddream.data.sync.CueTriggeredPayload
import com.luciddream.data.sync.StartSessionPayload
import com.luciddream.data.sync.WakeSpikePayload
import com.luciddream.model.*
import com.luciddream.wear.haptic.WatchHapticEngine
import com.luciddream.wear.sensor.SamsungSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Service managing nocturnal sensor collection, on-watch confidence heuristics,
 * haptic actuation, and wake-spike surveillance.
 */
class WatchNightTrackingService(
    private val sensorManager: SamsungSensorManager,
    private val hapticEngine: WatchHapticEngine,
    private val confidenceEngine: RemConfidenceEngine = RemConfidenceEngine(),
    private val decisionEngine: NightCueDecisionEngine = NightCueDecisionEngine(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {

    private val _currentSession = MutableStateFlow<NightSession?>(null)
    val currentSession: StateFlow<NightSession?> = _currentSession.asStateFlow()

    private val recentWindows = mutableListOf<SensorWindow>()
    private val triggeredCues = mutableListOf<CueEvent>()
    private var lastDeliveredCue: CueEvent? = null
    private var preCueWindow: SensorWindow? = null

    val onCueTriggeredCallbacks = mutableListOf<suspend (CueTriggeredPayload) -> Unit>()
    val onWakeSpikeCallbacks = mutableListOf<suspend (WakeSpikePayload) -> Unit>()

    fun startSession(payload: StartSessionPayload, userProfile: UserProfile = UserProfile()): NightSession {
        sensorManager.startTracking()
        recentWindows.clear()
        triggeredCues.clear()
        lastDeliveredCue = null
        preCueWindow = null

        val session = NightSession(
            id = payload.sessionId,
            startTimeMs = payload.startTimeMs,
            mode = payload.mode,
            status = SessionStatus.RUNNING,
            cuesPlanned = payload.maxCues,
            cuesTriggered = 0,
            cooldownMinutes = payload.cooldownMinutes,
            earliestCueMinutes = payload.earliestCueMinutes,
            hapticIntensity = payload.hapticIntensity,
            audioEnabled = payload.audioEnabled,
            wbtbAlarmTimeMs = payload.wbtbAlarmTimeMs
        )

        _currentSession.value = session
        return session
    }

    suspend fun processSensorWindow(
        windowStartMs: Long,
        windowEndMs: Long,
        userProfile: UserProfile = UserProfile()
    ): SensorWindow {
        val session = _currentSession.value ?: return sensorManager.aggregateWindow(windowStartMs, windowEndMs)
        val rawWindow = sensorManager.aggregateWindow(windowStartMs, windowEndMs)

        val elapsedMinutes = (windowEndMs - session.startTimeMs) / 60000

        // 1. Evaluate REM confidence
        val breakdown = confidenceEngine.evaluateWindow(
            currentWindow = rawWindow,
            minutesFromOnset = elapsedMinutes,
            recentWindows = recentWindows,
            userBaselineHr = userProfile.baselineHeartRate,
            userBaselineIbiVar = userProfile.baselineIbiVariance
        )

        val evaluatedWindow = rawWindow.copy(confidence = breakdown.compositeScore)
        recentWindows.add(evaluatedWindow)
        if (recentWindows.size > 10) recentWindows.removeAt(0)

        // 2. Check for post-cue wake spike on immediately preceding cue
        val lastCue = lastDeliveredCue
        val preWin = preCueWindow
        if (lastCue != null && preWin != null && !lastCue.wakeSpikeAfter) {
            val isSpike = decisionEngine.checkForWakeSpike(preWin, evaluatedWindow)
            if (isSpike) {
                val updatedCue = lastCue.copy(wakeSpikeAfter = true, outcome = CueOutcome.WOKE_UP_IMMEDIATELY)
                val cueIndex = triggeredCues.indexOfFirst { it.id == lastCue.id }
                if (cueIndex >= 0) triggeredCues[cueIndex] = updatedCue

                val spikePayload = WakeSpikePayload(
                    sessionId = session.id,
                    cueId = lastCue.id,
                    timestampMs = windowEndMs,
                    movementIndex = evaluatedWindow.movementIndex,
                    hrSurgeBpm = evaluatedWindow.meanHr - preWin.meanHr
                )
                onWakeSpikeCallbacks.forEach { it(spikePayload) }
            }
        }

        // 3. Make Decision on cue triggering
        val updatedSession = session.copy(
            sensorWindows = session.sensorWindows + evaluatedWindow,
            cuesTriggered = triggeredCues.size,
            cueEvents = triggeredCues.toList()
        )

        val decision = decisionEngine.evaluate(
            session = updatedSession,
            currentWindow = evaluatedWindow,
            minutesFromSleepStart = elapsedMinutes,
            confidence = breakdown.compositeScore,
            userProfile = userProfile
        )

        if (decision is NightCueDecisionEngine.Decision.TriggerCue) {
            val cueId = "cue_${UUID.randomUUID().toString().take(8)}"
            val cueEvent = CueEvent(
                id = cueId,
                sessionId = session.id,
                timestampMs = windowEndMs,
                minutesFromSleepStart = elapsedMinutes,
                cueType = decision.cueType,
                intensity = decision.intensity,
                confidenceScoreAtTrigger = decision.confidence
            )

            triggeredCues.add(cueEvent)
            lastDeliveredCue = cueEvent
            preCueWindow = evaluatedWindow

            // Play local haptics
            hapticEngine.playLucidCue(decision.intensity)

            val payload = CueTriggeredPayload(
                cueId = cueId,
                sessionId = session.id,
                timestampMs = windowEndMs,
                cueType = decision.cueType,
                intensity = decision.intensity,
                confidence = decision.confidence
            )
            onCueTriggeredCallbacks.forEach { it(payload) }
        }

        _currentSession.value = updatedSession.copy(
            cuesTriggered = triggeredCues.size,
            cueEvents = triggeredCues.toList()
        )

        return evaluatedWindow
    }

    fun stopSession(endTimeMs: Long = System.currentTimeMillis()): NightSession? {
        sensorManager.stopTracking()
        val session = _currentSession.value ?: return null
        val finished = session.copy(
            endTimeMs = endTimeMs,
            status = SessionStatus.COMPLETED,
            cuesTriggered = triggeredCues.size,
            cueEvents = triggeredCues.toList()
        )
        _currentSession.value = null
        return finished
    }
}
