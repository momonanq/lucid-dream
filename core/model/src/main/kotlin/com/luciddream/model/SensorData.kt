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
    val meanHr: Double = 60.0,
    val minHr: Double = 55.0,
    val maxHr: Double = 65.0,
    val hrStdDev: Double = 2.0,
    val ibiMeanMs: Double = 1000.0,
    val rmssd: Double = 45.0, // Root Mean Square of Successive Differences (HRV proxy)
    val sdnn: Double = 50.0,  // Standard Deviation of NN intervals
    val movementIndex: Double = 0.05, // Aggregated movement score in window (0.0 = still, 1.0+ = active)
    val sampleCount: Int = 60,
    val confidence: Double = 0.0,
    val hrSampleCount: Int = 20,
    val ibiSampleCount: Int = 20,
    val motionSampleCount: Int = 20,
    val isDataSufficient: Boolean = true
)

