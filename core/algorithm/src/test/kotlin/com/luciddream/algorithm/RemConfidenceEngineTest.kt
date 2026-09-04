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
            sampleCount = 60,
            hrSampleCount = 20,
            ibiSampleCount = 20,
            motionSampleCount = 20,
            isDataSufficient = true
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

    @Test
    fun `evaluateWindow returns zero confidence when data is insufficient`() {
        val invalidWindow = SensorWindow(
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
            sampleCount = 0,
            hrSampleCount = 0,
            ibiSampleCount = 0,
            motionSampleCount = 0,
            isDataSufficient = false
        )

        val breakdown = engine.evaluateWindow(
            currentWindow = invalidWindow,
            minutesFromOnset = 320,
            recentWindows = emptyList()
        )

        assertEquals(0.0, breakdown.compositeScore, 0.001)
        assertEquals(0.0, breakdown.timeScore, 0.001)
        assertEquals(0.0, breakdown.motionScore, 0.001)
    }

    @Test
    fun `motion score transitions smoothly across 0_05 boundary without jump`() {
        val scoreAtBoundary = engine.calculateMotionScore(0.05)
        val scoreJustAbove = engine.calculateMotionScore(0.051)

        assertEquals(1.0, scoreAtBoundary, 0.001)
        assertTrue(scoreJustAbove < 1.0, "Score should start declining above 0.05")
        assertTrue(scoreJustAbove > 0.99, "Score should not jump down abruptly; got $scoreJustAbove")
    }
}
