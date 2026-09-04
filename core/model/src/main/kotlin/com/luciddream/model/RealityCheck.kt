package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
enum class RealityCheckType {
    FINGER_COUNT,          // Counting fingers, checking hand anatomy
    TEXT_RE_READ,          // Reading digital/printed text twice to observe stability
    BREATH_TEST,           // Pinching nose and testing if breathing is possible
    PHYSICAL_ENVIRONMENT, // Checking memory of how user arrived in the current room
    TIME_CHECK             // Checking watch face, looking away and checking again
}

@Serializable
enum class InductionTechnique {
    MILD,                  // Mnemonic Induction of Lucid Dreams
    WBTB,                  // Wake-Back-To-Bed
    SSILD,                 // Senses Initiated Lucid Dream
    ADA,                   // All-Day Awareness
    REALITY_CHECK          // Daytime Reality Check drill
}

@Serializable
data class RealityCheckPrompt(
    val id: String,
    val type: RealityCheckType,
    val title: String,
    val instruction: String,
    val prospectiveTrigger: String? = null // e.g. "Whenever you walk through a door"
)

@Serializable
data class RealityCheckLog(
    val id: String,
    val timestampMs: Long,
    val type: RealityCheckType,
    val wasMindful: Boolean,
    val doubtRealityGenerated: Boolean,
    val note: String = ""
)
