package com.luciddream.wear

import com.luciddream.data.sync.StartSessionPayload
import com.luciddream.model.HeartRateReading
import com.luciddream.model.IbiReading
import com.luciddream.model.MotionReading
import com.luciddream.model.NightMode
import com.luciddream.wear.haptic.WatchHapticEngine
import com.luciddream.wear.sensor.SamsungSensorManager
import com.luciddream.wear.service.WatchNightTrackingService
import com.luciddream.wear.ui.WatchMainWorkflow
import com.luciddream.wear.ui.WatchScreenState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WatchWorkflowSimulationTest {

    @Test
    fun `full watch session workflow from ready to running to morning feedback`() = runTest {
        val sensorManager = SamsungSensorManager()
        val hapticEngine = WatchHapticEngine()
        val trackingService = WatchNightTrackingService(sensorManager, hapticEngine)
        val workflow = WatchMainWorkflow(trackingService, hapticEngine)

        // 1. Initially Ready
        assertTrue(workflow.screenState.value is WatchScreenState.Ready)

        // 2. Start Session
        val session = workflow.onStartSessionClicked(mode = NightMode.WATCH_ASSIST)
        assertTrue(workflow.screenState.value is WatchScreenState.Running)

        // 3. Feed 5.5 hours of simulated data (into peak REM zone)
        val startTime = session.startTimeMs
        val currentWindowStart = startTime + (330 * 60 * 1000L) // 5h30m
        val currentWindowEnd = currentWindowStart + 60000L

        // Feed realistic REM sensor data: HR ~58bpm, IBI ~1034ms with high RMSSD, minimal motion
        for (i in 0 until 60) {
            sensorManager.onHeartRateSample(HeartRateReading(currentWindowStart + i * 1000, 58.0))
            val ibiJitter = if (i % 2 == 0) 30.0 else -30.0
            sensorManager.onIbiSample(IbiReading(currentWindowStart + i * 1000, 1034.0 + ibiJitter))
            sensorManager.onMotionSample(MotionReading(currentWindowStart + i * 1000, 0.01f, 0.02f, 9.80f))
        }

        val window = trackingService.processSensorWindow(currentWindowStart, currentWindowEnd)
        assertTrue(window.confidence >= 0.70, "Window confidence should be high, got ${window.confidence}")

        // Check that haptic engine received cue
        val patterns = hapticEngine.getDeliveredPatterns()
        assertTrue(patterns.isNotEmpty(), "Haptic cue should have fired")

        // 4. Stop Session and Morning Feedback
        val finished = workflow.onStopSessionClicked()
        assertNotNull(finished)
        assertTrue(workflow.screenState.value is WatchScreenState.MorningFeedback)

        val morningPayload = workflow.onSubmitMorningFeedback(hadDream = true, hadLucid = true, noticedSignal = true)
        assertEquals(true, morningPayload.hadLucidDream)

        // Should return to Ready state
        assertTrue(workflow.screenState.value is WatchScreenState.Ready)
    }
}
