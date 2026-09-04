package com.luciddream.phone.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.luciddream.algorithm.protocols.RealityCheckScheduler
import java.util.Calendar

/**
 * Schedules daytime Reality Check reminders via Android's AlarmManager,
 * ensuring prompts fire reliably throughout the day.
 */
object AndroidRealityCheckScheduler {

    private const val SNOOZE_REQUEST_CODE = 999
    private const val DAILY_BASE_REQUEST_CODE = 1000

    fun scheduleDailyReminders(
        context: Context,
        wakeMinuteOfDay: Int = 9 * 60,   // 09:00
        bedMinuteOfDay: Int = 22 * 60,   // 22:00
        count: Int = 8
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val schedule = RealityCheckScheduler().generateDailySchedule(
            dayStartMinutes = wakeMinuteOfDay,
            dayEndMinutes = bedMinuteOfDay,
            totalChecksCount = count
        )

        val nowCalendar = Calendar.getInstance()
        val currentMinuteOfDay = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)

        for ((index, minute) in schedule.withIndex()) {
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If scheduled time already passed today, schedule for tomorrow
                if (minute <= currentMinuteOfDay) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, RealityCheckReceiver::class.java).apply {
                action = RealityCheckReceiver.ACTION_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_BASE_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setExactAlarm(alarmManager, targetCal.timeInMillis, pendingIntent)
        }
    }

    fun scheduleSnooze(context: Context, snoozeMinutes: Int = 30) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, RealityCheckReceiver::class.java).apply {
            action = RealityCheckReceiver.ACTION_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(alarmManager, triggerAtMillis, pendingIntent)
    }

    fun cancelAllReminders(context: Context, count: Int = 8) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until count) {
            val intent = Intent(context, RealityCheckReceiver::class.java).apply {
                action = RealityCheckReceiver.ACTION_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_BASE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    /**
     * Schedules a reminder, preferring an exact alarm but never depending on one.
     *
     * From Android 12 the user can revoke SCHEDULE_EXACT_ALARM at any time, and the revocation
     * can land between the check and the call. A daytime reality-check prompt is not worth
     * crashing the app over, so both cases fall back to an inexact alarm: it still fires, just
     * not to the minute, which is fine for a reminder the user answers whenever they notice it.
     */
    private fun setExactAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val mayScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (mayScheduleExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Permission revoked after the check; fall through to the inexact path below.
            }
        }

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}
