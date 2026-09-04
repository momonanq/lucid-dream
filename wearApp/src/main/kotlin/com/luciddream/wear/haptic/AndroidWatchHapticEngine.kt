package com.luciddream.wear.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Wear OS implementation of haptic cue engine utilizing physical vibrator.
 * Implements a progressive 3-tap waveform with safe duration clamping.
 */
class AndroidWatchHapticEngine(
    private val context: Context
) : WatchHapticEngine() {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override suspend fun playLucidCue(intensity: Double) = withContext(Dispatchers.Default) {
        val targetVibrator = vibrator ?: return@withContext
        val clampedIntensity = intensity.coerceIn(0.1, 1.0)

        // Safety clamp: total duration ~520ms (max 1500ms allowable)
        val timings = longArrayOf(0, 50, 150, 70, 150, 100)

        val amp1 = (100 * clampedIntensity).toInt().coerceIn(1, 255)
        val amp2 = (180 * clampedIntensity).toInt().coerceIn(1, 255)
        val amp3 = (255 * clampedIntensity).toInt().coerceIn(1, 255)
        val amplitudes = intArrayOf(0, amp1, 0, amp2, 0, amp3)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (targetVibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                targetVibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            // Fallback gracefully if vibration service is unavailable or in DND
        }

        super.playLucidCue(intensity)
    }

    override suspend fun playTestTap() = withContext(Dispatchers.Default) {
        val targetVibrator = vibrator ?: return@withContext
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                targetVibrator.vibrate(VibrationEffect.createOneShot(80, (150).coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(80)
            }
        } catch (e: Exception) {
            // Ignore
        }
        super.playTestTap()
    }
}
