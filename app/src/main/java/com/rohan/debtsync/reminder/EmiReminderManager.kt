package com.rohan.debtsync.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rohan.debtsync.MainActivity
import com.rohan.debtsync.R
import com.rohan.debtsync.data.DebtRepository
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.PaymentStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DueEmiReminder(
    val loanId: String,
    val platform: String,
    val monthKey: String,
    val amount: Double,
    val dueDateLabel: String,
    val daysUntilDue: Int,
    val urgencyLabel: String
)

object EmiReminderManager {
    private const val CHANNEL_ID = "debt_sync_emi_reminders"
    private const val ALARM_REQ_CODE = 4401
    private const val NOTIF_ID = 4402

    fun findUpcomingDueReminders(
        loans: List<LoanAccount>,
        leadDays: Int
    ): List<DueEmiReminder> {
        val todayNoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val results = mutableListOf<DueEmiReminder>()

        loans.forEach { loan ->
            val dueDay = loan.dueDay ?: return@forEach
            loan.payments.forEach { (monthKey, emi) ->
                if (emi.amount <= 0.0 || emi.status != PaymentStatus.UPCOMING) return@forEach
                val parts = monthKey.split("-")
                val year = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val month = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                if (month !in 1..12) return@forEach

                val dueCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val maxDay = dueCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                dueCal.set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, maxDay))

                val diffDays = ((dueCal.timeInMillis - todayNoon.timeInMillis) / 86_400_000L).toInt()
                if (diffDays <= leadDays) {
                    val urgency = when {
                        diffDays < 0 -> "Overdue by ${-diffDays}d"
                        diffDays == 0 -> "Due today!"
                        diffDays == 1 -> "Due tomorrow"
                        else -> "Due in $diffDays days"
                    }
                    results.add(
                        DueEmiReminder(
                            loanId = loan.id,
                            platform = loan.platform,
                            monthKey = monthKey,
                            amount = emi.amount,
                            dueDateLabel = dateFmt.format(dueCal.time),
                            daysUntilDue = diffDays,
                            urgencyLabel = urgency
                        )
                    )
                }
            }
        }
        return results.sortedWith(compareBy<DueEmiReminder> { it.daysUntilDue }.thenBy { it.platform })
    }

    fun scheduleDailyReminder(context: Context) {
        val repo = DebtRepository(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildAlarmIntent(context)

        if (!repo.isReminderEnabled()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val nextTrigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTrigger.timeInMillis,
            pendingIntent
        )
    }

    fun postDueNotificationIfNeeded(context: Context) {
        val repo = DebtRepository(context)
        if (!repo.isReminderEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val loans = repo.loadLoans()
        val leadDays = repo.getReminderLeadDays()
        val due = findUpcomingDueReminders(loans, leadDays)
        if (due.isEmpty()) return

        ensureChannel(context)

        val first = due.first()
        val title = if (due.size == 1) {
            "EMI Reminder: ${first.platform}"
        } else {
            "${due.size} Upcoming EMI Reminders"
        }
        val body = if (due.size == 1) {
            "${first.urgencyLabel} (${first.dueDateLabel}). Open Debt Sync to review or mark paid."
        } else {
            "Next: ${first.platform} (${first.urgencyLabel} on ${first.dueDateLabel}) and ${due.size - 1} more."
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            NOTIF_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, notification)
    }

    private fun buildAlarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, EmiReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "EMI Due Date Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts you before your scheduled loan EMI due dates."
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
