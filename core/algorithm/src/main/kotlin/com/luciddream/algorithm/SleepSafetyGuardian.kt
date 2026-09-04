package com.luciddream.algorithm

import com.luciddream.model.MorningReport
import com.luciddream.model.NightMode
import com.luciddream.model.NightSession
import com.luciddream.model.SafetyScreening
import com.luciddream.model.SessionStatus

/**
 * Enforces sleep-fragmentation guardrails before a night session starts.
 *
 * The night algorithms ([RemConfidenceEngine], [NightCueDecisionEngine]) protect a single night:
 * they decide whether *this* window deserves a cue. Nothing in them limits exposure *across*
 * nights, yet the practice being automated here — WBTB and cued reactivation during REM — works
 * by deliberately interrupting sleep. Repeated nightly, that is simply sleep fragmentation.
 *
 * This class is the missing across-nights limiter. It never blocks the app: when a guardrail
 * fires it downgrades the night to [NightMode.BEGINNER], which still supports the dream journal,
 * recall drills and reality checks while [NightCueDecisionEngine] suppresses every nocturnal cue.
 *
 * All decisions are pure functions of the supplied history, so they are fully testable offline.
 */
class SleepSafetyGuardian(
    private val policy: Policy = Policy()
) {

    /**
     * Tunable exposure limits. Defaults are deliberately conservative: this is consumer wellness
     * software acting on sleeping users, so the cost of being too permissive is asymmetric.
     */
    data class Policy(
        /** Maximum nights with nocturnal cues within any rolling 7-day window. */
        val maxCueNightsPerWeek: Int = 3,

        /** Minimum undisturbed nights required between two cue nights. */
        val minRestNightsBetweenCueNights: Int = 1,

        /** Per-night wake-spike rate at or above which a night counts as disruptive. */
        val disruptiveWakeSpikeRate: Double = 0.50,

        /** Consecutive disruptive nights that force a recovery night. */
        val consecutiveDisruptiveNightsForStop: Int = 2,

        /** Number of recent reports averaged when checking subjective sleep quality. */
        val sleepQualityLookbackNights: Int = 3,

        /** Mean subjective sleep quality (1..5) at or below which a recovery night is forced. */
        val sleepQualityFloor: Double = 2.0
    ) {
        init {
            require(maxCueNightsPerWeek >= 0) { "maxCueNightsPerWeek must not be negative" }
            require(minRestNightsBetweenCueNights >= 0) { "minRestNightsBetweenCueNights must not be negative" }
            require(disruptiveWakeSpikeRate in 0.0..1.0) { "disruptiveWakeSpikeRate must be a rate in 0..1" }
            require(consecutiveDisruptiveNightsForStop >= 1) { "consecutiveDisruptiveNightsForStop must be >= 1" }
            require(sleepQualityLookbackNights >= 1) { "sleepQualityLookbackNights must be >= 1" }
        }
    }

    /** Why cues were withheld. Kept as a code so the UI can localise and the analytics can count. */
    enum class Trigger {
        SCREENING_EXCLUSION,
        WEEKLY_CAP_REACHED,
        REST_NIGHT_REQUIRED,
        WAKE_SPIKE_ESCALATION,
        SLEEP_QUALITY_DECLINE
    }

    data class Reason(val trigger: Trigger, val explanation: String)

    sealed class Decision {

        /** The requested cue mode may proceed. [advisories] are non-blocking notes for the UI. */
        data class Allowed(
            val mode: NightMode,
            val cueNightsUsedThisWeek: Int,
            val advisories: List<String> = emptyList()
        ) : Decision()

        /**
         * Cues are withheld tonight. The session may still run in [fallbackMode] for journaling
         * and recall; [NightCueDecisionEngine] independently suppresses cues in that mode.
         */
        data class RestNight(
            val requestedMode: NightMode,
            val fallbackMode: NightMode,
            val reasons: List<Reason>
        ) : Decision() {
            val explanations: List<String> get() = reasons.map { it.explanation }
        }
    }

    /**
     * Decides whether [requestedMode] may deliver nocturnal cues tonight.
     *
     * @param recentSessions past sessions in any order; only completed non-beginner ones count as exposure.
     * @param recentReports morning reports in any order.
     * @param nowMs start of the night being planned.
     */
    fun evaluateNight(
        requestedMode: NightMode,
        screening: SafetyScreening,
        recentSessions: List<NightSession>,
        recentReports: List<MorningReport>,
        nowMs: Long
    ): Decision {
        val cueNights = recentSessions
            .filter { it.status == SessionStatus.COMPLETED && it.mode != NightMode.BEGINNER }
            .sortedByDescending { it.startTimeMs }

        val cueNightsThisWeek = cueNights.count { nowMs - it.startTimeMs < WEEK_MS }

        // Beginner mode delivers no nocturnal stimuli, so no exposure limit can apply to it.
        if (requestedMode == NightMode.BEGINNER) {
            return Decision.Allowed(NightMode.BEGINNER, cueNightsThisWeek)
        }

        val reasons = buildList {
            screeningReason(screening)?.let { add(it) }
            weeklyCapReason(cueNightsThisWeek)?.let { add(it) }
            restGapReason(cueNights, nowMs)?.let { add(it) }
            wakeSpikeReason(cueNights)?.let { add(it) }
            sleepQualityReason(recentReports)?.let { add(it) }
        }

        if (reasons.isNotEmpty()) {
            return Decision.RestNight(
                requestedMode = requestedMode,
                fallbackMode = NightMode.BEGINNER,
                reasons = reasons
            )
        }

        val remaining = policy.maxCueNightsPerWeek - cueNightsThisWeek
        val advisories = if (remaining <= 1) {
            listOf("Это последняя ночь с сигналами на текущей неделе (лимит ${policy.maxCueNightsPerWeek}).")
        } else {
            emptyList()
        }

        return Decision.Allowed(requestedMode, cueNightsThisWeek, advisories)
    }

    private fun screeningReason(screening: SafetyScreening): Reason? {
        if (!screening.isComplete) {
            return Reason(
                Trigger.SCREENING_EXCLUSION,
                "Скрининг безопасности не пройден. Ночные сигналы выключены до его завершения."
            )
        }
        val exclusions = screening.exclusionReasons
        if (exclusions.isEmpty()) return null
        return Reason(
            Trigger.SCREENING_EXCLUSION,
            "Ночные сигналы недоступны по результатам скрининга: ${exclusions.joinToString(" ")}"
        )
    }

    private fun weeklyCapReason(cueNightsThisWeek: Int): Reason? {
        if (cueNightsThisWeek < policy.maxCueNightsPerWeek) return null
        return Reason(
            Trigger.WEEKLY_CAP_REACHED,
            "Достигнут недельный лимит ночей с сигналами " +
                "($cueNightsThisWeek из ${policy.maxCueNightsPerWeek}). Сегодня — ночь восстановления."
        )
    }

    private fun restGapReason(cueNights: List<NightSession>, nowMs: Long): Reason? {
        if (policy.minRestNightsBetweenCueNights <= 0) return null
        val lastCueNight = cueNights.firstOrNull() ?: return null
        val requiredGapMs = policy.minRestNightsBetweenCueNights * NIGHT_MS
        val elapsedMs = nowMs - lastCueNight.startTimeMs
        if (elapsedMs >= requiredGapMs) return null
        return Reason(
            Trigger.REST_NIGHT_REQUIRED,
            "Между ночами с сигналами нужен перерыв " +
                "${policy.minRestNightsBetweenCueNights} ноч(и). Прошло ${elapsedMs / NIGHT_MS}."
        )
    }

    /**
     * Counts disruptive nights backwards from the most recent night that actually delivered cues.
     * Nights without cues carry no evidence either way and are skipped rather than resetting the streak.
     */
    private fun wakeSpikeReason(cueNights: List<NightSession>): Reason? {
        var streak = 0
        for (session in cueNights) {
            val cues = session.cueEvents
            if (cues.isEmpty()) continue
            val spikeRate = cues.count { it.wakeSpikeAfter }.toDouble() / cues.size
            if (spikeRate >= policy.disruptiveWakeSpikeRate) streak++ else break
        }
        if (streak < policy.consecutiveDisruptiveNightsForStop) return null
        return Reason(
            Trigger.WAKE_SPIKE_ESCALATION,
            "Сигналы будили вас $streak ноч(и) подряд. Практика приостановлена до восстановления сна."
        )
    }

    private fun sleepQualityReason(recentReports: List<MorningReport>): Reason? {
        val window = recentReports
            .sortedByDescending { it.timestampMs }
            .take(policy.sleepQualityLookbackNights)

        if (window.size < policy.sleepQualityLookbackNights) return null

        val meanQuality = window.map { it.subjectiveSleepQuality }.average()
        if (meanQuality > policy.sleepQualityFloor) return null

        val rounded = kotlin.math.round(meanQuality * 10) / 10
        return Reason(
            Trigger.SLEEP_QUALITY_DECLINE,
            "Субъективное качество сна за последние ${window.size} ноч(и) — $rounded из 5. " +
                "Ночные сигналы приостановлены."
        )
    }

    private companion object {
        const val NIGHT_MS = 24L * 60 * 60 * 1000
        const val WEEK_MS = 7 * NIGHT_MS
    }
}

/** Mode that should actually run tonight for this decision. */
val SleepSafetyGuardian.Decision.effectiveMode: NightMode
    get() = when (this) {
        is SleepSafetyGuardian.Decision.Allowed -> mode
        is SleepSafetyGuardian.Decision.RestNight -> fallbackMode
    }
