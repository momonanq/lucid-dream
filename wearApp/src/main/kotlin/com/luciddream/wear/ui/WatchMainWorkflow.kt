package com.luciddream.wear.ui

import com.luciddream.data.sync.QuickMorningFeedbackPayload
import com.luciddream.data.sync.StartSessionPayload
import com.luciddream.model.NightMode
import com.luciddream.model.NightSession
import com.luciddream.wear.haptic.WatchHapticEngine
import com.luciddream.wear.service.WatchNightTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Screen state machine for Wear OS app.
 */
sealed class WatchScreenState {
    data class Ready(
        val batteryPercentage: Int,
        val isPhoneConnected: Boolean,
        val selectedMode: NightMode
    ) : WatchScreenState()

    data class Running(
        val sessionId: String,
        val mode: NightMode,
        val elapsedMinutes: Long,
        val cuesTriggeredCount: Int,
        val currentConfidence: Double
    ) : WatchScreenState()

    data class MorningFeedback(
        val sessionId: String,
        val totalCuesDelivered: Int
    ) : WatchScreenState()
}

class WatchMainWorkflow(
    private val trackingService: WatchNightTrackingService,
    private val hapticEngine: WatchHapticEngine
) {

    private val _screenState = MutableStateFlow<WatchScreenState>(
        WatchScreenState.Ready(batteryPercentage = 88, isPhoneConnected = true, selectedMode = NightMode.TLR)
    )
    val screenState: StateFlow<WatchScreenState> = _screenState.asStateFlow()

    private var activeSessionId: String? = null

    fun onStartSessionClicked(mode: NightMode = NightMode.TLR, audioEnabled: Boolean = true): NightSession {
        val sessionId = "night_${UUID.randomUUID().toString().take(8)}"
        activeSessionId = sessionId

        val payload = StartSessionPayload(
            sessionId = sessionId,
            mode = mode,
            startTimeMs = System.currentTimeMillis(),
            earliestCueMinutes = 90,
            cooldownMinutes = 15,
            maxCues = 5,
            hapticIntensity = 0.5,
            audioEnabled = audioEnabled
        )

        val session = trackingService.startSession(payload)
        _screenState.value = WatchScreenState.Running(
            sessionId = sessionId,
            mode = mode,
            elapsedMinutes = 0,
            cuesTriggeredCount = 0,
            currentConfidence = 0.0
        )
        return session
    }

    suspend fun onTestHapticClicked() {
        hapticEngine.playTestTap()
    }

    fun onStopSessionClicked(): NightSession? {
        val finished = trackingService.stopSession()
        val sessId = activeSessionId ?: "unknown"
        _screenState.value = WatchScreenState.MorningFeedback(
            sessionId = sessId,
            totalCuesDelivered = finished?.cuesTriggered ?: 0
        )
        return finished
    }

    fun onSubmitMorningFeedback(
        hadDream: Boolean,
        hadLucid: Boolean,
        noticedSignal: Boolean
    ): QuickMorningFeedbackPayload {
        val payload = QuickMorningFeedbackPayload(
            sessionId = activeSessionId ?: "unknown",
            timestampMs = System.currentTimeMillis(),
            hadDream = hadDream,
            hadLucidDream = hadLucid,
            noticedSignal = noticedSignal
        )

        // Reset to Ready screen
        _screenState.value = WatchScreenState.Ready(
            batteryPercentage = 85,
            isPhoneConnected = true,
            selectedMode = NightMode.TLR
        )
        activeSessionId = null

        return payload
    }
}
