package com.luciddream.algorithm

import com.luciddream.model.*
import kotlin.math.max
import kotlin.math.min

/**
 * Post-hoc calibration engine that analyzes nocturnal cue events against
 * Samsung Health imported sleep stages, optimizing user baseline thresholds.
 */
class CalibrationEngine {

    data class CalibrationResult(
        val totalCuesDelivered: Int,
        val cuesInRemStage: Int,
        val cuesInNonRemStage: Int,
        val cuesInAwakeStage: Int,
        val wakeSpikesCount: Int,
        val remAccuracyProxy: Double, // Cues in REM / Total Cues
        val wakeSpikeRate: Double,    // Wake Spikes / Total Cues
        val adaptedProfile: UserProfile,
        val recommendations: List<String>
    )

    /**
     * Correlates delivered cues with Samsung Health sleep stages and adapts user settings.
     */
    fun calibrate(
        session: NightSession,
        sleepImport: SleepImport?,
        morningReport: MorningReport?,
        currentProfile: UserProfile
    ): CalibrationResult {
        val cues = session.cueEvents
        if (cues.isEmpty()) {
            return CalibrationResult(
                totalCuesDelivered = 0,
                cuesInRemStage = 0,
                cuesInNonRemStage = 0,
                cuesInAwakeStage = 0,
                wakeSpikesCount = 0,
                remAccuracyProxy = 0.0,
                wakeSpikeRate = 0.0,
                adaptedProfile = currentProfile,
                recommendations = listOf("No cues were delivered in this session.")
            )
        }

        var remHits = 0
        var nonRemHits = 0
        var awakeHits = 0
        var wakeSpikes = cues.count { it.wakeSpikeAfter }

        if (sleepImport != null && sleepImport.stages.isNotEmpty()) {
            for (cue in cues) {
                val matchingStage = sleepImport.stages.find { stage ->
                    cue.timestampMs >= stage.startTimestampMs && cue.timestampMs <= stage.endTimestampMs
                }
                when (matchingStage?.stage) {
                    SleepStage.REM -> remHits++
                    SleepStage.LIGHT, SleepStage.DEEP -> nonRemHits++
                    SleepStage.AWAKE -> awakeHits++
                    null -> {
                        // If outside exact stage bounds, count as non-rem for strict accuracy
                        nonRemHits++
                    }
                }
            }
        } else {
            // Fallback estimation when Samsung Health stages are not yet imported
            remHits = cues.count { it.confidenceScoreAtTrigger >= 0.70 }
            nonRemHits = cues.size - remHits
        }

        val accuracyProxy = remHits.toDouble() / cues.size.toDouble()
        val wakeSpikeRate = wakeSpikes.toDouble() / cues.size.toDouble()

        // Adaptive parameter updates
        var updatedHaptic = currentProfile.preferredHapticIntensity
        var updatedCooldown = currentProfile.cooldownMinutes
        val recommendations = mutableListOf<String>()

        // 1. High wake spike rate -> signal is too intrusive or frequent
        if (wakeSpikeRate >= 0.30 || morningReport?.falseAwakening == true) {
            updatedHaptic = max(0.2, updatedHaptic - 0.1)
            updatedCooldown = min(25, updatedCooldown + 5)
            recommendations.add("Снижена интенсивность вибрации и увеличен cooldown для предотвращения пробуждений.")
        }

        // 2. High REM accuracy and good recall with no wake spikes -> optimal calibration
        if (accuracyProxy >= 0.70 && wakeSpikeRate == 0.0) {
            recommendations.add("Высокая точность попадания в фазу REM. Текущие пороги эффективны.")
        }

        // 3. Cues delivered but unnoticed in dream and no wake spikes -> slight intensity nudge
        if (morningReport != null && !morningReport.cueDetectedInDream && wakeSpikes == 0 && accuracyProxy >= 0.60) {
            if (updatedHaptic < 0.8) {
                updatedHaptic = min(0.8, updatedHaptic + 0.05)
                recommendations.add("Сигналы не были замечены во сне. Мягко увеличена интенсивность вибрации (+5%).")
            }
        }

        // Calculate average HR from session sensor windows to refine baseline
        val sessionMeanHr = session.sensorWindows.map { it.meanHr }.filter { it > 40 && it < 100 }.average()
        val updatedBaselineHr = if (!sessionMeanHr.isNaN()) {
            (currentProfile.baselineHeartRate * 0.7) + (sessionMeanHr * 0.3)
        } else {
            currentProfile.baselineHeartRate
        }

        val adaptedProfile = currentProfile.copy(
            preferredHapticIntensity = updatedHaptic,
            cooldownMinutes = updatedCooldown,
            baselineHeartRate = updatedBaselineHr,
            calibrationNightsCompleted = currentProfile.calibrationNightsCompleted + 1
        )

        return CalibrationResult(
            totalCuesDelivered = cues.size,
            cuesInRemStage = remHits,
            cuesInNonRemStage = nonRemHits,
            cuesInAwakeStage = awakeHits,
            wakeSpikesCount = wakeSpikes,
            remAccuracyProxy = accuracyProxy,
            wakeSpikeRate = wakeSpikeRate,
            adaptedProfile = adaptedProfile,
            recommendations = recommendations
        )
    }
}
