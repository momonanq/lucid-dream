package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
enum class SleepStage {
    AWAKE,
    LIGHT,
    DEEP,
    REM
}

@Serializable
data class SleepStageInterval(
    val stage: SleepStage,
    val startTimestampMs: Long,
    val endTimestampMs: Long
) {
    val durationMinutes: Long = (endTimestampMs - startTimestampMs) / 60000
}

@Serializable
data class SleepImport(
    val id: String,
    val sessionId: String,
    val source: String = "Samsung Health Data SDK",
    val sleepScore: Int? = null,
    val totalSleepMinutes: Int,
    val remMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val stages: List<SleepStageInterval> = emptyList(),
    val averageBloodOxygenPercentage: Double? = null,
    val skinTemperatureCelsius: Double? = null
)
