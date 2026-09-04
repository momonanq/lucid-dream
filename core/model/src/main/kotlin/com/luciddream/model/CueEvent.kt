package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
enum class CueType {
    HAPTIC_VIBRATION,
    AUDIO_BEEP,
    COMBINED
}

@Serializable
enum class CueOutcome {
    PENDING_FEEDBACK,
    NOTICED_IN_DREAM_LUCID,
    NOTICED_IN_DREAM_NO_LUCID,
    WOKE_UP_IMMEDIATELY, // Wake spike triggered
    MISSED_OR_IGNORED
}

@Serializable
data class CueEvent(
    val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val minutesFromSleepStart: Long,
    val cueType: CueType,
    val intensity: Double, // 0.0 to 1.0
    val confidenceScoreAtTrigger: Double,
    val acknowledged: Boolean = false,
    val wakeSpikeAfter: Boolean = false,
    val outcome: CueOutcome = CueOutcome.PENDING_FEEDBACK
)
