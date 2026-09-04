package com.luciddream.wear.haptic

import com.luciddream.model.CueType
import kotlinx.coroutines.delay

/**
 * Watch Haptic engine providing progressive, soft tactile cue patterns on Galaxy Watch.
 */
class WatchHapticEngine {

    data class HapticPattern(
        val pulseCount: Int,
        val pulseDurationMs: Long,
        val pauseDurationMs: Long,
        val amplitudePercent: Double
    )

    private val activeVibrations = mutableListOf<HapticPattern>()

    fun getDeliveredPatterns(): List<HapticPattern> = activeVibrations.toList()

    /**
     * Triggers a non-intrusive haptic cue tailored for REM lucidity induction without awakening.
     */
    suspend fun playLucidCue(intensity: Double = 0.5) {
        val clampedIntensity = intensity.coerceIn(0.1, 1.0)

        // Progressive 3-tap pattern: 70ms tap -> 150ms pause -> 70ms tap -> 150ms pause -> 90ms tap
        val pattern = HapticPattern(
            pulseCount = 3,
            pulseDurationMs = (70 * clampedIntensity).toLong().coerceAtLeast(30),
            pauseDurationMs = 150,
            amplitudePercent = clampedIntensity
        )

        activeVibrations.add(pattern)

        // Simulate tactile timing
        for (i in 0 until pattern.pulseCount) {
            delay(pattern.pulseDurationMs)
            if (i < pattern.pulseCount - 1) {
                delay(pattern.pauseDurationMs)
            }
        }
    }

    /**
     * Quick haptic confirmation when testing vibration from the watch screen.
     */
    suspend fun playTestTap() {
        playLucidCue(0.4)
    }
}
