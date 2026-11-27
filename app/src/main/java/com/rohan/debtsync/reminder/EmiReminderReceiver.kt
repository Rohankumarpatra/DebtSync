package com.rohan.debtsync.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EmiReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            EmiReminderManager.postDueNotificationIfNeeded(context)
        }
        EmiReminderManager.scheduleDailyReminder(context)
    }
}
