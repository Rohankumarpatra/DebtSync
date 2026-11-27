package com.rohan.debtsync.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.rohan.debtsync.data.DebtRepository
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.MonthlyEmi
import com.rohan.debtsync.reminder.EmiReminderManager

class EmiSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val repo = DebtRepository(context)
        if (!repo.isSmsAutoSyncEnabled()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Lender"
        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
        val ts = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        val currentLoans = repo.loadLoans().toMutableList()
        val detected = SmsEmiParser.parseSmsMessage(
            id = "live-$ts",
            sender = sender,
            body = fullBody,
            timestampMs = ts,
            existingLoans = currentLoans
        ) ?: return

        var updated = false
        if (detected.matchedLoanId != null) {
            val idx = currentLoans.indexOfFirst { it.id == detected.matchedLoanId }
            if (idx >= 0) {
                val loan = currentLoans[idx]
                val newMap = loan.payments.toMutableMap().apply {
                    put(detected.monthKey, MonthlyEmi(detected.amount, detected.status))
                }
                currentLoans[idx] = loan.copy(
                    dueDay = loan.dueDay ?: detected.dueDay,
                    payments = newMap
                )
                updated = true
            }
        } else {
            currentLoans.add(
                LoanAccount(
                    id = "sms-loan-$ts",
                    platform = detected.platform,
                    dueDay = detected.dueDay,
                    payments = mapOf(detected.monthKey to MonthlyEmi(detected.amount, detected.status))
                )
            )
            updated = true
        }

        if (updated) {
            repo.saveLoans(currentLoans)
            EmiReminderManager.scheduleDailyReminder(context)
        }
    }
}
