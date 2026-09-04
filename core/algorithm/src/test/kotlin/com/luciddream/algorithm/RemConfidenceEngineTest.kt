package com.luciddream.algorithm

import com.luciddream.model.SensorWindow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RemConfidenceEngineTest {

    private val engine = RemConfidenceEngine()

    @Test
    fun `time score suppresses cues in early sleep and peaks in late night`() {
        val earlyScore = engine.calculateTimeScore(45) // 45 min post onset
        val midScore = engine.calculateTimeScore(150)  // 2.5 hours
        val lateScore = engine.calculateTimeScore(330) // 5.5 hours (peak REM)

        assertEquals(0.05, earlyScore, 0.01)
        assertTrue(midScore > earlyScore, "Mid score should exceed early score")
        assertTrue(lateScore >= 0.85, "Late score should be high (>=0.85), got $lateScore")
    }

    @Test
    fun `motion score is high during atonia and drops on movement`() {
        val atoniaScore = engine.calculateMotionScore(0.02)
        val slightTwitchScore = engine.calculateMotionScore(0.10)
        val grossMovementScore = engine.calculateMotionScore(0.60)

        assertEquals(1.0, atoniaScore, 0.01)
        assertTrue(slightTwitchScore in 0.6..0.85)
        assertEquals(0.0, grossMovementScore, 0.01)
    }

    @Test
    fun `evaluateWindow produces high composite confidence during late night still period`() {
        val window = SensorWindow(
            startTimestampMs = 100000,
            endTimestampMs = 160000,
            meanHr = 58.0,
            minHr = 54.0,
            maxHr = 63.0,
            hrStdDev = 2.5,
            ibiMeanMs = 1034.0,
            rmssd = 48.0,
            sdnn = 50.0,
            movementIndex = 0.03,
            sampleCount = 60
        )

        val recent = listOf(window, window.copy(movementIndex = 0.04))

        val breakdown = engine.evaluateWindow(
            currentWindow = window,
            minutesFromOnset = 320, // 5h20m
            recentWindows = recent,
            userBaselineHr = 58.0,
            userBaselineIbiVar = 45.0
        )

        assertTrue(breakdown.compositeScore >= 0.80, "Expected composite confidence >= 0.80, got ${breakdown.compositeScore}")
        assertTrue(breakdown.timeScore >= 0.80)
        assertEquals(1.0, breakdown.motionScore)
    }
}
