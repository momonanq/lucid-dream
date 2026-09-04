package com.luciddream.algorithm

import com.luciddream.model.SensorWindow
import kotlin.math.max
import kotlin.math.min

/**
 * Heuristic probabilistic REM window estimation engine for Galaxy Watch sensors.
 *
 * Grounded in sleep neurobiology:
 * 1. Circadian REM propensity increases markedly across the sleep period (maximal > 4-5 hours).
 * 2. Skeletal muscle atonia results in prolonged periods of minimal gross motor movement.
 * 3. Phasic REM exhibits autonomic turbulence with fluctuating IBI variance against stable N3 baseline.
 * 4. Multi-window persistence filters out fleeting sensory noise.
 */
class RemConfidenceEngine(
    private val timeWeight: Double = 0.35,
    private val motionWeight: Double = 0.30,
    private val hrvWeight: Double = 0.20,
    private val consistencyWeight: Double = 0.15
) {

    data class ConfidenceBreakdown(
        val timeScore: Double,
        val motionScore: Double,
        val hrvScore: Double,
        val consistencyScore: Double,
        val compositeScore: Double
    )

    /**
     * Calculates time-of-night score.
     * REM sleep is minimal in the first 90 minutes (mostly N2/N3) and reaches peak density
     * between 240 and 420 minutes post sleep onset.
     */
    fun calculateTimeScore(minutesFromOnset: Long): Double {
        if (minutesFromOnset < 90) {
            return 0.05
        }
        if (minutesFromOnset < 180) {
            // First cycle transition (1.5h - 3h): modest REM duration (~10-15m)
            return 0.30 + ((minutesFromOnset - 90).toDouble() / 90.0) * 0.20
        }
        if (minutesFromOnset < 270) {
            // Second cycle transition (3h - 4.5h): increasing REM duration (~20m)
            return 0.50 + ((minutesFromOnset - 180).toDouble() / 90.0) * 0.25
        }
        // Peak REM zone (4.5h - 7h+): long REM episodes (30-60m)
        val peakProgress = min(1.0, (minutesFromOnset - 270).toDouble() / 150.0)
        return min(1.0, 0.75 + (peakProgress * 0.25))
    }

    /**
     * Calculates motion score based on motor stillness.
     * REM sleep has strong muscle atonia; higher movement indicates awakening or stage shift.
     * Smooth, continuous monotonic transition from 1.0 (stillness <= 0.05) down to 0.0 (movement >= 0.45).
     */
    fun calculateMotionScore(movementIndex: Double): Double {
        return when {
            movementIndex <= 0.05 -> 1.0
            movementIndex <= 0.15 -> 1.0 - ((movementIndex - 0.05) / 0.10) * 0.50
            movementIndex <= 0.35 -> 0.50 - ((movementIndex - 0.15) / 0.20) * 0.40
            else -> max(0.0, 0.10 - (movementIndex - 0.35))
        }
    }

    /**
     * Calculates HRV score based on RMSSD / SDNN variance matching REM autonomic profile.
     * Slow-wave sleep (N3) shows stable parasympathetic dominance (high stable RMSSD, regular respiratory sinus arrhythmia).
     * REM shows transient autonomic turbulence with fluctuating IBI bursts.
     */
    fun calculateHrvScore(
        window: SensorWindow,
        userBaselineHr: Double,
        userBaselineIbiVar: Double
    ): Double {
        if (window.ibiSampleCount < 5) return 0.4 // fallback on sparse IBI data

        // Check if HR is within reasonable sleep band (not awake surge, not extreme bradycardia)
        val hrDiff = kotlin.math.abs(window.meanHr - userBaselineHr)
        val hrPenalty = if (hrDiff > 25.0) 0.3 else 1.0

        // REM autonomic proxy: RMSSD relative to user baseline
        val targetRmssd = max(20.0, userBaselineIbiVar)
        val ratio = window.rmssd / targetRmssd

        val hrvProfileScore = when {
            ratio in 0.75..1.6 -> 0.95
            ratio in 0.50..2.2 -> 0.75
            ratio in 0.30..3.0 -> 0.50
            else -> 0.25
        }

        return min(1.0, hrvProfileScore * hrPenalty)
    }

    /**
     * Calculates consistency score over consecutive historical windows (e.g. past 2-4 windows).
     */
    fun calculateConsistencyScore(recentWindows: List<SensorWindow>): Double {
        if (recentWindows.isEmpty()) return 0.5
        val stillnessCount = recentWindows.count { it.movementIndex < 0.15 }
        return stillnessCount.toDouble() / recentWindows.size.toDouble()
    }

    /**
     * Computes the complete composite REM confidence breakdown.
     */
    fun evaluateWindow(
        currentWindow: SensorWindow,
        minutesFromOnset: Long,
        recentWindows: List<SensorWindow>,
        userBaselineHr: Double = 60.0,
        userBaselineIbiVar: Double = 45.0
    ): ConfidenceBreakdown {
        if (!currentWindow.isDataSufficient) {
            return ConfidenceBreakdown(
                timeScore = 0.0,
                motionScore = 0.0,
                hrvScore = 0.0,
                consistencyScore = 0.0,
                compositeScore = 0.0
            )
        }

        val timeScore = calculateTimeScore(minutesFromOnset)
        val motionScore = calculateMotionScore(currentWindow.movementIndex)
        val hrvScore = calculateHrvScore(currentWindow, userBaselineHr, userBaselineIbiVar)
        val consistencyScore = calculateConsistencyScore(recentWindows)

        val composite = (timeScore * timeWeight) +
                (motionScore * motionWeight) +
                (hrvScore * hrvWeight) +
                (consistencyScore * consistencyWeight)

        val normalizedComposite = min(1.0, max(0.0, composite))

        return ConfidenceBreakdown(
            timeScore = timeScore,
            motionScore = motionScore,
            hrvScore = hrvScore,
            consistencyScore = consistencyScore,
            compositeScore = normalizedComposite
        )
    }
}
