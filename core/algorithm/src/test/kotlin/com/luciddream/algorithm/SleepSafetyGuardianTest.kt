package com.luciddream.algorithm

import com.luciddream.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SleepSafetyGuardianTest {

    private val guardian = SleepSafetyGuardian()

    private val clearedScreening = SafetyScreening(
        isComplete = true,
        ageYears = 32,
        acknowledgedNotMedicalDevice = true
    )

    private fun cueNight(
        daysAgo: Double,
        cueCount: Int = 1,
        spikedCues: Int = 0,
        mode: NightMode = NightMode.TLR,
        status: SessionStatus = SessionStatus.COMPLETED
    ): NightSession {
        val startMs = NOW - (daysAgo * DAY_MS).toLong()
        val cues = (0 until cueCount).map { index ->
            CueEvent(
                id = "cue_${startMs}_$index",
                sessionId = "session_$startMs",
                timestampMs = startMs + (300 * 60 * 1000L),
                minutesFromSleepStart = 300,
                cueType = CueType.HAPTIC_VIBRATION,
                intensity = 0.5,
                confidenceScoreAtTrigger = 0.8,
                wakeSpikeAfter = index < spikedCues
            )
        }
        return NightSession(
            id = "session_$startMs",
            startTimeMs = startMs,
            mode = mode,
            status = status,
            cuesTriggered = cueCount,
            cueEvents = cues
        )
    }

    private fun report(daysAgo: Double, quality: Int): MorningReport {
        val ts = NOW - (daysAgo * DAY_MS).toLong()
        return MorningReport(
            id = "report_$ts",
            sessionId = "session_$ts",
            timestampMs = ts,
            hadDreams = true,
            recallScore = 3,
            lucidSuccess = false,
            cueDetectedInDream = false,
            falseAwakening = false,
            subjectiveSleepQuality = quality
        )
    }

    private fun evaluate(
        mode: NightMode = NightMode.TLR,
        screening: SafetyScreening = clearedScreening,
        sessions: List<NightSession> = emptyList(),
        reports: List<MorningReport> = emptyList()
    ) = guardian.evaluateNight(mode, screening, sessions, reports, NOW)

    private fun triggersOf(decision: SleepSafetyGuardian.Decision): List<SleepSafetyGuardian.Trigger> {
        assertTrue(
            decision is SleepSafetyGuardian.Decision.RestNight,
            "Expected a rest night, got: $decision"
        )
        return (decision as SleepSafetyGuardian.Decision.RestNight).reasons.map { it.trigger }
    }

    @Test
    fun `allows a cue night for a screened user with no history`() {
        val decision = evaluate()

        assertTrue(decision is SleepSafetyGuardian.Decision.Allowed, "Got: $decision")
        assertEquals(NightMode.TLR, decision.effectiveMode)
        assertTrue((decision as SleepSafetyGuardian.Decision.Allowed).advisories.isEmpty())
    }

    @Test
    fun `withholds cues until the onboarding screening is completed`() {
        val decision = evaluate(screening = SafetyScreening())

        assertEquals(listOf(SleepSafetyGuardian.Trigger.SCREENING_EXCLUSION), triggersOf(decision))
        assertEquals(NightMode.BEGINNER, decision.effectiveMode)
    }

    @Test
    fun `withholds cues for excluded sleep conditions and for minors`() {
        val apnea = evaluate(screening = clearedScreening.copy(sleepApnea = true))
        assertEquals(listOf(SleepSafetyGuardian.Trigger.SCREENING_EXCLUSION), triggersOf(apnea))

        val parasomnia = evaluate(screening = clearedScreening.copy(parasomnia = true))
        assertEquals(listOf(SleepSafetyGuardian.Trigger.SCREENING_EXCLUSION), triggersOf(parasomnia))

        val minor = evaluate(screening = clearedScreening.copy(ageYears = 16))
        assertEquals(listOf(SleepSafetyGuardian.Trigger.SCREENING_EXCLUSION), triggersOf(minor))
    }

    @Test
    fun `enforces the weekly cap on cue nights`() {
        val threeCueNights = listOf(cueNight(daysAgo = 2.0), cueNight(daysAgo = 3.0), cueNight(daysAgo = 4.0))

        val decision = evaluate(sessions = threeCueNights)

        assertEquals(listOf(SleepSafetyGuardian.Trigger.WEEKLY_CAP_REACHED), triggersOf(decision))
        assertEquals(NightMode.BEGINNER, decision.effectiveMode)
    }

    @Test
    fun `counts only completed cue nights inside the rolling week`() {
        val outsideWindowOrNotCounted = listOf(
            cueNight(daysAgo = 8.0),                                   // older than 7 days
            cueNight(daysAgo = 9.0),
            cueNight(daysAgo = 3.0, status = SessionStatus.CANCELLED), // never ran
            cueNight(daysAgo = 4.0, mode = NightMode.BEGINNER)         // delivered no cues
        )

        val decision = evaluate(sessions = outsideWindowOrNotCounted)

        assertTrue(decision is SleepSafetyGuardian.Decision.Allowed, "Got: $decision")
        assertEquals(0, (decision as SleepSafetyGuardian.Decision.Allowed).cueNightsUsedThisWeek)
    }

    @Test
    fun `requires a rest night immediately after a cue night`() {
        val lastNight = listOf(cueNight(daysAgo = 0.5))

        val decision = evaluate(sessions = lastNight)

        assertEquals(listOf(SleepSafetyGuardian.Trigger.REST_NIGHT_REQUIRED), triggersOf(decision))
    }

    @Test
    fun `stops the practice after consecutive nights of waking the user`() {
        val disruptiveNights = listOf(
            cueNight(daysAgo = 2.0, cueCount = 2, spikedCues = 2),
            cueNight(daysAgo = 3.0, cueCount = 2, spikedCues = 2)
        )

        val decision = evaluate(sessions = disruptiveNights)

        assertEquals(listOf(SleepSafetyGuardian.Trigger.WAKE_SPIKE_ESCALATION), triggersOf(decision))
    }

    @Test
    fun `tolerates a single disruptive night`() {
        val nights = listOf(
            cueNight(daysAgo = 2.0, cueCount = 2, spikedCues = 2),
            cueNight(daysAgo = 3.0, cueCount = 2, spikedCues = 0)
        )

        val decision = evaluate(sessions = nights)

        assertTrue(decision is SleepSafetyGuardian.Decision.Allowed, "Got: $decision")
    }

    @Test
    fun `stops the practice when subjective sleep quality declines`() {
        val decision = evaluate(
            sessions = listOf(cueNight(daysAgo = 3.0)),
            reports = listOf(report(1.0, quality = 2), report(2.0, quality = 2), report(3.0, quality = 1))
        )

        assertEquals(listOf(SleepSafetyGuardian.Trigger.SLEEP_QUALITY_DECLINE), triggersOf(decision))
    }

    @Test
    fun `does not judge sleep quality before enough nights are reported`() {
        val decision = evaluate(
            sessions = listOf(cueNight(daysAgo = 3.0)),
            reports = listOf(report(1.0, quality = 1), report(2.0, quality = 1))
        )

        assertTrue(decision is SleepSafetyGuardian.Decision.Allowed, "Got: $decision")
    }

    @Test
    fun `reports every triggered guardrail rather than only the first`() {
        val decision = evaluate(
            screening = clearedScreening.copy(narcolepsy = true),
            sessions = listOf(cueNight(daysAgo = 0.5), cueNight(daysAgo = 2.0), cueNight(daysAgo = 3.0))
        )

        val triggers = triggersOf(decision)
        assertTrue(triggers.contains(SleepSafetyGuardian.Trigger.SCREENING_EXCLUSION), "Got: $triggers")
        assertTrue(triggers.contains(SleepSafetyGuardian.Trigger.WEEKLY_CAP_REACHED), "Got: $triggers")
        assertTrue(triggers.contains(SleepSafetyGuardian.Trigger.REST_NIGHT_REQUIRED), "Got: $triggers")
    }

    @Test
    fun `advises when the current night is the last one allowed this week`() {
        val decision = evaluate(sessions = listOf(cueNight(daysAgo = 2.0), cueNight(daysAgo = 3.0)))

        assertTrue(decision is SleepSafetyGuardian.Decision.Allowed, "Got: $decision")
        val allowed = decision as SleepSafetyGuardian.Decision.Allowed
        assertEquals(2, allowed.cueNightsUsedThisWeek)
        assertTrue(allowed.advisories.isNotEmpty(), "Expected a weekly-limit advisory")
    }

    @Test
    fun `never withholds beginner mode which delivers no stimuli at all`() {
        val decision = evaluate(
            mode = NightMode.BEGINNER,
            screening = SafetyScreening(isComplete = true, ageYears = 15, parasomnia = true),
            sessions = listOf(cueNight(daysAgo = 0.5), cueNight(daysAgo = 2.0), cueNight(daysAgo = 3.0)),
            reports = listOf(report(1.0, quality = 1), report(2.0, quality = 1), report(3.0, quality = 1))
        )

        assertTrue(decision is SleepSafetyGuardian.Decision.Allowed, "Got: $decision")
        assertEquals(NightMode.BEGINNER, decision.effectiveMode)
    }

    @Test
    fun `rejects an incoherent policy instead of silently disabling a guardrail`() {
        assertThrows(IllegalArgumentException::class.java) {
            SleepSafetyGuardian.Policy(consecutiveDisruptiveNightsForStop = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SleepSafetyGuardian.Policy(disruptiveWakeSpikeRate = 1.5)
        }
    }

    private companion object {
        const val DAY_MS = 24.0 * 60 * 60 * 1000
        const val NOW = 1_700_000_000_000L
    }
}
