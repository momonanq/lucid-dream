package com.luciddream.algorithm.protocols

import com.luciddream.model.RealityCheckPrompt
import com.luciddream.model.RealityCheckType
import java.util.UUID

/**
 * Scheduler and generator for daytime Reality Checks and ADA (All-Day Awareness) drills.
 */
class RealityCheckScheduler {

    fun getDefaultPrompts(): List<RealityCheckPrompt> {
        return listOf(
            RealityCheckPrompt(
                id = "rc_finger_count",
                type = RealityCheckType.FINGER_COUNT,
                title = "Счёт пальцев и форма ладоней",
                instruction = "Посмотрите на свои руки. Внимательно пересчитайте пальцы на обеих ладонях (их точно 5 на каждой?). Проверьте структуру кожи и форму.",
                prospectiveTrigger = "Каждый раз при взгляде на свои руки или мытье рук"
            ),
            RealityCheckPrompt(
                id = "rc_text_reread",
                type = RealityCheckType.TEXT_RE_READ,
                title = "Повторное чтение текста",
                instruction = "Прочитайте любую строку текста на экране или бумаге. Отведите взгляд на секунду и перечитайте. Текст остался неизменным?",
                prospectiveTrigger = "При каждом получении сообщения или разблокировке смартфона"
            ),
            RealityCheckPrompt(
                id = "rc_breath_test",
                type = RealityCheckType.BREATH_TEST,
                title = "Дыхательный тест (Pinch nose)",
                instruction = "Зажмите нос пальцами и сомкните губы. Попробуйте осторожно вдохнуть через нос. (Во сне дыхание продолжится свободно).",
                prospectiveTrigger = "При смене помещения или выходе на улицу"
            ),
            RealityCheckPrompt(
                id = "rc_environment",
                type = RealityCheckType.PHYSICAL_ENVIRONMENT,
                title = "Память о недавнем пути",
                instruction = "Спросите себя: 'Как я оказался в этом месте?' Вспомните последние 10-15 минут своего пути. Есть ли пробелы в памяти?",
                prospectiveTrigger = "При прохождении через дверной проем"
            ),
            RealityCheckPrompt(
                id = "rc_time_check",
                type = RealityCheckType.TIME_CHECK,
                title = "Двойная проверка времени на часах",
                instruction = "Посмотрите на экран Galaxy Watch, запомните время. Отвернитесь на секунду и взгляните снова. Цифры ведут себя стабильно?",
                prospectiveTrigger = "Каждый раз при проверке времени на Galaxy Watch"
            )
        )
    }

    /**
     * Generates randomized notification timestamps throughout the active day (e.g. 09:00 - 22:00).
     */
    fun generateDailySchedule(
        dayStartMinutes: Int = 9 * 60, // 09:00
        dayEndMinutes: Int = 22 * 60,   // 22:00
        totalChecksCount: Int = 8
    ): List<Int> {
        val totalMinutes = (dayEndMinutes - dayStartMinutes).coerceAtLeast(60)
        val interval = totalMinutes / totalChecksCount
        val schedule = mutableListOf<Int>()

        for (i in 0 until totalChecksCount) {
            val base = dayStartMinutes + (i * interval)
            // Add slight pseudo-random jitter (+/- 10 mins)
            val jitter = ((i * 7 + 3) % 15) - 7
            val time = (base + jitter).coerceIn(dayStartMinutes, dayEndMinutes)
            schedule.add(time)
        }

        return schedule.sorted()
    }
}
