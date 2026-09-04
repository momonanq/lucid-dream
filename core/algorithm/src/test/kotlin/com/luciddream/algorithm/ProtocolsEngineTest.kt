package com.luciddream.algorithm

import com.luciddream.algorithm.protocols.MildProtocolManager
import com.luciddream.algorithm.protocols.RealityCheckScheduler
import com.luciddream.algorithm.protocols.SsildProtocolManager
import com.luciddream.algorithm.protocols.WbtbScheduler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProtocolsEngineTest {

    @Test
    fun `MILD protocol manager produces ordered rehearsal steps with mantra`() {
        val manager = MildProtocolManager()
        val steps = manager.getRehearsalSteps(customDreamSign = "неработающий лифт")

        assertEquals(5, steps.size)
        assertTrue(steps[2].mantraSuggestion?.contains("неработающий лифт") == true)
    }

    @Test
    fun `SSILD manager produces fast and slow sensory cycles across 3 modalities`() {
        val manager = SsildProtocolManager()
        val routine = manager.generateRoutine(quickCycleCount = 2, slowCycleCount = 2)

        // 2 quick cycles * 3 modalities + 2 slow cycles * 3 modalities = 12 steps
        assertEquals(12, routine.size)
        val fastSteps = routine.filter { it.isFastCycle }
        val slowSteps = routine.filter { !it.isFastCycle }

        assertEquals(6, fastSteps.size)
        assertEquals(6, slowSteps.size)
        assertEquals(5, fastSteps.first().durationSeconds)
        assertEquals(30, slowSteps.first().durationSeconds)
    }

    @Test
    fun `WBTB scheduler calculates 5-hour wake window`() {
        val scheduler = WbtbScheduler()
        val bedtime = 1000000L
        val schedule = scheduler.calculateOptimalAlarm(bedtimeMs = bedtime, preferredSleepHoursBeforeWake = 5.0)

        assertEquals(bedtime + (5 * 3600 * 1000), schedule.alarmTimeMs)
        assertEquals(5.0, schedule.sleepDurationHours)
        assertTrue(scheduler.getWakeGuidelines().isNotEmpty())
    }

    @Test
    fun `Reality check scheduler generates daytime schedule with prompts`() {
        val scheduler = RealityCheckScheduler()
        val prompts = scheduler.getDefaultPrompts()
        val times = scheduler.generateDailySchedule(dayStartMinutes = 9 * 60, dayEndMinutes = 21 * 60, totalChecksCount = 6)

        assertEquals(5, prompts.size)
        assertEquals(6, times.size)
        assertTrue(times.all { it in (9 * 60)..(21 * 60) })
    }
}
