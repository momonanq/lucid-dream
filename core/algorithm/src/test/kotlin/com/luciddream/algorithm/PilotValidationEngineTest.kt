package com.luciddream.algorithm

import com.luciddream.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PilotValidationEngineTest {

    private val validationEngine = PilotValidationEngine(defaultThreshold = 0.65)

    @Test
    fun `evaluates session windows against ground-truth hypnogram correctly`() {
        val startTime = 1000_000L

        // Window 1: Non-REM (Light), confidence 0.40 -> True Negative
        val w1 = SensorWindow(
            startTimestampMs = startTime,
            endTimestampMs = startTime + 60_000L,
            meanHr = 58.0,
            rmssd = 40.0,
            movementIndex = 0.08,
            confidence = 0.40,
            isDataSufficient = true
        )

        // Window 2: REM, confidence 0.72 -> True Positive (Hit)
        val w2 = SensorWindow(
            startTimestampMs = startTime + 60_000L,
            endTimestampMs = startTime + 120_000L,
            meanHr = 61.0,
            rmssd = 46.0,
            movementIndex = 0.03,
            confidence = 0.72,
            isDataSufficient = true
        )

        // Window 3: Non-REM (Deep), confidence 0.70 -> False Positive (False Alarm)
        val w3 = SensorWindow(
            startTimestampMs = startTime + 120_000L,
            endTimestampMs = startTime + 180_000L,
            meanHr = 52.0,
            rmssd = 75.0,
            movementIndex = 0.02,
            confidence = 0.70,
            isDataSufficient = true
        )

        // Window 4: REM, confidence 0.50 -> False Negative (Miss)
        val w4 = SensorWindow(
            startTimestampMs = startTime + 180_000L,
            endTimestampMs = startTime + 240_000L,
            meanHr = 62.0,
            rmssd = 44.0,
            movementIndex = 0.04,
            confidence = 0.50,
            isDataSufficient = true
        )

        val session = NightSession(
            id = "pilot_s1",
            startTimeMs = startTime,
            mode = NightMode.BEGINNER, // Passive data collection mode
            sensorWindows = listOf(w1, w2, w3, w4)
        )

        val sleepImport = SleepImport(
            id = "imp_1",
            sessionId = "pilot_s1",
            totalSleepMinutes = 4,
            remMinutes = 2,
            deepMinutes = 1,
            lightMinutes = 1,
            awakeMinutes = 0,
            stages = listOf(
                SleepStageInterval(SleepStage.LIGHT, startTime, startTime + 60_000L),
                SleepStageInterval(SleepStage.REM, startTime + 60_000L, startTime + 120_000L),
                SleepStageInterval(SleepStage.DEEP, startTime + 120_000L, startTime + 180_000L),
                SleepStageInterval(SleepStage.REM, startTime + 180_000L, startTime + 240_000L)
            )
        )

        val (metrics, matches) = validationEngine.evaluateSession(session, sleepImport, threshold = 0.65)

        assertEquals(4, metrics.totalWindows)
        assertEquals(2, metrics.remWindows)
        assertEquals(2, metrics.nonRemWindows)
        assertEquals(1, metrics.truePositives)   // w2
        assertEquals(1, metrics.falsePositives)  // w3
        assertEquals(1, metrics.trueNegatives)   // w1
        assertEquals(1, metrics.falseNegatives)  // w4

        assertEquals(0.5, metrics.hitRate, 0.01)       // 1 / 2 = 0.5
        assertEquals(0.5, metrics.precision, 0.01)     // 1 / 2 = 0.5
        assertEquals(0.5, metrics.specificity, 0.01)   // 1 / 2 = 0.5
        assertEquals(0.5, metrics.f1Score, 0.01)

        assertEquals(4, matches.size)
        assertTrue(matches[0].isCorrectRejection)
        assertTrue(matches[1].isHit)
        assertTrue(matches[2].isFalseAlarm)
        assertTrue(matches[3].isMiss)
    }

    @Test
    fun `generates valid pilot CSV export with required columns`() {
        val startTime = 1000_000L
        val w1 = SensorWindow(
            startTimestampMs = startTime,
            endTimestampMs = startTime + 60_000L,
            meanHr = 60.0,
            rmssd = 45.0,
            movementIndex = 0.02,
            confidence = 0.80,
            isDataSufficient = true
        )

        val session = NightSession(
            id = "pilot_export_test",
            startTimeMs = startTime,
            mode = NightMode.BEGINNER,
            sensorWindows = listOf(w1)
        )

        val sleepImport = SleepImport(
            id = "imp_export",
            sessionId = "pilot_export_test",
            totalSleepMinutes = 1,
            remMinutes = 1,
            deepMinutes = 0,
            lightMinutes = 0,
            awakeMinutes = 0,
            stages = listOf(
                SleepStageInterval(SleepStage.REM, startTime, startTime + 60_000L)
            )
        )

        val csv = validationEngine.generatePilotCsv(session, sleepImport)

        assertTrue(csv.contains("window_index,start_ms,end_ms,minutes_from_onset"))
        assertTrue(csv.contains("mean_hr,rmssd,movement_index,is_data_sufficient,rem_confidence,ground_truth_stage"))
        assertTrue(csv.contains("is_rem_ground_truth,is_predicted_rem,is_hit,is_false_alarm"))
        assertTrue(csv.contains("REM,true,true,true,false"))
    }

    @Test
    fun `optimizes weights to produce recommendations`() {
        val startTime = 1000_000L
        val windows = (0 until 10).map { i ->
            SensorWindow(
                startTimestampMs = startTime + (i * 60_000L),
                endTimestampMs = startTime + ((i + 1) * 60_000L),
                meanHr = 60.0,
                rmssd = 45.0,
                movementIndex = 0.02,
                confidence = 0.70,
                isDataSufficient = true
            )
        }

        val session = NightSession(
            id = "opt_test",
            startTimeMs = startTime,
            mode = NightMode.WATCH_ASSIST,
            sensorWindows = windows
        )

        val sleepImport = SleepImport(
            id = "imp_opt",
            sessionId = "opt_test",
            totalSleepMinutes = 10,
            remMinutes = 5,
            deepMinutes = 5,
            lightMinutes = 0,
            awakeMinutes = 0,
            stages = listOf(
                SleepStageInterval(SleepStage.DEEP, startTime, startTime + 300_000L),
                SleepStageInterval(SleepStage.REM, startTime + 300_000L, startTime + 600_000L)
            )
        )

        val recommendation = validationEngine.optimizeWeights(session, sleepImport)

        assertNotNull(recommendation)
        assertTrue(recommendation.timeWeight > 0.0)
        assertTrue(recommendation.motionWeight > 0.0)
        assertTrue(recommendation.hrvWeight > 0.0)
        assertTrue(recommendation.consistencyWeight > 0.0)
        assertTrue(recommendation.optimalThreshold in 0.50..0.80)
    }
}
