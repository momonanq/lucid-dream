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
    private val context: Context,
    /**
     * Set false when a higher-fidelity source supplies heart rate, so this one contributes motion
     * only. Without it the Samsung source would register both sensors and every beat would be
     * counted twice.
     */
    private val provideHeartRate: Boolean = true
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

        if (provideHeartRate) {
            hrSensor?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
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

        // createSamsungSensorDataSource resolves to the real SDK integration only when the
        // proprietary AAR is present at build time; otherwise it returns null. Falling back to the
        // standard source is safe because that source reports its own fidelity honestly, so the
        // watch UI and the scoring engine both learn that no inter-beat intervals are coming.
        val isSamsungWatch = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
        val hasHealthSensorService = runCatching {
            context.packageManager.getPackageInfo(SAMSUNG_HEALTH_SERVICE_PACKAGE, 0) != null
        }.getOrDefault(false)

        if (isSamsungWatch && hasHealthSensorService) {
            createSamsungSensorDataSource(context)?.let { return it }
        }

        return AndroidStandardSensorDataSource(context)
    }

    /**
     * Package hosting the Health Tracking Service on a Galaxy Watch.
     *
     * Verified on a Galaxy Watch Ultra (SM-L705F): the installed package is
     * `com.samsung.android.service.health`; there is no `...health.sensor`, which an earlier guard
     * looked for and therefore never matched. The SDK's own manifest agrees — it declares
     * <queries> for exactly this name, which is also what makes the package visible to us at all
     * under Android 11+ package visibility rules.
     */
    private const val SAMSUNG_HEALTH_SERVICE_PACKAGE = "com.samsung.android.service.health"
}
