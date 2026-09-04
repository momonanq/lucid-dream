package com.luciddream.algorithm

import com.luciddream.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalibrationEngineTest {

    private val engine = CalibrationEngine()

    @Test
    fun `calibrates against sleep stages and reduces haptic on wake spikes`() {
        val cue1 = CueEvent("c1", "s1", 10000, 200, CueType.HAPTIC_VIBRATION, 0.6, 0.8, wakeSpikeAfter = true)
        val cue2 = CueEvent("c2", "s1", 20000, 260, CueType.HAPTIC_VIBRATION, 0.6, 0.85, wakeSpikeAfter = false)

        val session = NightSession(
            id = "s1",
            startTimeMs = 0,
            mode = NightMode.WATCH_ASSIST,
            cueEvents = listOf(cue1, cue2)
        )

        val sleepImport = SleepImport(
            id = "imp1",
            sessionId = "s1",
            totalSleepMinutes = 480,
            remMinutes = 100,
            deepMinutes = 90,
            lightMinutes = 250,
            awakeMinutes = 40,
            stages = listOf(
                SleepStageInterval(SleepStage.REM, 5000, 15000),
                SleepStageInterval(SleepStage.REM, 18000, 25000)
            )
        )

        val morningReport = MorningReport(
            id = "r1",
            sessionId = "s1",
            timestampMs = 30000,
            hadDreams = true,
            recallScore = 4,
            lucidSuccess = true,
            cueDetectedInDream = true,
            falseAwakening = false
        )

        val profile = UserProfile(preferredHapticIntensity = 0.6, cooldownMinutes = 15)

        val result = engine.calibrate(session, sleepImport, morningReport, profile)

        assertEquals(2, result.totalCuesDelivered)
        assertEquals(2, result.cuesInRemStage)
        assertEquals(1.0, result.remAccuracyProxy, 0.01)
        assertEquals(0.5, result.wakeSpikeRate, 0.01)

        // Wake spike was 50% (>= 30%), intensity should be reduced and cooldown increased
        assertTrue(result.adaptedProfile.preferredHapticIntensity < 0.6)
        assertTrue(result.adaptedProfile.cooldownMinutes > 15)
        assertEquals(1, result.adaptedProfile.calibrationNightsCompleted)
    }

    @Test
    fun `adapts baselineIbiVariance and tightens confidence threshold on non-rem cues`() {
        // Cue delivered in LIGHT sleep (non-REM)
        val cue = CueEvent("c1", "s1", 10000, 150, CueType.HAPTIC_VIBRATION, 0.5, 0.68, wakeSpikeAfter = false)

        val window = SensorWindow(
            startTimestampMs = 5000,
            endTimestampMs = 15000,
            meanHr = 55.0,
            minHr = 50.0,
            maxHr = 60.0,
            hrStdDev = 2.0,
            ibiMeanMs = 1050.0,
            rmssd = 35.0, // Session observed RMSSD
            sdnn = 40.0,
            movementIndex = 0.02,
            sampleCount = 60,
            hrSampleCount = 20,
            ibiSampleCount = 20,
            motionSampleCount = 20,
            isDataSufficient = true
        )

        val session = NightSession(
            id = "s1",
            startTimeMs = 0,
            mode = NightMode.WATCH_ASSIST,
            cueEvents = listOf(cue),
            sensorWindows = listOf(window)
        )

        val sleepImport = SleepImport(
            id = "imp1",
            sessionId = "s1",
            totalSleepMinutes = 480,
            remMinutes = 80,
            deepMinutes = 100,
            lightMinutes = 260,
            awakeMinutes = 40,
            stages = listOf(
                SleepStageInterval(SleepStage.LIGHT, 5000, 20000) // Cue is in LIGHT sleep!
            )
        )

        val initialProfile = UserProfile(
            baselineIbiVariance = 50.0,
            confidenceThreshold = 0.65
        )

        val result = engine.calibrate(session, sleepImport, null, initialProfile)

        assertEquals(0, result.cuesInRemStage)
        assertEquals(1, result.cuesInNonRemStage)
        assertEquals(0.0, result.remAccuracyProxy, 0.01)

        // Confidence threshold should be increased (tightened) because of false trigger in LIGHT sleep
        assertTrue(result.adaptedProfile.confidenceThreshold > 0.65, "Expected threshold to increase above 0.65")
        // Baseline IBI variance should adapt towards session RMSSD (35.0)
        assertTrue(result.adaptedProfile.baselineIbiVariance < 50.0, "Expected baselineIbiVariance to blend towards 35.0")
    }
}
