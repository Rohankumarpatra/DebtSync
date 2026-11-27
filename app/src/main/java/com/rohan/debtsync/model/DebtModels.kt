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
    val MONTHS: List<MonthSlot> by lazy { buildMonthsFromKey(currentMonthKey(), count = 12) }

    fun initialLoans(): List<LoanAccount> = emptyList()

    fun currentMonthKey(): String {
        val cal = Calendar.getInstance()
        return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }

    fun offsetMonthKey(baseKey: String, offsetMonths: Int): String {
        val cal = parseMonthKey(baseKey) ?: Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        cal.add(Calendar.MONTH, offsetMonths)
        return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }

    fun buildMonthsFromKey(startKey: String, count: Int): List<MonthSlot> {
        val safeCount = count.coerceIn(1, 360)
        val cal = parseMonthKey(startKey) ?: Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFmt = SimpleDateFormat("MMM yyyy", Locale.US)
        val shortFmt = SimpleDateFormat("MMM", Locale.US)
        val curYear = Calendar.getInstance().get(Calendar.YEAR)

        return (0 until safeCount).map {
            val key = keyFmt.format(cal.time)
            val label = labelFmt.format(cal.time)
            val yr = cal.get(Calendar.YEAR)
            val short = if (yr != curYear) {
                "${shortFmt.format(cal.time)} '${(yr % 100).toString().padStart(2, '0')}"
            } else {
                shortFmt.format(cal.time)
            }
            cal.add(Calendar.MONTH, 1)
            MonthSlot(key = key, label = label, shortLabel = short)
        }
    }

    fun allMonthsFor(loans: List<LoanAccount>): List<MonthSlot> {
        val nowKey = currentMonthKey()
        val allKeys = loans.flatMap { loan ->
            loan.payments.filterValues { it.amount > 0 }.keys
        }
        if (allKeys.isEmpty()) {
            return buildMonthsFromKey(nowKey, 12)
        }
        val minKey = minOf(nowKey, allKeys.min())
        val maxKey = maxOf(offsetMonthKey(nowKey, 11), allKeys.max())
        val totalSpan = monthsBetweenInclusive(minKey, maxKey).coerceIn(1, 360)
        return buildMonthsFromKey(minKey, totalSpan)
    }

    fun slotFromKey(key: String): MonthSlot {
        val cal = parseMonthKey(key)
        if (cal != null) {
            val year = cal.get(Calendar.YEAR)
            val curYear = Calendar.getInstance().get(Calendar.YEAR)
            val labelFmt = SimpleDateFormat("MMM yyyy", Locale.US)
            val shortFmt = SimpleDateFormat("MMM", Locale.US)
            val shortStr = if (year != curYear) {
                "${shortFmt.format(cal.time)} '${(year % 100).toString().padStart(2, '0')}"
            } else {
                shortFmt.format(cal.time)
            }
            return MonthSlot(key, labelFmt.format(cal.time), shortStr)
        }
        return MonthSlot(key, key, key)
    }

    private fun parseMonthKey(key: String): Calendar? {
        val parts = key.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun monthsBetweenInclusive(startKey: String, endKey: String): Int {
        val start = parseMonthKey(startKey) ?: return 12
        val end = parseMonthKey(endKey) ?: return 12
        val yDiff = end.get(Calendar.YEAR) - start.get(Calendar.YEAR)
        val mDiff = end.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        return (yDiff * 12 + mDiff + 1).coerceAtLeast(1)
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
