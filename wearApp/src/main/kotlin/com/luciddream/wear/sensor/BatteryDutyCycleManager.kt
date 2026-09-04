package com.luciddream.wear.sensor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Manages sensor power consumption over an 8-hour sleep session via circadian duty-cycling.
 * Solves the watch battery exhaustion risk by throttling sensors during slow-wave N3 and
 * allocating maximum sampling budget to late-night peak REM windows (4–8h).
 */
class BatteryDutyCycleManager(
    val lowBatteryThresholdPercent: Int = 20
) {

    enum class SleepCyclePhase {
        SLEEP_ONSET,       // 0–90 min (Falling asleep & Slow-wave N3)
        INTERMEDIATE,      // 90–240 min (NREM/REM transition)
        PEAK_REM,          // 240–480 min (Peak REM density window)
        LOW_BATTERY_SAVER  // Battery < 20% guard
    }

    data class DutyCycleDecision(
        val phase: SleepCyclePhase,
        val policy: SamplingPolicy,
        val isLowBatteryClamped: Boolean,
        val reason: String
    )

    fun evaluate(elapsedMinutes: Long, batteryPercent: Int): DutyCycleDecision {
        if (batteryPercent in 1 until lowBatteryThresholdPercent) {
            return DutyCycleDecision(
                phase = SleepCyclePhase.LOW_BATTERY_SAVER,
                policy = SamplingPolicy.LOW_POWER_INTERMITTENT,
                isLowBatteryClamped = true,
                reason = "Battery level ($batteryPercent%) below safety threshold ($lowBatteryThresholdPercent%). Clamped to low power mode."
            )
        }

        return when {
            elapsedMinutes < 90 -> DutyCycleDecision(
                phase = SleepCyclePhase.SLEEP_ONSET,
                policy = SamplingPolicy.LOW_POWER_INTERMITTENT,
                isLowBatteryClamped = false,
                reason = "Sleep onset and deep slow-wave N3 phase ($elapsedMinutes min). Sampling 15s per 2 min."
            )
            elapsedMinutes < 240 -> DutyCycleDecision(
                phase = SleepCyclePhase.INTERMEDIATE,
                policy = SamplingPolicy.MEDIUM_POWER,
                isLowBatteryClamped = false,
                reason = "Intermediate sleep cycles ($elapsedMinutes min). Sampling 30s per 1 min."
            )
            else -> DutyCycleDecision(
                phase = SleepCyclePhase.PEAK_REM,
                policy = SamplingPolicy.CONTINUOUS_HIGH_PRECISION,
                isLowBatteryClamped = false,
                reason = "Peak circadian REM density window ($elapsedMinutes min). Continuous high-fidelity tracking active."
            )
        }
    }

    companion object {
        fun getCurrentBatteryPercentage(context: Context): Int {
            return try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, filter)
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    (level * 100 / scale)
                } else 100
            } catch (e: Exception) {
                100
            }
        }
    }
}
