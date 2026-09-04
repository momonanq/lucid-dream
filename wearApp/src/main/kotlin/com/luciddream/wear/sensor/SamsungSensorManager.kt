package com.luciddream.wear.sensor

import com.luciddream.model.HeartRateReading
import com.luciddream.model.IbiReading
import com.luciddream.model.MotionReading
import com.luciddream.model.SensorWindow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Sensor ingestion & windowing manager for Galaxy Watch.
 * Connects to Samsung Health Sensor SDK (continuous HR, IBI) and 3-axis Accelerometer.
 */
class SamsungSensorManager {

    private val hrBuffer = ConcurrentLinkedQueue<HeartRateReading>()
    private val ibiBuffer = ConcurrentLinkedQueue<IbiReading>()
    private val motionBuffer = ConcurrentLinkedQueue<MotionReading>()

    private val _windowFlow = MutableSharedFlow<SensorWindow>(extraBufferCapacity = 64)
    val windowFlow: SharedFlow<SensorWindow> = _windowFlow.asSharedFlow()

    private var isTracking = false

    fun startTracking() {
        hrBuffer.clear()
        ibiBuffer.clear()
        motionBuffer.clear()
        isTracking = true
    }

    fun stopTracking() {
        isTracking = false
    }

    fun onHeartRateSample(reading: HeartRateReading) {
        if (!isTracking) return
        hrBuffer.add(reading)
    }

    fun onIbiSample(reading: IbiReading) {
        if (!isTracking) return
        ibiBuffer.add(reading)
    }

    fun onMotionSample(reading: MotionReading) {
        if (!isTracking) return
        motionBuffer.add(reading)
    }

    /**
     * Aggregates the buffered continuous sensor metrics into a cohesive SensorWindow.
     * Only consumes samples up to endTimestampMs, leaving subsequent samples in the queue.
     */
    suspend fun aggregateWindow(startTimestampMs: Long, endTimestampMs: Long): SensorWindow {
        val hrs = mutableListOf<HeartRateReading>()
        while (true) {
            val r = hrBuffer.peek() ?: break
            if (r.timestampMs < startTimestampMs) {
                hrBuffer.poll() // discard stale sample preceding window
            } else if (r.timestampMs <= endTimestampMs) {
                hrs.add(hrBuffer.poll() ?: break)
            } else {
                break // belongs to subsequent window; keep in queue
            }
        }

        val ibis = mutableListOf<IbiReading>()
        while (true) {
            val r = ibiBuffer.peek() ?: break
            if (r.timestampMs < startTimestampMs) {
                ibiBuffer.poll() // discard stale sample preceding window
            } else if (r.timestampMs <= endTimestampMs) {
                ibis.add(ibiBuffer.poll() ?: break)
            } else {
                break // belongs to subsequent window; keep in queue
            }
        }

        val motions = mutableListOf<MotionReading>()
        while (true) {
            val r = motionBuffer.peek() ?: break
            if (r.timestampMs < startTimestampMs) {
                motionBuffer.poll() // discard stale sample preceding window
            } else if (r.timestampMs <= endTimestampMs) {
                motions.add(motionBuffer.poll() ?: break)
            } else {
                break // belongs to subsequent window; keep in queue
            }
        }

        // Modality-specific minimum viable sample count checks:
        // Motion: >= 3 samples to evaluate movement variance
        // HR: >= 3 samples to calculate average/min/max
        // IBI: >= 5 samples to compute RMSSD/SDNN meaningfully
        val isSufficient = hrs.size >= 3 && ibis.size >= 5 && motions.size >= 3

        val meanHr = if (hrs.isNotEmpty()) hrs.map { it.bpm }.average() else 0.0
        val minHr = if (hrs.isNotEmpty()) hrs.minOf { it.bpm } else 0.0
        val maxHr = if (hrs.isNotEmpty()) hrs.maxOf { it.bpm } else 0.0

        val hrStdDev = if (hrs.size > 1) {
            val variance = hrs.map { (it.bpm - meanHr).pow(2) }.average()
            sqrt(variance)
        } else 0.0

        val meanIbi = if (ibis.isNotEmpty()) ibis.map { it.ibiMs }.average() else 0.0

        // RMSSD (Root Mean Square of Successive Differences)
        val rmssd = if (ibis.size > 2) {
            var sumDiffSq = 0.0
            for (i in 0 until ibis.size - 1) {
                val diff = ibis[i + 1].ibiMs - ibis[i].ibiMs
                sumDiffSq += diff.pow(2)
            }
            sqrt(sumDiffSq / (ibis.size - 1))
        } else 0.0

        // SDNN (Standard deviation of NN intervals)
        val sdnn = if (ibis.size > 1) {
            val varIbi = ibis.map { (it.ibiMs - meanIbi).pow(2) }.average()
            sqrt(varIbi)
        } else 0.0

        // Movement Index: standard deviation of accelerometer magnitude + jerk
        // When data is insufficient, DO NOT fake 0.0 (perfect stillness) which would trick REM scoring
        val movementIndex = if (motions.size > 1) {
            val meanMag = motions.map { it.magnitude }.average()
            val magVariance = motions.map { (it.magnitude - meanMag).pow(2) }.average()
            sqrt(magVariance) / 9.81 // Normalized to Gs
        } else if (isSufficient) 0.0 else 1.0

        val window = SensorWindow(
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            meanHr = meanHr,
            minHr = minHr,
            maxHr = maxHr,
            hrStdDev = hrStdDev,
            ibiMeanMs = meanIbi,
            rmssd = rmssd,
            sdnn = sdnn,
            movementIndex = movementIndex,
            sampleCount = hrs.size + ibis.size + motions.size,
            confidence = 0.0,
            hrSampleCount = hrs.size,
            ibiSampleCount = ibis.size,
            motionSampleCount = motions.size,
            isDataSufficient = isSufficient
        )

        _windowFlow.emit(window)
        return window
    }
}
