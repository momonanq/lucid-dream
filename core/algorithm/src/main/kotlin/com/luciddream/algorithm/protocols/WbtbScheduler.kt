package com.luciddream.algorithm.protocols

import kotlinx.serialization.Serializable

/**
 * Wake-Back-To-Bed (WBTB) Timing & Cognitive Rehearsal Scheduler.
 * Computes circadian REM peak wake windows and structured wakefulness stages.
 */
class WbtbScheduler {

    @Serializable
    data class WbtbSchedule(
        val bedtimeMs: Long,
        val alarmTimeMs: Long,
        val sleepDurationHours: Double,
        val wakefulnessDurationMinutes: Int,
        val recommendedTechnique: String
    )

    /**
     * Calculates the optimal WBTB alarm timestamp based on target bedtime and typical cycle length (~90 mins).
     * Standard protocol recommends waking after 3-4 full NREM/REM cycles (4.5 to 6.0 hours).
     */
    fun calculateOptimalAlarm(
        bedtimeMs: Long,
        preferredSleepHoursBeforeWake: Double = 5.0,
        wakefulnessDurationMinutes: Int = 25
    ): WbtbSchedule {
        val clampedSleepHours = preferredSleepHoursBeforeWake.coerceIn(4.0, 6.0)
        val alarmTimeMs = bedtimeMs + (clampedSleepHours * 3600 * 1000).toLong()

        return WbtbSchedule(
            bedtimeMs = bedtimeMs,
            alarmTimeMs = alarmTimeMs,
            sleepDurationHours = clampedSleepHours,
            wakefulnessDurationMinutes = wakefulnessDurationMinutes,
            recommendedTechnique = "MILD + TLR (Audio/Haptic cues active in remaining REM periods)"
        )
    }

    /**
     * Prescribed protocol guidelines during the 20-30 minute WBTB wake window.
     */
    fun getWakeGuidelines(): List<String> {
        return listOf(
            "Не включайте яркий верхний свет (используйте мягкий тёплый свет или ночник).",
            "Запишите сны первой половины ночи в журнал снов.",
            "Прочитайте 1-2 страницы о техниках осознанных сновидений или свои заметки.",
            "Сделайте 1-2 спокойных reality checks с искренним сомнением в реальности.",
            "Через 20–30 минут вернитесь в постель и выполните MILD / SSILD с установкой на осознание."
        )
    }
}
