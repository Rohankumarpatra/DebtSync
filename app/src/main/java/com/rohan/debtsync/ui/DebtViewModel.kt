package com.rohan.debtsync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.debtsync.BuildConfig
import com.rohan.debtsync.data.DebtRepository
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.DebtCatalog
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.MonthlyEmi
import com.rohan.debtsync.model.PaymentStatus
import com.rohan.debtsync.model.SimStrategy
import com.rohan.debtsync.reminder.DueEmiReminder
import com.rohan.debtsync.reminder.EmiReminderManager
import com.rohan.debtsync.security.TotpManager
import com.rohan.debtsync.update.AppUpdateInfo
import com.rohan.debtsync.update.UpdateChecker
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

data class SimulatedAccountResult(
    val platform: String,
    val originalLeft: Double,
    val emisLeft: Int,
    val payoffMonthLabel: String,
    val payoffMonthIndex: Int
)

data class SimulationSummary(
    val baselineLabel: String,
    val baselineMonths: Int,
    val acceleratedLabel: String,
    val acceleratedMonths: Int,
    val monthsSaved: Int,
    val order: List<SimulatedAccountResult>
)

class DebtViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = DebtRepository(application.applicationContext)

    var loans by mutableStateOf(repo.loadLoans())
        private set

    var currency by mutableStateOf(repo.loadCurrency())
        private set

    var darkThemeOverride by mutableStateOf(repo.loadDarkTheme())
        private set

    var isAuthConfigured by mutableStateOf(repo.isAuthConfigured())
        private set

    var isUnlocked by mutableStateOf(false)
        private set

    var remindersEnabled by mutableStateOf(repo.isReminderEnabled())
        private set

    var reminderLeadDays by mutableIntStateOf(repo.getReminderLeadDays())
        private set

    var dueReminders by mutableStateOf<List<DueEmiReminder>>(emptyList())
        private set

    var showReminderPopup by mutableStateOf(false)
        private set

    var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
        private set

    private var hasAutoCheckedUpdate = false

    var focusMonthKey by mutableStateOf(findInitialFocusMonth(loans))
    var simExtraBudget by mutableDoubleStateOf(2000.0)
    var simStrategy by mutableStateOf(SimStrategy.SNOWBALL)
    var statusMessage by mutableStateOf<String?>(null)

    val totalAmountLeft: Double
        get() = loans.sumOf { it.amountLeft }

    val totalPaid: Double
        get() = loans.sumOf { it.totalPaid }

    val totalScheduled: Double
        get() = totalAmountLeft + totalPaid

    val totalEmisLeft: Int
        get() = loans.sumOf { it.emisLeft }

    val totalEmisPaid: Int
        get() = loans.sumOf { it.emisPaid }

    val settledAccountsCount: Int
        get() = loans.count { it.isSettled }

    val progressFraction: Float
        get() = if (totalScheduled > 0) (totalPaid / totalScheduled).toFloat().coerceIn(0f, 1f) else 0f

    val debtFreeTargetLabel: String
        get() {
            val allMonths = DebtCatalog.allMonthsFor(loans)
            val lastIdx = allMonths.indexOfLast { m ->
                loans.any { l ->
                    val p = l.payments[m.key]
                    p != null && p.amount > 0 && p.status == PaymentStatus.UPCOMING
                }
            }
            return if (lastIdx >= 0) allMonths[lastIdx].label else "Debt Free!"
        }

    fun completeAuthenticatorSetup(secretBase32: String, recoveryCode: String, code: String): Boolean {
        if (!TotpManager.verifyTotp(secretBase32, code)) return false
        repo.saveAuthConfig(secretBase32, recoveryCode)
        isAuthConfigured = true
        onSuccessfulUnlock()
        statusMessage = "Authenticator lock enabled"
        return true
    }

    fun unlockWithTotp(code: String): Boolean {
        val secret = repo.loadTotpSecret() ?: return false
        if (!TotpManager.verifyTotp(secret, code)) return false
        onSuccessfulUnlock()
        return true
    }

    fun unlockWithRecoveryCode(code: String): Boolean {
        if (!repo.verifyRecoveryCode(code)) return false
        onSuccessfulUnlock()
        statusMessage = "Unlocked with recovery code"
        return true
    }

    fun beginAuthResync() {
        isAuthConfigured = false
        isUnlocked = false
        showReminderPopup = false
    }

    fun lockApp() {
        isUnlocked = false
        showReminderPopup = false
    }

    private fun onSuccessfulUnlock() {
        isUnlocked = true
        refreshReminders(openPopupIfDue = true)
        EmiReminderManager.scheduleDailyReminder(getApplication())
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean = false) {
        if (!manual && hasAutoCheckedUpdate) return
        hasAutoCheckedUpdate = true
        viewModelScope.launch {
            UpdateChecker.checkForUpdate()
                .onSuccess { info ->
                    if (info != null) {
                        availableUpdate = info
                    } else if (manual) {
                        statusMessage = "You're on the latest version (v${BuildConfig.VERSION_NAME})"
                    }
                }
                .onFailure {
                    if (manual) {
                        statusMessage = "Couldn't check for updates right now"
                    }
                }
        }
    }

    fun dismissUpdateDialog() {
        availableUpdate = null
    }

    fun refreshReminders(openPopupIfDue: Boolean = false) {
        if (!remindersEnabled) {
            dueReminders = emptyList()
            showReminderPopup = false
            return
        }
        val list = EmiReminderManager.findUpcomingDueReminders(loans, reminderLeadDays)
        dueReminders = list
        if (openPopupIfDue && list.isNotEmpty()) {
            showReminderPopup = true
        } else if (list.isEmpty()) {
            showReminderPopup = false
        }
    }

    fun dismissReminderPopup() {
        showReminderPopup = false
    }

    fun openReminderPopup() {
        refreshReminders(openPopupIfDue = true)
        if (dueReminders.isEmpty()) {
            statusMessage = "No upcoming EMIs due within $reminderLeadDays day(s)"
        }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        remindersEnabled = enabled
        repo.setReminderEnabled(enabled)
        EmiReminderManager.scheduleDailyReminder(getApplication())
        refreshReminders(openPopupIfDue = false)
    }

    fun updateReminderLeadDays(days: Int) {
        reminderLeadDays = days.coerceIn(1, 14)
        repo.setReminderLeadDays(reminderLeadDays)
        EmiReminderManager.scheduleDailyReminder(getApplication())
        refreshReminders(openPopupIfDue = false)
    }

    fun toggleCurrency() {
        currency = if (currency == CurrencyMode.USD) CurrencyMode.INR else CurrencyMode.USD
        repo.saveCurrency(currency)
    }

    fun toggleTheme(currentlyDark: Boolean) {
        val next = !currentlyDark
        darkThemeOverride = next
        repo.saveDarkTheme(next)
    }

    fun togglePayment(loanId: String, monthKey: String) {
        var toastMsg: String? = null
        val allMonths = DebtCatalog.allMonthsFor(loans)
        loans = loans.map { loan ->
            if (loan.id != loanId) {
                loan
            } else {
                val existing = loan.payments[monthKey] ?: return@map loan
                val nextStatus = if (existing.status == PaymentStatus.PAID) PaymentStatus.UPCOMING else PaymentStatus.PAID
                val updatedMap = loan.payments.toMutableMap().apply {
                    put(monthKey, existing.copy(status = nextStatus))
                }
                val updated = loan.copy(payments = updatedMap)
                val shortMonth = allMonths.find { it.key == monthKey }?.shortLabel ?: monthKey
                toastMsg = if (!loan.isSettled && updated.isSettled) {
                    "${loan.platform} is 100% settled!"
                } else {
                    "${loan.platform} ($shortMonth) marked ${if (nextStatus == PaymentStatus.PAID) "Paid" else "Upcoming"}"
                }
                updated
            }
        }
        repo.saveLoans(loans)
        refreshReminders(openPopupIfDue = false)
        EmiReminderManager.scheduleDailyReminder(getApplication())
        statusMessage = toastMsg
    }

    fun markFocusMonthPaid() {
        var count = 0
        loans = loans.map { loan ->
            val emi = loan.payments[focusMonthKey]
            if (emi != null && emi.amount > 0 && emi.status == PaymentStatus.UPCOMING) {
                count++
                val updated = loan.payments.toMutableMap().apply {
                    put(focusMonthKey, emi.copy(status = PaymentStatus.PAID))
                }
                loan.copy(payments = updated)
            } else {
                loan
            }
        }
        if (count > 0) {
            repo.saveLoans(loans)
            refreshReminders(openPopupIfDue = false)
            EmiReminderManager.scheduleDailyReminder(getApplication())
            val mLabel = DebtCatalog.allMonthsFor(loans).find { it.key == focusMonthKey }?.label ?: focusMonthKey
            statusMessage = "Marked $count EMIs paid for $mLabel"
        } else {
            statusMessage = "All EMIs for this month are already paid"
        }
    }

    fun saveLoan(existingId: String?, platform: String, dueDay: Int?, amounts: Map<String, Double>) {
        val cleanName = platform.trim()
        if (cleanName.isEmpty()) return

        if (existingId != null) {
            loans = loans.map { loan ->
                if (loan.id != existingId) loan
                else {
                    val newPayments = amounts.filterValues { it > 0 }.mapValues { (k, amt) ->
                        val prevStatus = loan.payments[k]?.status ?: PaymentStatus.UPCOMING
                        MonthlyEmi(amt, prevStatus)
                    }
                    loan.copy(platform = cleanName, dueDay = dueDay, payments = newPayments)
                }
            }
            statusMessage = "Updated $cleanName"
        } else {
            val newPayments = amounts.filterValues { it > 0 }.mapValues { (_, amt) ->
                MonthlyEmi(amt, PaymentStatus.UPCOMING)
            }
            loans = loans + LoanAccount(
                id = "loan-${System.currentTimeMillis()}",
                platform = cleanName,
                dueDay = dueDay,
                payments = newPayments
            )
            focusMonthKey = findInitialFocusMonth(loans)
            statusMessage = "Added $cleanName"
        }
        repo.saveLoans(loans)
        refreshReminders(openPopupIfDue = true)
        EmiReminderManager.scheduleDailyReminder(getApplication())
    }

    fun deleteLoan(loanId: String) {
        val target = loans.find { it.id == loanId } ?: return
        loans = loans.filterNot { it.id == loanId }
        repo.saveLoans(loans)
        refreshReminders(openPopupIfDue = false)
        EmiReminderManager.scheduleDailyReminder(getApplication())
        statusMessage = "Deleted ${target.platform}"
    }

    fun clearAllLoans() {
        loans = emptyList()
        repo.saveLoans(loans)
        refreshReminders(openPopupIfDue = false)
        EmiReminderManager.scheduleDailyReminder(getApplication())
        statusMessage = "Cleared all loan accounts"
    }

    fun buildExportCsv(): String = repo.exportCsv(loans)

    fun clearStatusMessage() {
        statusMessage = null
    }

    fun runSimulation(): SimulationSummary {
        val pending = loans.filter { it.amountLeft > 0 }
        if (pending.isEmpty()) {
            return SimulationSummary("Debt Free!", 0, "Debt Free!", 0, 0, emptyList())
        }

        val allMonths = DebtCatalog.allMonthsFor(loans)
        val firstUpcomingIdx = allMonths.indexOfFirst { m ->
            pending.any { l ->
                val p = l.payments[m.key]
                p != null && p.amount > 0 && p.status == PaymentStatus.UPCOMING
            }
        }.coerceAtLeast(0)

        val lastUpcomingIdx = allMonths.indexOfLast { m ->
            pending.any { l ->
                val p = l.payments[m.key]
                p != null && p.amount > 0 && p.status == PaymentStatus.UPCOMING
            }
        }.coerceAtLeast(firstUpcomingIdx)

        val baselineMonths = (lastUpcomingIdx - firstUpcomingIdx) + 1
        val baselineLabel = allMonths[lastUpcomingIdx].label

        class WorkingAccount(
            val platform: String,
            val originalLeft: Double,
            val emisLeft: Int,
            val nextEmi: Double,
            val payments: Map<String, MonthlyEmi>,
            var balance: Double = originalLeft,
            var paidOffIdx: Int = -1
        )

        val working = pending.map { loan ->
            val nextEmi = allMonths.firstNotNullOfOrNull { m ->
                val p = loan.payments[m.key]
                if (p != null && p.amount > 0 && p.status == PaymentStatus.UPCOMING) p.amount else null
            } ?: 0.0
            WorkingAccount(loan.platform, loan.amountLeft, loan.emisLeft, nextEmi, loan.payments)
        }

        var monthIdx = firstUpcomingIdx
        val maxEndIdx = firstUpcomingIdx + 36
        while (working.any { it.balance > 0.01 } && monthIdx < maxEndIdx) {
            val mKey = allMonths.getOrNull(monthIdx)?.key
            working.forEach { acc ->
                if (acc.balance > 0.01) {
                    val sched = if (mKey != null) {
                        val p = acc.payments[mKey]
                        if (p != null && p.status == PaymentStatus.UPCOMING) p.amount else 0.0
                    } else 0.0
                    val pay = min(acc.balance, sched)
                    acc.balance -= pay
                    if (acc.balance <= 0.01) {
                        acc.balance = 0.0
                        if (acc.paidOffIdx == -1) acc.paidOffIdx = monthIdx
                    }
                }
            }

            var extraPool = simExtraBudget
            while (extraPool > 0.01) {
                val targets = working.filter { it.balance > 0.01 }
                if (targets.isEmpty()) break
                val target = if (simStrategy == SimStrategy.SNOWBALL) {
                    targets.minBy { it.balance }
                } else {
                    targets.maxBy { it.nextEmi }
                }
                val apply = min(target.balance, extraPool)
                target.balance -= apply
                extraPool -= apply
                if (target.balance <= 0.01) {
                    target.balance = 0.0
                    if (target.paidOffIdx == -1) target.paidOffIdx = monthIdx
                }
            }
            monthIdx++
        }

        val lastPaidIdx = working.maxOfOrNull { if (it.paidOffIdx >= 0) it.paidOffIdx else monthIdx - 1 } ?: firstUpcomingIdx
        val accelMonths = max(1, (lastPaidIdx - firstUpcomingIdx) + 1)
        val accelLabel = allMonths.getOrNull(lastPaidIdx)?.label ?: "Month ${lastPaidIdx + 1}"
        val saved = max(0, baselineMonths - accelMonths)

        val order = working
            .sortedWith(
                compareBy<WorkingAccount> { if (it.paidOffIdx >= 0) it.paidOffIdx else Int.MAX_VALUE }
                    .thenBy { it.originalLeft }
            )
            .map { acc ->
                val lbl = when {
                    acc.paidOffIdx < 0 -> "36+ mo"
                    else -> allMonths.getOrNull(acc.paidOffIdx)?.label ?: "Month ${acc.paidOffIdx + 1}"
                }
                SimulatedAccountResult(acc.platform, acc.originalLeft, acc.emisLeft, lbl, acc.paidOffIdx)
            }

        return SimulationSummary(baselineLabel, baselineMonths, accelLabel, accelMonths, saved, order)
    }

    private fun findInitialFocusMonth(loanList: List<LoanAccount>): String {
        val allMonths = DebtCatalog.allMonthsFor(loanList)
        val firstUpcoming = allMonths.firstOrNull { m ->
            loanList.any { l ->
                val p = l.payments[m.key]
                p != null && p.amount > 0 && p.status == PaymentStatus.UPCOMING
            }
        }
        return firstUpcoming?.key ?: DebtCatalog.MONTHS.first().key
    }
}
