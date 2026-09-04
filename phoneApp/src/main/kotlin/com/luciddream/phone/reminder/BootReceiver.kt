package com.luciddream.phone.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered on system boot or package update to restore
 * daytime Reality Check reminder alarms.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Restore default schedule
            AndroidRealityCheckScheduler.scheduleDailyReminders(context)
        }
    }
}
