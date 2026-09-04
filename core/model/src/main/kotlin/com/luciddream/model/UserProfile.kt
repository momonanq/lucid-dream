package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
enum class LucidGoal {
    MORE_LUCID_DREAMS,
    NIGHTMARE_RESOLUTION,
    DREAM_RECALL_JOURNAL,
    SKILL_PRACTICE,
    EXPLORATION
}

@Serializable
enum class CuePreference {
    VIBRATION_ONLY,
    AUDIO_ONLY,
    COMBINED_VIBRATION_AND_AUDIO
}

@Serializable
data class UserProfile(
    val id: String = "default_user",
    val bedtimeTargetMinutes: Int = 23 * 60, // 23:00 in minutes of day
    val wakeTargetMinutes: Int = 7 * 60,     // 07:00 in minutes of day
    val lucidGoal: LucidGoal = LucidGoal.MORE_LUCID_DREAMS,
    val cuePreference: CuePreference = CuePreference.VIBRATION_ONLY,
    val watchModel: String = "Galaxy Watch 4+",
    val baselineHeartRate: Double = 60.0,
    val baselineIbiVariance: Double = 45.0,
    val preferredHapticIntensity: Double = 0.5, // 0.0 to 1.0
    val maxCuesPerNight: Int = 5,
    val cooldownMinutes: Int = 15,
    val earliestCueMinutesAfterOnset: Int = 90,
    val confidenceThreshold: Double = 0.65,
    /** Onboarding safety screening; gates whether nocturnal cue modes may run at all. */
    val screening: SafetyScreening = SafetyScreening(),
    val calibrationNightsCompleted: Int = 0
)
