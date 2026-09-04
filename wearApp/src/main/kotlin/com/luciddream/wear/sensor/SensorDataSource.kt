package com.luciddream.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.luciddream.model.HeartRateReading
import com.luciddream.model.IbiReading
import com.luciddream.model.MotionReading
import kotlinx.coroutines.*

enum class SourceFidelity(val displayName: String, val providesIbi: Boolean) {
    SAMSUNG_CONTINUOUS_IBI("Samsung Health Sensor SDK (Continuous IBI)", providesIbi = true),

    /**
     * Ordinary Wear OS heart rate sensor. Reports averaged BPM only: beat-to-beat intervals are
     * not exposed by the platform, so no HRV metric can be derived from this source.
     */
    ANDROID_STANDARD_HR("Standard Wear OS (Heart Rate Sensor)", providesIbi = false),

    SIMULATED("Simulated Biometric Data", providesIbi = true)
}

enum class SamplingPolicy(val activeSecondsPerCycle: Int, val cyclePeriodSeconds: Int) {
    LOW_POWER_INTERMITTENT(15, 120),
    MEDIUM_POWER(30, 60),
    CONTINUOUS_HIGH_PRECISION(60, 60)
}

interface SensorDataCallback {
    fun onHeartRate(reading: HeartRateReading)
    fun onIbi(reading: IbiReading)
    fun onMotion(reading: MotionReading)
}

interface SensorDataSource {
    val fidelity: SourceFidelity
    fun start(callback: SensorDataCallback)
    fun stop()
    fun setSamplingPolicy(policy: SamplingPolicy)
}

/**
 * Standard Wear OS implementation utilizing android.hardware.SensorManager.
 * Operates on all Wear OS devices (Samsung, Google Pixel Watch, TicWatch) with graceful degradation.
 */
class AndroidStandardSensorDataSource(
    private val context: Context
) : SensorDataSource, SensorEventListener {

    override val fidelity: SourceFidelity = SourceFidelity.ANDROID_STANDARD_HR

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val hrSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var callback: SensorDataCallback? = null
    private var currentPolicy: SamplingPolicy = SamplingPolicy.CONTINUOUS_HIGH_PRECISION

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var dutyCycleJob: Job? = null
    private var isSensorsRegistered = false

    override fun start(callback: SensorDataCallback) {
        this.callback = callback
        startDutyCycleLoop()
    }

    override fun stop() {
        dutyCycleJob?.cancel()
        unregisterSensors()
        this.callback = null
    }

    override fun setSamplingPolicy(policy: SamplingPolicy) {
        if (currentPolicy != policy) {
            currentPolicy = policy
            startDutyCycleLoop()
        }
    }

    private fun startDutyCycleLoop() {
        dutyCycleJob?.cancel()
        if (currentPolicy == SamplingPolicy.CONTINUOUS_HIGH_PRECISION) {
            registerSensors()
            return
        }

        dutyCycleJob = scope.launch {
            while (isActive) {
                registerSensors()
                delay(currentPolicy.activeSecondsPerCycle * 1000L)
                unregisterSensors()
                val sleepDuration = (currentPolicy.cyclePeriodSeconds - currentPolicy.activeSecondsPerCycle).coerceAtLeast(0)
                if (sleepDuration > 0) {
                    delay(sleepDuration * 1000L)
                }
            }
        }
    }

    @Synchronized
    private fun registerSensors() {
        if (isSensorsRegistered) return
        val sm = sensorManager ?: return

        hrSensor?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelSensor?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        isSensorsRegistered = true
    }

    @Synchronized
    private fun unregisterSensors() {
        if (!isSensorsRegistered) return
        sensorManager?.unregisterListener(this)
        isSensorsRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        val now = System.currentTimeMillis()
        val cb = callback ?: return

        when (ev.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val bpm = ev.values.firstOrNull()?.toDouble() ?: return
                if (bpm in 30.0..220.0) {
                    cb.onHeartRate(HeartRateReading(now, bpm))
                }
                // Deliberately no onIbi here. TYPE_HEART_RATE reports averaged BPM; the platform
                // exposes no beat-to-beat timing. Deriving an "IBI" from the callback interval
                // would measure the sampling cadence, and 60000/bpm is just a restatement of the
                // BPM — either way RMSSD computed from it is an artefact wearing the name of a
                // physiological measure. Sources that cannot measure IBI must report none, so
                // SourceFidelity.providesIbi can exclude HRV from scoring honestly.
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (ev.values.size >= 3) {
                    cb.onMotion(MotionReading(now, ev.values[0], ev.values[1], ev.values[2]))
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/**
 * Placeholder / Stub for the Samsung Health Sensor SDK continuous PPG & IBI tracker.
 * When the Samsung Partner Privilege is granted, this tracker binds to
 * com.samsung.android.service.health.sensor.HealthTrackingService.
 */
class SamsungSensorDataSourceStub(
    private val context: Context
) : SensorDataSource {

    private val fallback = AndroidStandardSensorDataSource(context)

    /**
     * Reports what is actually delivered, not what this class is named after.
     *
     * Until the real tracker is wired up this stub is the standard Wear OS source, and claiming
     * SAMSUNG_CONTINUOUS_IBI made the watch display "Samsung IBI Active" with no SDK attached
     * and told the scoring engine that HRV was measured when it was not.
     */
    override val fidelity: SourceFidelity get() = fallback.fidelity

    override fun start(callback: SensorDataCallback) {
        // Fallback to standard sensors until partner signature is linked
        fallback.start(callback)
    }

    override fun stop() {
        fallback.stop()
    }

    override fun setSamplingPolicy(policy: SamplingPolicy) {
        fallback.setSamplingPolicy(policy)
    }
}

/**
 * Simulated sensor data source producing synthetic sleep curve data for testing and emulators.
 */
class SimulatedSensorDataSource(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SensorDataSource {
    override val fidelity: SourceFidelity = SourceFidelity.SIMULATED

    private var callback: SensorDataCallback? = null
    private var job: Job? = null

    override fun start(callback: SensorDataCallback) {
        this.callback = callback
        job = scope.launch {
            var count = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val hr = 58.0 + (count % 3)
                val ibi = (60000.0 / hr) + if (count % 2 == 0) 35.0 else -35.0
                callback.onHeartRate(HeartRateReading(now, hr))
                callback.onIbi(IbiReading(now, ibi))
                callback.onMotion(MotionReading(now, 0.01f, 0.02f, 9.8f))
                count++
                delay(1000L)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        callback = null
    }

    override fun setSamplingPolicy(policy: SamplingPolicy) {}
}

object SensorDataSourceFactory {
    fun create(context: Context, forceSimulated: Boolean = false): SensorDataSource {
        if (forceSimulated) {
            return SimulatedSensorDataSource()
        }

        val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
        val hasSamsungHealthService = try {
            val pm = context.packageManager
            pm.getPackageInfo("com.samsung.android.service.health.sensor", 0) != null
        } catch (e: Exception) {
            false
        }

        return if (isSamsung && hasSamsungHealthService) {
            SamsungSensorDataSourceStub(context)
        } else {
            AndroidStandardSensorDataSource(context)
        }
    }
}
