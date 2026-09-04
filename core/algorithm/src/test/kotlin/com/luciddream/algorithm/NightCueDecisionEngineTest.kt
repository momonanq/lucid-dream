package com.luciddream.algorithm

import com.luciddream.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NightCueDecisionEngineTest {

    private val engine = NightCueDecisionEngine(defaultConfidenceThreshold = 0.65)

    private fun createDummyWindow(movement: Double = 0.04, hr: Double = 58.0): SensorWindow {
        return SensorWindow(
            startTimestampMs = 100000,
            endTimestampMs = 160000,
            meanHr = hr,
            minHr = hr - 2,
            maxHr = hr + 2,
            hrStdDev = 1.0,
            ibiMeanMs = 1000.0,
            rmssd = 45.0,
            sdnn = 45.0,
            movementIndex = movement,
            sampleCount = 60,
            confidence = 0.85
        )
    }

    @Test
    fun `suppresses cues when elapsed time is before earliestCueMinutes`() {
        val session = NightSession(
            id = "s1",
            startTimeMs = 0,
            mode = NightMode.WATCH_ASSIST,
            status = SessionStatus.RUNNING,
            earliestCueMinutes = 90
        )

        val decision = engine.evaluate(
            session = session,
            currentWindow = createDummyWindow(),
            minutesFromSleepStart = 60, // Before 90m
            confidence = 0.85,
            userProfile = UserProfile()
        )

        assertTrue(decision is NightCueDecisionEngine.Decision.Suppressed)
    }

    @Test
    fun `suppresses cues when max cues limit is reached`() {
        val session = NightSession(
            id = "s1",
            startTimeMs = 0,
            mode = NightMode.WATCH_ASSIST,
            status = SessionStatus.RUNNING,
            cuesPlanned = 3,
            cuesTriggered = 3
        )

        val decision = engine.evaluate(
            session = session,
            currentWindow = createDummyWindow(),
            minutesFromSleepStart = 280,
            confidence = 0.85,
            userProfile = UserProfile()
        )

        assertTrue(decision is NightCueDecisionEngine.Decision.Suppressed)
    }

    @Test
    fun `suppresses cues during active cooldown`() {
        val lastCue = CueEvent(
            id = "c1",
            sessionId = "s1",
            timestampMs = 100000,
            minutesFromSleepStart = 250,
            cueType = CueType.HAPTIC_VIBRATION,
            intensity = 0.5,
            confidenceScoreAtTrigger = 0.80
        )

        val session = NightSession(
            id = "s1",
            startTimeMs = 0,
            mode = NightMode.TLR,
            status = SessionStatus.RUNNING,
            cooldownMinutes = 15,
            cueEvents = listOf(lastCue)
        )

        // 8 minutes elapsed since last cue
        val currentWin = createDummyWindow().copy(endTimestampMs = 100000 + (8 * 60 * 1000))

        val decision = engine.evaluate(
            session = session,
            currentWindow = currentWin,
            minutesFromSleepStart = 258,
            confidence = 0.85,
            userProfile = UserProfile()
        )

        assertTrue(decision is NightCueDecisionEngine.Decision.Suppressed)
    }

    @Test
    fun `triggers cue when all conditions and confidence threshold are satisfied`() {
        val session = NightSession(
            id = "s1",
            startTimeMs = 0,
            mode = NightMode.TLR,
            status = SessionStatus.RUNNING,
            cuesPlanned = 5,
            cuesTriggered = 1,
            audioEnabled = true,
            hapticIntensity = 0.6
        )

        val window = createDummyWindow(movement = 0.02)
        val decision = engine.evaluate(
            session = session,
            currentWindow = window,
            minutesFromSleepStart = 310,
            confidence = 0.82,
            userProfile = UserProfile()
        )

        assertTrue(decision is NightCueDecisionEngine.Decision.TriggerCue)
        val trigger = decision as NightCueDecisionEngine.Decision.TriggerCue
        assertEquals(CueType.COMBINED, trigger.cueType)
        assertEquals(0.6, trigger.intensity, 0.01)
    }

    @Test
    fun `detects wake spike upon sudden motion or HR surge`() {
        val pre = createDummyWindow(movement = 0.02, hr = 55.0)
        val postSpike = createDummyWindow(movement = 0.45, hr = 75.0)

        val isSpike = engine.checkForWakeSpike(pre, postSpike)
        assertTrue(isSpike, "Should identify wake spike from high motion and HR jump")
    }
}
