package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
data class HeartRateReading(
    val timestampMs: Long,
    val bpm: Double,
    val accuracy: Int = 3
)

@Serializable
data class IbiReading(
    val timestampMs: Long,
    val ibiMs: Double // Inter-Beat Interval in milliseconds
)

@Serializable
data class MotionReading(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Double = Math.sqrt((x * x + y * y + z * z).toDouble())
)

@Serializable
data class SensorWindow(
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val meanHr: Double,
    val minHr: Double,
    val maxHr: Double,
    val hrStdDev: Double,
    val ibiMeanMs: Double,
    val rmssd: Double, // Root Mean Square of Successive Differences (HRV proxy)
    val sdnn: Double,  // Standard Deviation of NN intervals
    val movementIndex: Double, // Aggregated movement score in window (0.0 = still, 1.0+ = active)
    val sampleCount: Int,
    val confidence: Double = 0.0
)
