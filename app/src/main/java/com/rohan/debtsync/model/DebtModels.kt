package com.rohan.debtsync.model

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class CurrencyMode(val symbol: String, val label: String) {
    USD("$", "$ USD"),
    INR("₹", "₹ INR")
}

enum class PaymentStatus {
    UPCOMING,
    PAID
}

enum class SimStrategy(val title: String, val subtitle: String) {
    SNOWBALL("Snowball", "Smallest balance first"),
    HIGHEST_EMI("Cashflow Relief", "Largest EMI first")
}

data class MonthSlot(
    val key: String,
    val label: String,
    val shortLabel: String
)

data class MonthlyEmi(
    val amount: Double,
    val status: PaymentStatus = PaymentStatus.UPCOMING
)

data class LoanAccount(
    val id: String,
    val platform: String,
    val dueDay: Int? = null,
    val payments: Map<String, MonthlyEmi> = emptyMap()
) {
    val amountLeft: Double
        get() = payments.values.filter { it.status == PaymentStatus.UPCOMING && it.amount > 0 }.sumOf { it.amount }

    val totalPaid: Double
        get() = payments.values.filter { it.status == PaymentStatus.PAID && it.amount > 0 }.sumOf { it.amount }

    val totalScheduled: Double
        get() = amountLeft + totalPaid

    val emisLeft: Int
        get() = payments.values.count { it.status == PaymentStatus.UPCOMING && it.amount > 0 }

    val emisPaid: Int
        get() = payments.values.count { it.status == PaymentStatus.PAID && it.amount > 0 }

    val totalEmis: Int
        get() = emisLeft + emisPaid

    val progressFraction: Float
        get() = if (totalScheduled > 0) (totalPaid / totalScheduled).toFloat().coerceIn(0f, 1f) else 0f

    val isSettled: Boolean
        get() = emisLeft == 0 && totalScheduled > 0

    val hasSchedule: Boolean
        get() = totalScheduled > 0
}

object DebtCatalog {
    val MONTHS: List<MonthSlot> by lazy { buildRollingMonths(count = 15) }

    fun initialLoans(): List<LoanAccount> = emptyList()

    fun allMonthsFor(loans: List<LoanAccount>): List<MonthSlot> {
        val known = MONTHS.associateBy { it.key }.toMutableMap()
        loans.forEach { loan ->
            loan.payments.keys.forEach { key ->
                if (key !in known) {
                    known[key] = slotFromKey(key)
                }
            }
        }
        return known.values.sortedBy { it.key }
    }

    fun slotFromKey(key: String): MonthSlot {
        val parts = key.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year != null && month != null && month in 1..12) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
            }
            val labelFmt = SimpleDateFormat("MMM yyyy", Locale.US)
            val shortFmt = SimpleDateFormat("MMM", Locale.US)
            val curYear = Calendar.getInstance().get(Calendar.YEAR)
            val shortStr = if (year != curYear) {
                "${shortFmt.format(cal.time)} '${(year % 100).toString().padStart(2, '0')}"
            } else {
                shortFmt.format(cal.time)
            }
            return MonthSlot(key, labelFmt.format(cal.time), shortStr)
        }
        return MonthSlot(key, key, key)
    }

    private fun buildRollingMonths(count: Int): List<MonthSlot> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFmt = SimpleDateFormat("MMM yyyy", Locale.US)
        val shortFmt = SimpleDateFormat("MMM", Locale.US)
        val startYear = cal.get(Calendar.YEAR)

        return (0 until count).map {
            val key = keyFmt.format(cal.time)
            val label = labelFmt.format(cal.time)
            val yr = cal.get(Calendar.YEAR)
            val short = if (yr != startYear) {
                "${shortFmt.format(cal.time)} '${(yr % 100).toString().padStart(2, '0')}"
            } else {
                shortFmt.format(cal.time)
            }
            cal.add(Calendar.MONTH, 1)
            MonthSlot(key = key, label = label, shortLabel = short)
        }
    }
}

fun formatMoney(amount: Double, currency: CurrencyMode, compact: Boolean = false): String {
    val locale = if (currency == CurrencyMode.INR) Locale("en", "IN") else Locale.US
    val nf = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = if (compact) 0 else 2
        maximumFractionDigits = if (compact) 0 else 2
    }
    return "${currency.symbol}${nf.format(amount)}"
}
