package com.luciddream.phone.service

import com.luciddream.algorithm.CalibrationEngine
import com.luciddream.algorithm.SleepSafetyGuardian
import com.luciddream.algorithm.effectiveMode
import com.luciddream.data.repository.NightSessionRepository
import com.luciddream.data.repository.UserProfileRepository
import com.luciddream.data.samsung.SamsungHealthDataGateway
import com.luciddream.data.sync.*
import com.luciddream.model.*
import com.luciddream.phone.audio.TlrAudioEngine
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Phone side orchestrator managing the night session lifecycle, audio cue pairing,
 * post-hoc Samsung Health sleep import, and calibration.
 */
class PhoneSessionCoordinator(
    private val sessionRepository: NightSessionRepository,
    private val profileRepository: UserProfileRepository,
    private val samsungHealthGateway: SamsungHealthDataGateway,
    private val audioElement: TlrAudioEngine,
    private val calibrationEngine: CalibrationEngine = CalibrationEngine(),
    private val safetyGuardian: SleepSafetyGuardian = SleepSafetyGuardian()
) {

    /**
     * Outcome of a start request, including how the sleep-fragmentation guardrails ruled on it.
     * [effectiveMode] may differ from [requestedMode] when the night was downgraded to recovery.
     */
    data class NightStartResult(
        val payload: StartSessionPayload,
        val requestedMode: NightMode,
        val guardrail: SleepSafetyGuardian.Decision
    ) {
        val effectiveMode: NightMode get() = guardrail.effectiveMode
        val cuesWithheld: Boolean get() = guardrail is SleepSafetyGuardian.Decision.RestNight
    }

    /**
     * Applies the across-nights exposure limits without starting anything, so the Tonight screen
     * can warn about a recovery night before the user commits to it.
     */
    suspend fun evaluateTonight(
        mode: NightMode,
        nowMs: Long = System.currentTimeMillis()
    ): SleepSafetyGuardian.Decision {
        return safetyGuardian.evaluateNight(
            requestedMode = mode,
            screening = profileRepository.getUserProfile().first().screening,
            recentSessions = sessionRepository.getAllSessions().first(),
            recentReports = sessionRepository.getAllMorningReports().first(),
            nowMs = nowMs
        )
    }

    suspend fun startNightSession(
        mode: NightMode,
        audioEnabled: Boolean = true,
        wbtbAlarmTimeMs: Long? = null
    ): NightStartResult {
        val profile = profileRepository.getUserProfile().first()
        val sessionId = "session_${UUID.randomUUID().toString().take(8)}"
        val startTime = System.currentTimeMillis()

        // A blocked night still runs, but in BEGINNER mode, where NightCueDecisionEngine
        // independently suppresses every nocturnal cue.
        val guardrail = evaluateTonight(mode, startTime)
        val effectiveMode = guardrail.effectiveMode
        val cuesAllowed = effectiveMode != NightMode.BEGINNER

        val session = NightSession(
            id = sessionId,
            userId = profile.id,
            startTimeMs = startTime,
            mode = effectiveMode,
            status = SessionStatus.RUNNING,
            cuesPlanned = if (cuesAllowed) profile.maxCuesPerNight else 0,
            cuesTriggered = 0,
            cooldownMinutes = profile.cooldownMinutes,
            earliestCueMinutes = profile.earliestCueMinutesAfterOnset,
            hapticIntensity = profile.preferredHapticIntensity,
            audioEnabled = audioEnabled && cuesAllowed,
            wbtbEnabled = effectiveMode == NightMode.WBTB,
            wbtbAlarmTimeMs = wbtbAlarmTimeMs.takeIf { cuesAllowed }
        )

        sessionRepository.saveSession(session)

        val payload = StartSessionPayload(
            sessionId = sessionId,
            mode = session.mode,
            startTimeMs = startTime,
            earliestCueMinutes = session.earliestCueMinutes,
            cooldownMinutes = session.cooldownMinutes,
            maxCues = session.cuesPlanned,
            hapticIntensity = session.hapticIntensity,
            audioEnabled = session.audioEnabled,
            wbtbAlarmTimeMs = session.wbtbAlarmTimeMs
        )

        return NightStartResult(payload, mode, guardrail)
    }

    suspend fun handleLiveCueEvent(payload: CueTriggeredPayload) {
        val session = sessionRepository.getSessionById(payload.sessionId) ?: return

        // If audio cues are enabled in session or combined mode, play synced soft acoustic chime
        if (session.audioEnabled && (payload.cueType == CueType.AUDIO_BEEP || payload.cueType == CueType.COMBINED)) {
            audioElement.playLucidityChime(volume = 0.25)
        }

        val event = CueEvent(
            id = payload.cueId,
            sessionId = payload.sessionId,
            timestampMs = payload.timestampMs,
            minutesFromSleepStart = (payload.timestampMs - session.startTimeMs) / 60000,
            cueType = payload.cueType,
            intensity = payload.intensity,
            confidenceScoreAtTrigger = payload.confidence
        )

        val updated = session.copy(
            cuesTriggered = session.cuesTriggered + 1,
            cueEvents = session.cueEvents + event
        )
        sessionRepository.saveSession(updated)
    }

    suspend fun handleWakeSpikeEvent(payload: WakeSpikePayload) {
        val session = sessionRepository.getSessionById(payload.sessionId) ?: return
        val updatedCues = session.cueEvents.map { cue ->
            if (cue.id == payload.cueId) {
                cue.copy(wakeSpikeAfter = true, outcome = CueOutcome.WOKE_UP_IMMEDIATELY)
            } else cue
        }
        sessionRepository.saveSession(session.copy(cueEvents = updatedCues))
    }

    suspend fun completeMorningSession(
        sessionId: String,
        endTimeMs: Long = System.currentTimeMillis(),
        morningFeedback: QuickMorningFeedbackPayload? = null
    ): Pair<NightSession, CalibrationEngine.CalibrationResult> {
        val session = sessionRepository.getSessionById(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val finishedSession = session.copy(
            endTimeMs = endTimeMs,
            status = SessionStatus.COMPLETED
        )
        sessionRepository.saveSession(finishedSession)

        // 1. Post-hoc import from Samsung Health Data SDK
        val sleepImport = samsungHealthGateway.importSleepSession(
            sessionId = sessionId,
            startMs = finishedSession.startTimeMs,
            endMs = endTimeMs
        )

        // 2. Create Morning Report
        val report = if (morningFeedback != null) {
            MorningReport(
                id = "report_$sessionId",
                sessionId = sessionId,
                timestampMs = System.currentTimeMillis(),
                hadDreams = morningFeedback.hadDream,
                recallScore = if (morningFeedback.hadDream) 4 else 1,
                lucidSuccess = morningFeedback.hadLucidDream,
                cueDetectedInDream = morningFeedback.noticedSignal,
                falseAwakening = false
            )
        } else null

        if (report != null) {
            sessionRepository.saveMorningReport(report)
        }

        // 3. Post-hoc Calibration & Threshold Adaptation
        val currentProfile = profileRepository.getUserProfile().first()
        val calibrationResult = calibrationEngine.calibrate(
            session = finishedSession,
            sleepImport = sleepImport,
            morningReport = report,
            currentProfile = currentProfile
        )

        profileRepository.updateProfile(calibrationResult.adaptedProfile)

        return Pair(finishedSession, calibrationResult)
    }
}
