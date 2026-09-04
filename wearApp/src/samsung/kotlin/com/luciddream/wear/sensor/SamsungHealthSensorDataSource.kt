package com.luciddream.wear.sensor

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import com.luciddream.model.HeartRateReading
import com.luciddream.model.IbiReading

/**
 * Real inter-beat intervals from the Samsung Health Sensor SDK.
 *
 * This is the only source that can supply IBI on a Galaxy Watch, and therefore the only one on
 * which [com.luciddream.algorithm.RemConfidenceEngine] can score the HRV term at all. Compiled
 * only when the SDK AAR is present in `wearApp/libs/` (see the module build script).
 *
 * Requires either an approved Samsung partner request or Health Sensor Service developer mode.
 */
class SamsungHealthSensorDataSource(
    private val context: Context,
    /**
     * Motion still comes from the platform accelerometer.
     *
     * Samsung exposes ACCELEROMETER_X/Y/Z as raw integers whose scale factor is not stated in the
     * SDK documentation, while `android.hardware.Sensor` guarantees m/s². `movementIndex` divides
     * by 9.81, so guessing the Samsung scale would quietly distort every motion score — and motion
     * carries more weight (0.30) than HRV (0.20). Taking each signal from the source that defines
     * its units is worth the second sensor registration.
     */
    private val motionSource: AndroidStandardSensorDataSource =
        AndroidStandardSensorDataSource(context, provideHeartRate = false)
) : SensorDataSource {

    override val fidelity: SourceFidelity = SourceFidelity.SAMSUNG_CONTINUOUS_IBI

    private var trackingService: HealthTrackingService? = null
    private var heartRateTracker: HealthTracker? = null
    private var callback: SensorDataCallback? = null

    @Volatile
    private var connected = false

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            connected = true
            startHeartRateTracker()
        }

        override fun onConnectionEnded() {
            connected = false
        }

        override fun onConnectionFailed(exception: HealthTrackerException?) {
            connected = false
            // No fallback here on purpose. Silently degrading to the standard sensor would report
            // SAMSUNG_CONTINUOUS_IBI fidelity while delivering no IBI, which is exactly the lie
            // that made the watch claim "Samsung IBI Active" with nothing attached. With no data
            // flowing, windows come out insufficient and the decision engine withholds cues.
            Log.e(TAG, "Samsung Health Tracking connection failed", exception)
        }
    }

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            val cb = callback ?: return
            dataPoints.forEach { point -> emitFrom(point, cb) }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError?) {
            Log.e(TAG, "Heart rate tracker error: $error")
        }
    }

    override fun start(callback: SensorDataCallback) {
        this.callback = callback
        motionSource.start(callback)

        try {
            trackingService = HealthTrackingService(connectionListener, context).also {
                it.connectService()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to bind Samsung Health Tracking Service", e)
        }
    }

    override fun stop() {
        motionSource.stop()
        runCatching { heartRateTracker?.unsetEventListener() }
        heartRateTracker = null
        runCatching { trackingService?.disconnectService() }
        trackingService = null
        connected = false
        callback = null
    }

    /**
     * The Samsung tracker streams continuously and exposes no sampling-rate control, so duty
     * cycling applies to the platform accelerometer only. Battery is managed by starting and
     * stopping the session rather than by thinning this stream.
     */
    override fun setSamplingPolicy(policy: SamplingPolicy) {
        motionSource.setSamplingPolicy(policy)
    }

    private fun startHeartRateTracker() {
        val service = trackingService ?: return
        try {
            heartRateTracker = service
                .getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                .also { it.setEventListener(trackerListener) }
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to obtain HEART_RATE_CONTINUOUS tracker", e)
        }
    }

    private fun emitFrom(point: DataPoint, cb: SensorDataCallback) {
        val timestamp = point.timestamp

        // HEART_RATE_STATUS: 1 is a successful measurement. Everything else means the sensor could
        // not read — detached wearable (-3), movement (-2), weak PPG (-8, -10), a higher priority
        // sensor holding the hardware (-999), or the initial state (0). Publishing those as a
        // heart rate would put a fabricated value into the confidence score.
        val hrStatus = point.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
        if (hrStatus == HR_STATUS_OK) {
            val bpm = point.getValue(ValueKey.HeartRateSet.HEART_RATE)?.toDouble()
            if (bpm != null && bpm in PLAUSIBLE_BPM) {
                cb.onHeartRate(HeartRateReading(timestamp, bpm))
            }
        }

        emitIbis(point, timestamp, cb)
    }

    /**
     * A data point carries several intervals at once, each with its own validity flag, and one
     * timestamp for the whole point. The intervals are the beats that led up to that instant, so
     * timestamps are reconstructed by walking backwards from it.
     */
    private fun emitIbis(point: DataPoint, pointTimestamp: Long, cb: SensorDataCallback) {
        val intervals = point.getValue(ValueKey.HeartRateSet.IBI_LIST) ?: return
        val statuses = point.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)

        var offsetFromEnd = 0L
        for (index in intervals.indices.reversed()) {
            val intervalMs = intervals[index]?.toLong() ?: continue

            // IBI_STATUS_LIST: 0 is Normal, -1 is Error. A missing status list is treated as
            // unusable rather than assumed valid: an unverified interval is what this whole
            // sensor path exists to avoid.
            val status = statuses?.getOrNull(index)
            val usable = status == IBI_STATUS_OK && intervalMs in PLAUSIBLE_IBI_MS

            if (usable) {
                cb.onIbi(IbiReading(pointTimestamp - offsetFromEnd, intervalMs.toDouble()))
            }
            offsetFromEnd += intervalMs
        }
    }

    private companion object {
        const val TAG = "SamsungSensorSource"
        const val HR_STATUS_OK = 1
        const val IBI_STATUS_OK = 0
        val PLAUSIBLE_BPM = 30.0..220.0
        val PLAUSIBLE_IBI_MS = 270L..2000L // ~30-220 bpm
    }
}

/**
 * Builds the Samsung-backed source. The no-SDK variant of this file returns null instead, which is
 * how [SensorDataSourceFactory] stays compilable without the proprietary AAR.
 */
fun createSamsungSensorDataSource(context: Context): SensorDataSource? =
    SamsungHealthSensorDataSource(context)
