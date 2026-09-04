package com.luciddream.data

import com.luciddream.data.repository.AnalyticsRepository
import com.luciddream.data.repository.InMemoryNightSessionRepository
import com.luciddream.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnalyticsRepositoryTest {

    private val sessionRepo = InMemoryNightSessionRepository()
    private val analyticsRepo = AnalyticsRepository(sessionRepo)

    @Test
    fun `computes accurate aggregate metrics across sessions and morning reports`() {
        val cue1 = CueEvent("c1", "s1", 1000, 300, CueType.HAPTIC_VIBRATION, 0.5, 0.8, wakeSpikeAfter = false)
        val cue2 = CueEvent("c2", "s2", 2000, 320, CueType.COMBINED, 0.5, 0.85, wakeSpikeAfter = true)

        val session1 = NightSession("s1", startTimeMs = 0, mode = NightMode.TLR, cueEvents = listOf(cue1))
        val session2 = NightSession("s2", startTimeMs = 0, mode = NightMode.WBTB, cueEvents = listOf(cue2))

        val report1 = MorningReport("r1", "s1", 10000, hadDreams = true, recallScore = 4, lucidSuccess = true, cueDetectedInDream = true, falseAwakening = false)
        val report2 = MorningReport("r2", "s2", 20000, hadDreams = true, recallScore = 3, lucidSuccess = false, cueDetectedInDream = false, falseAwakening = false)

        val analytics = analyticsRepo.computeAnalytics(listOf(session1, session2), listOf(report1, report2))

        assertEquals(2, analytics.totalSessionsCount)
        assertEquals(2, analytics.totalMorningReportsCount)
        assertEquals(100.0, analytics.recallPercentage, 0.01)
        assertEquals(50.0, analytics.lucidPercentage, 0.01)
        assertEquals(50.0, analytics.wakeSpikePercentage, 0.01)
        assertEquals(100.0, analytics.successByMode[NightMode.TLR.name]!!, 0.01)
        assertEquals(0.0, analytics.successByMode[NightMode.WBTB.name]!!, 0.01)
    }
}
