package com.luciddream.algorithm

import com.luciddream.model.*

/**
 * Deterministic decision engine responsible for deciding whether to deliver a nocturnal cue.
 * Enforces safety guardrails, cooldown periods, and wake-spike abort criteria.
 */
class NightCueDecisionEngine(
    /**
     * Fixed threshold that overrides the per-user calibrated one.
     * Leave null in production so the personalized [UserProfile.confidenceThreshold] applies;
     * set it only to pin a threshold in tests or diagnostics.
     */
    private val confidenceThresholdOverride: Double? = null,
    private val wakeSpikeMovementThreshold: Double = 0.40,
    private val wakeSpikeHrSurgePercent: Double = 0.25 // 25% surge over window baseline
) {

    sealed class Decision {
        data class TriggerCue(
            val cueType: CueType,
            val intensity: Double,
            val confidence: Double,
            val reason: String
        ) : Decision()

        data class Suppressed(val reason: String) : Decision()
    }

    /**
     * Evaluates current night state to produce a safe cue decision.
     */
    fun evaluate(
        session: NightSession,
        currentWindow: SensorWindow,
        minutesFromSleepStart: Long,
        confidence: Double,
        userProfile: UserProfile
    ): Decision {
        if (session.status != SessionStatus.RUNNING) {
            return Decision.Suppressed("Session is not running (status=${session.status})")
        }

        // Beginner mode never delivers night sensory cues (pure recall & reality checks)
        if (session.mode == NightMode.BEGINNER) {
            return Decision.Suppressed("Night cues disabled in Beginner mode")
        }

        // Safety Guardrail: Strict data sufficiency requirement (prevents cues on sensor failure / disconnect)
        if (!currentWindow.isDataSufficient) {
            return Decision.Suppressed(
                "Insufficient sensor data in window (HR: ${currentWindow.hrSampleCount}, IBI: ${currentWindow.ibiSampleCount}, Motion: ${currentWindow.motionSampleCount})"
            )
        }

        // Safety Guardrail 1: Do not disrupt early sleep architecture (N3 / slow-wave sleep in first 90m)
        val earliestAllowed = session.earliestCueMinutes.toLong()
        if (minutesFromSleepStart < earliestAllowed) {
            return Decision.Suppressed(
                "Elapsed time ($minutesFromSleepStart min) is earlier than allowed ($earliestAllowed min)"
            )
        }

        // Safety Guardrail 2: Enforce max cues limit per night
        if (session.cuesTriggered >= session.cuesPlanned) {
            return Decision.Suppressed(
                "Max cues reached for this session (${session.cuesTriggered}/${session.cuesPlanned})"
            )
        }

        // Safety Guardrail 3: Enforce post-cue cooldown (default 15-20 minutes)
        val lastCue = session.cueEvents.maxByOrNull { it.timestampMs }
        if (lastCue != null) {
            val elapsedSinceLastCueMin = (currentWindow.endTimestampMs - lastCue.timestampMs) / 60000
            if (elapsedSinceLastCueMin < session.cooldownMinutes) {
                return Decision.Suppressed(
                    "Cooldown active: $elapsedSinceLastCueMin min elapsed of ${session.cooldownMinutes} min required"
                )
            }
        }

        // Safety Guardrail 4: Confidence threshold check (uses user profile personalized threshold)
        val activeThreshold = confidenceThresholdOverride ?: userProfile.confidenceThreshold
        if (confidence < activeThreshold) {
            return Decision.Suppressed(
                "Confidence score $confidence is below threshold $activeThreshold"
            )
        }

        // Guardrail 5: Motor stillness confirmation on current window
        if (currentWindow.movementIndex >= wakeSpikeMovementThreshold) {
            return Decision.Suppressed(
                "High motion detected in current window (${currentWindow.movementIndex})"
            )
        }

        // Determine cue modality based on session mode and user preferences
        val cueType = when (session.mode) {
            NightMode.TLR -> if (session.audioEnabled) CueType.COMBINED else CueType.HAPTIC_VIBRATION
            NightMode.WBTB -> if (session.audioEnabled) CueType.COMBINED else CueType.HAPTIC_VIBRATION
            NightMode.WATCH_ASSIST -> CueType.HAPTIC_VIBRATION
            NightMode.BEGINNER -> CueType.HAPTIC_VIBRATION
        }

        val intensity = session.hapticIntensity.coerceIn(0.1, 1.0)

        return Decision.TriggerCue(
            cueType = cueType,
            intensity = intensity,
            confidence = confidence,
            reason = "High REM probability ($confidence) in late-night cycle ($minutesFromSleepStart min)"
        )
    }

    /**
     * Detects if a wake-spike occurred immediately following a cue delivery.
     * If motor activity or HR surges significantly, the cue likely awakened the user,
     * requiring post-hoc classification and threshold adaptation.
     */
    fun checkForWakeSpike(
        preCueWindow: SensorWindow,
        postCueWindow: SensorWindow
    ): Boolean {
        // High movement surge post-cue
        if (postCueWindow.movementIndex >= wakeSpikeMovementThreshold) {
            return true
        }

        // Significant heart rate jump post-cue
        if (preCueWindow.meanHr > 0) {
            val hrDeltaPercent = (postCueWindow.meanHr - preCueWindow.meanHr) / preCueWindow.meanHr
            if (hrDeltaPercent >= wakeSpikeHrSurgePercent) {
                return true
            }
        }

        return false
    }
}
