package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
enum class DreamTag {
    LUCID,
    VIVID,
    NIGHTMARE,
    FALSE_AWAKENING,
    CUE_NOTICED,
    FLYING,
    RECURRING_LOCATION,
    MEETING_PEOPLE
}

@Serializable
data class DreamSign(
    val keyword: String,
    val category: String, // e.g. "Action", "Context", "Form", "Awareness"
    val occurrenceCount: Int = 1
)

@Serializable
data class DreamEntry(
    val id: String,
    val dateIso: String,
    val timestampMs: Long,
    val title: String,
    val transcript: String,
    val tags: Set<DreamTag> = emptySet(),
    val dreamSigns: List<DreamSign> = emptyList(),
    val clarityRating: Int = 3, // 1 to 5
    val lucidityLevel: Int = 0  // 0 (none) to 5 (full conscious control)
)
