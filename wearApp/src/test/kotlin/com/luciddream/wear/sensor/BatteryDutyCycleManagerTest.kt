package com.luciddream.wear.sensor

import com.luciddream.model.HeartRateReading
import com.luciddream.model.IbiReading
import com.luciddream.model.MotionReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BatteryDutyCycleManagerTest {

    private val dutyCycleManager = BatteryDutyCycleManager(lowBatteryThresholdPercent = 20)

    @Test
    fun `sleep onset phase 0 to 90 min selects low power intermittent policy`() {
        val decisionEarly = dutyCycleManager.evaluate(elapsedMinutes = 10, batteryPercent = 85)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.SLEEP_ONSET, decisionEarly.phase)
        assertEquals(SamplingPolicy.LOW_POWER_INTERMITTENT, decisionEarly.policy)
        assertFalse(decisionEarly.isLowBatteryClamped)

        val decisionAt89m = dutyCycleManager.evaluate(elapsedMinutes = 89, batteryPercent = 80)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.SLEEP_ONSET, decisionAt89m.phase)
        assertEquals(SamplingPolicy.LOW_POWER_INTERMITTENT, decisionAt89m.policy)
    }

    @Test
    fun `intermediate sleep cycles 90 to 240 min select medium power policy`() {
        val decisionAt90m = dutyCycleManager.evaluate(elapsedMinutes = 90, batteryPercent = 75)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.INTERMEDIATE, decisionAt90m.phase)
        assertEquals(SamplingPolicy.MEDIUM_POWER, decisionAt90m.policy)
        assertFalse(decisionAt90m.isLowBatteryClamped)

        val decisionAt239m = dutyCycleManager.evaluate(elapsedMinutes = 239, batteryPercent = 60)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.INTERMEDIATE, decisionAt239m.phase)
        assertEquals(SamplingPolicy.MEDIUM_POWER, decisionAt239m.policy)
    }

    @Test
    fun `peak REM density window 240m plus selects continuous high precision policy`() {
        val decisionAt240m = dutyCycleManager.evaluate(elapsedMinutes = 240, batteryPercent = 55)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.PEAK_REM, decisionAt240m.phase)
        assertEquals(SamplingPolicy.CONTINUOUS_HIGH_PRECISION, decisionAt240m.policy)
        assertFalse(decisionAt240m.isLowBatteryClamped)

        val decisionAt360m = dutyCycleManager.evaluate(elapsedMinutes = 360, batteryPercent = 45)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.PEAK_REM, decisionAt360m.phase)
        assertEquals(SamplingPolicy.CONTINUOUS_HIGH_PRECISION, decisionAt360m.policy)
    }

    @Test
    fun `low battery under 20 percent forces low power saver clamp even in peak REM`() {
        val decisionLowBatInRem = dutyCycleManager.evaluate(elapsedMinutes = 330, batteryPercent = 14)
        assertEquals(BatteryDutyCycleManager.SleepCyclePhase.LOW_BATTERY_SAVER, decisionLowBatInRem.phase)
        assertEquals(SamplingPolicy.LOW_POWER_INTERMITTENT, decisionLowBatInRem.policy)
        assertTrue(decisionLowBatInRem.isLowBatteryClamped)
        assertTrue(decisionLowBatInRem.reason.contains("below safety threshold"))
    }

    @Test
    fun `simulated sensor data source emits samples to callback`() = runTest {
        val source = SimulatedSensorDataSource(this)
        val receivedHrs = mutableListOf<HeartRateReading>()
        val receivedIbis = mutableListOf<IbiReading>()
        val receivedMotions = mutableListOf<MotionReading>()

        source.start(object : SensorDataCallback {
            override fun onHeartRate(reading: HeartRateReading) {
                receivedHrs.add(reading)
            }
            override fun onIbi(reading: IbiReading) {
                receivedIbis.add(reading)
            }
            override fun onMotion(reading: MotionReading) {
                receivedMotions.add(reading)
            }
        })

        delay(1200L)
        source.stop()

        assertTrue(receivedHrs.isNotEmpty())
        assertTrue(receivedIbis.isNotEmpty())
        assertTrue(receivedMotions.isNotEmpty())
        assertEquals(SourceFidelity.SIMULATED, source.fidelity)
    }
}
