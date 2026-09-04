package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
data class MorningReport(
    val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val hadDreams: Boolean,
    val recallScore: Int, // 1 to 5
    val lucidSuccess: Boolean,
    val cueDetectedInDream: Boolean,
    val falseAwakening: Boolean,
    val dreamJournalId: String? = null,
    val subjectiveSleepQuality: Int = 3, // 1 to 5
    val notes: String = ""
)
