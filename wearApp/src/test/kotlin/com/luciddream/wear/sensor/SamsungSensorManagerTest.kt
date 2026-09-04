package com.luciddream.wear.sensor

import com.luciddream.model.HeartRateReading
import com.luciddream.model.IbiReading
import com.luciddream.model.MotionReading
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SamsungSensorManagerTest {

    @Test
    fun `empty buffers produce insufficient data window with no fake REM metrics`() = runTest {
        val manager = SamsungSensorManager()
        manager.startTracking()

        val window = manager.aggregateWindow(100000L, 160000L)

        assertFalse(window.isDataSufficient, "Empty buffer must mark isDataSufficient as false")
        assertEquals(0, window.hrSampleCount)
        assertEquals(0, window.ibiSampleCount)
        assertEquals(0, window.motionSampleCount)
        assertEquals(0.0, window.meanHr, 0.001)
        assertEquals(0.0, window.rmssd, 0.001)
        // Movement index must NOT be 0.0 (stillness) when data is absent
        assertTrue(window.movementIndex >= 0.5, "Movement index should be non-zero on empty data to prevent fake stillness")
    }

    @Test
    fun `preserves future samples across consecutive window aggregations without loss`() = runTest {
        val manager = SamsungSensorManager()
        manager.startTracking()

        val window1Start = 100000L
        val window1End = 160000L
        val window2Start = 160000L
        val window2End = 220000L

        // Feed 10 samples for Window 1
        for (i in 0 until 10) {
            val t = window1Start + (i * 5000L)
            manager.onHeartRateSample(HeartRateReading(t, 60.0))
            manager.onIbiSample(IbiReading(t, 1000.0))
            manager.onMotionSample(MotionReading(t, 0.01f, 0.01f, 9.8f))
        }

        // Feed 10 samples that arrive early but belong to Window 2!
        for (i in 0 until 10) {
            val t = window2Start + (i * 5000L) + 1000L
            manager.onHeartRateSample(HeartRateReading(t, 62.0))
            manager.onIbiSample(IbiReading(t, 980.0))
            manager.onMotionSample(MotionReading(t, 0.02f, 0.02f, 9.8f))
        }

        // Aggregate Window 1
        val win1 = manager.aggregateWindow(window1Start, window1End)
        assertTrue(win1.isDataSufficient)
        assertEquals(10, win1.hrSampleCount)
        assertEquals(10, win1.ibiSampleCount)
        assertEquals(10, win1.motionSampleCount)

        // Aggregate Window 2 - future samples must NOT have been discarded during win1 aggregation
        val win2 = manager.aggregateWindow(window2Start, window2End)
        assertTrue(win2.isDataSufficient, "Window 2 must retain buffered samples")
        assertEquals(10, win2.hrSampleCount, "HR samples for window 2 should be preserved")
        assertEquals(10, win2.ibiSampleCount, "IBI samples for window 2 should be preserved")
        assertEquals(10, win2.motionSampleCount, "Motion samples for window 2 should be preserved")
        assertEquals(62.0, win2.meanHr, 0.1)
    }

    @Test
    fun `isolated modality insufficiency triggers insufficient flag`() = runTest {
        val manager = SamsungSensorManager()
        manager.startTracking()

        val start = 100000L
        val end = 160000L

        // Feed 20 motion samples, but 0 HR and 0 IBI (e.g. watch off wrist or HR sensor failed)
        for (i in 0 until 20) {
            val t = start + (i * 2000L)
            manager.onMotionSample(MotionReading(t, 0.01f, 0.01f, 9.8f))
        }

        val window = manager.aggregateWindow(start, end)
        assertEquals(20, window.motionSampleCount)
        assertEquals(0, window.hrSampleCount)
        assertEquals(0, window.ibiSampleCount)
        assertFalse(window.isDataSufficient, "Window with missing HR and IBI must be insufficient")
    }
}
