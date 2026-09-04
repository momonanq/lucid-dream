package com.luciddream.phone.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.luciddream.algorithm.protocols.RealityCheckScheduler
import com.luciddream.phone.ui.MainActivity

/**
 * BroadcastReceiver responsible for posting Reality Check reminder notifications
 * and handling interactive button responses (Check completed vs Snooze).
 */
class RealityCheckReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM = "com.luciddream.phone.ACTION_REALITY_CHECK_ALARM"
        const val ACTION_COMPLETED = "com.luciddream.phone.ACTION_REALITY_CHECK_COMPLETED"
        const val ACTION_SNOOZE = "com.luciddream.phone.ACTION_REALITY_CHECK_SNOOZE"

        const val CHANNEL_ID = "lucid_reality_checks"
        const val EXTRA_PROMPT_ID = "extra_prompt_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val NOTIFICATION_BASE_ID = 2000
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent?.action) {
            ACTION_COMPLETED -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_BASE_ID)
                notificationManager.cancel(notifId)
            }

            ACTION_SNOOZE -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_BASE_ID)
                notificationManager.cancel(notifId)
                AndroidRealityCheckScheduler.scheduleSnooze(context, snoozeMinutes = 30)
            }

            ACTION_ALARM, null -> {
                createNotificationChannel(notificationManager)
                showRealityCheckNotification(context, notificationManager)
            }
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reality Checks",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daytime mindfulness reminders to induce lucid dreaming awareness"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showRealityCheckNotification(context: Context, manager: NotificationManager) {
        val prompts = RealityCheckScheduler().getDefaultPrompts()
        val prompt = prompts.random()
        val notifId = NOTIFICATION_BASE_ID + (prompt.id.hashCode() % 1000)

        // Main Tap Intent -> Opens App
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Check Completed
        val completeIntent = Intent(context, RealityCheckReceiver::class.java).apply {
            action = ACTION_COMPLETED
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            putExtra(EXTRA_PROMPT_ID, prompt.id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 30 min
        val snoozeIntent = Intent(context, RealityCheckReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reality Check: ${prompt.title}")
            .setContentText(prompt.instruction)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${prompt.instruction}\n\nТриггер: ${prompt.prospectiveTrigger ?: "Любой момент дня"}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "✅ Проверил", completePendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "💤 +30 мин", snoozePendingIntent)
            .build()

        manager.notify(notifId, notification)
    }
}
