package com.rohan.debtsync.sms

import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.PaymentStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class DetectedSmsEmi(
    val id: String,
    val sender: String,
    val platform: String,
    val matchedLoanId: String?,
    val monthKey: String,
    val amount: Double,
    val status: PaymentStatus,
    val dueDay: Int?,
    val snippet: String,
    val timestampMs: Long
)

object SmsEmiParser {
    private val KNOWN_LENDERS = listOf(
        "Moneyview" to listOf("moneyview", "money view", "mnyvw"),
        "mPokket" to listOf("mpokket", "mpockket", "mpocket"),
        "Snapmint" to listOf("snapmint"),
        "Stu-cred" to listOf("stucred", "stu-cred", "stu cred"),
        "KreditBee" to listOf("kreditbee", "kredit bee"),
        "Navi" to listOf("navi", "navi finserv"),
        "Bajaj Finserv" to listOf("bajaj finserv", "bajaj finance", "bfl"),
        "Fibe" to listOf("fibe", "earlysalary"),
        "LazyPay" to listOf("lazypay", "lazy pay"),
        "Slice" to listOf("slice"),
        "CASHe" to listOf("cashe"),
        "Stashfin" to listOf("stashfin"),
        "Kissht" to listOf("kissht"),
        "IDFC First" to listOf("idfc"),
        "HDFC Loan" to listOf("hdfc"),
        "ICICI Loan" to listOf("icici"),
        "SBI Loan" to listOf("sbi"),
        "Axis Loan" to listOf("axis bank", "axis"),
        "Kotak Loan" to listOf("kotak")
    )

    private val EMI_CONTEXT_REGEX = Regex(
        """\b(emi|installment|instalment|loan\s*(repayment|account|due|emi)?|nach|ecs|auto[-\s]?debit|autopay)\b""",
        RegexOption.IGNORE_CASE
    )
    private val PAID_REGEX = Regex(
        """\b(debited|paid|received|successful|deducted|cleared|settled|thank\s+you\s+for\s+paying|payment\s+of)\b""",
        RegexOption.IGNORE_CASE
    )
    private val DUE_REGEX = Regex(
        """\b(due|upcoming|reminder|overdue|scheduled|will\s+be\s+debited|keep\s+sufficient|pay\s+now|pay\s+before)\b""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?:Rs\.?|INR|₹|\$)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        Pattern.CASE_INSENSITIVE
    )
    private val DATE_TEXT_PATTERN = Pattern.compile(
        """\b(\d{1,2})[\s\-/](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*(?:[\s\-/](\d{2,4}))?\b""",
        Pattern.CASE_INSENSITIVE
    )
    private val DATE_NUM_PATTERN = Pattern.compile(
        """\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b"""
    )

    private val MONTH_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    fun parseBulkText(
        rawText: String,
        existingLoans: List<LoanAccount>
    ): List<DetectedSmsEmi> {
        val blocks = rawText
            .split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val now = System.currentTimeMillis()
        val results = mutableListOf<DetectedSmsEmi>()
        val seen = mutableSetOf<String>()

        blocks.forEachIndexed { idx, block ->
            val parsed = parseSmsMessage(
                id = "import-$now-$idx",
                sender = "SMS Import",
                body = block,
                timestampMs = now,
                existingLoans = existingLoans
            ) ?: return@forEachIndexed

            val key = "${parsed.platform.lowercase()}|${parsed.monthKey}|${parsed.amount}|${parsed.status}"
            if (seen.add(key)) {
                results.add(parsed)
            }
        }
        return results
    }

    fun parseSmsMessage(
        id: String,
        sender: String,
        body: String,
        timestampMs: Long,
        existingLoans: List<LoanAccount>
    ): DetectedSmsEmi? {
        if (!EMI_CONTEXT_REGEX.containsMatchIn(body)) return null

        val isPaid = PAID_REGEX.containsMatchIn(body)
        val isDue = DUE_REGEX.containsMatchIn(body)
        if (!isPaid && !isDue) return null

        val amount = extractAmount(body) ?: return null
        if (amount <= 0.0) return null

        val status = if (isPaid) PaymentStatus.PAID else PaymentStatus.UPCOMING
        val matchedLoan = findMatchingLoan(sender, body, existingLoans)
        val platform = matchedLoan?.platform ?: inferPlatformName(sender, body)
        val (monthKey, dueDay) = extractTargetMonthAndDay(body, timestampMs)
        val snippet = body.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)

        return DetectedSmsEmi(
            id = id,
            sender = sender,
            platform = platform,
            matchedLoanId = matchedLoan?.id,
            monthKey = monthKey,
            amount = amount,
            status = status,
            dueDay = dueDay,
            snippet = snippet,
            timestampMs = timestampMs
        )
    }

    private fun extractAmount(body: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(body)
        while (matcher.find()) {
            val raw = matcher.group(1)?.replace(",", "") ?: continue
            val value = raw.toDoubleOrNull()
            if (value != null && value >= 10.0) return value
        }
        return null
    }

    private fun findMatchingLoan(
        sender: String,
        body: String,
        existingLoans: List<LoanAccount>
    ): LoanAccount? {
        val hay = "$sender $body".lowercase(Locale.US)
        return existingLoans.firstOrNull { loan ->
            val p = loan.platform.trim().lowercase(Locale.US)
            p.length >= 3 && (hay.contains(p) || hay.contains(p.replace("-", "").replace(" ", "")))
        }
    }

    private fun inferPlatformName(sender: String, body: String): String {
        val hay = "$sender $body".lowercase(Locale.US)
        KNOWN_LENDERS.forEach { (display, keywords) ->
            if (keywords.any { hay.contains(it) }) return display
        }
        return "Loan EMI"
    }

    private fun extractTargetMonthAndDay(body: String, fallbackTs: Long): Pair<String, Int?> {
        val fallbackCal = Calendar.getInstance().apply { timeInMillis = fallbackTs }
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)

        val mText = DATE_TEXT_PATTERN.matcher(body)
        if (mText.find()) {
            val day = mText.group(1)?.toIntOrNull()?.takeIf { it in 1..31 }
            val monthAbbr = mText.group(2)?.take(3)?.lowercase(Locale.US)
            val monthNum = MONTH_MAP[monthAbbr]
            val yrRaw = mText.group(3)?.toIntOrNull()
            if (day != null && monthNum != null) {
                val yr = when {
                    yrRaw == null -> fallbackCal.get(Calendar.YEAR)
                    yrRaw < 100 -> 2000 + yrRaw
                    else -> yrRaw
                }
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, yr)
                    set(Calendar.MONTH, monthNum - 1)
                }
                return keyFmt.format(cal.time) to day
            }
        }

        val mNum = DATE_NUM_PATTERN.matcher(body)
        if (mNum.find()) {
            val day = mNum.group(1)?.toIntOrNull()?.takeIf { it in 1..31 }
            val monthNum = mNum.group(2)?.toIntOrNull()?.takeIf { it in 1..12 }
            val yrRaw = mNum.group(3)?.toIntOrNull()
            if (day != null && monthNum != null && yrRaw != null) {
                val yr = if (yrRaw < 100) 2000 + yrRaw else yrRaw
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, yr)
                    set(Calendar.MONTH, monthNum - 1)
                }
                return keyFmt.format(cal.time) to day
            }
        }

        return keyFmt.format(fallbackCal.time) to null
    }
}
