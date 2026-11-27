package com.rohan.debtsync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.DebtCatalog
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.formatMoney
import com.rohan.debtsync.ui.theme.EmiGoDialog
import com.rohan.debtsync.ui.theme.LocalDebtPalette

@Composable
fun AddLoanDialog(
    existingLoan: LoanAccount?,
    currency: CurrencyMode,
    onDismiss: () -> Unit,
    onSave: (platform: String, dueDay: Int?, amounts: Map<String, Double>) -> Unit
) {
    val palette = LocalDebtPalette.current

    val initialStartKey = remember(existingLoan?.id) {
        existingLoan?.payments?.filterValues { it.amount > 0 }?.keys?.minOrNull()
            ?: DebtCatalog.currentMonthKey()
    }
    val initialMonthsCount = remember(existingLoan?.id) {
        val activeKeys = existingLoan?.payments?.filterValues { it.amount > 0 }?.keys?.sorted()
        if (activeKeys.isNullOrEmpty()) {
            6
        } else {
            val allSpan = DebtCatalog.allMonthsFor(listOfNotNull(existingLoan))
            val firstIdx = allSpan.indexOfFirst { it.key == activeKeys.first() }.coerceAtLeast(0)
            val lastIdx = allSpan.indexOfLast { it.key == activeKeys.last() }.coerceAtLeast(firstIdx)
            (lastIdx - firstIdx + 1).coerceAtLeast(1)
        }
    }
    val initialEmi = remember(existingLoan?.id) {
        val amounts = existingLoan?.payments?.values?.filter { it.amount > 0 }?.map { it.amount }.orEmpty()
        if (amounts.isNotEmpty() && amounts.distinct().size == 1) formatInputAmount(amounts.first()) else ""
    }
    val hasCustomVariableSchedule = remember(existingLoan?.id) {
        val amounts = existingLoan?.payments?.values?.filter { it.amount > 0 }?.map { it.amount }.orEmpty()
        amounts.distinct().size > 1
    }

    var name by remember(existingLoan?.id) { mutableStateOf(existingLoan?.platform ?: "") }
    var dueDayText by remember(existingLoan?.id) { mutableStateOf(existingLoan?.dueDay?.toString() ?: "") }
    var startKey by remember(existingLoan?.id) { mutableStateOf(initialStartKey) }
    var monthsCount by remember(existingLoan?.id) { mutableIntStateOf(initialMonthsCount) }
    var monthsText by remember(existingLoan?.id) { mutableStateOf(initialMonthsCount.toString()) }
    var emiAmountText by remember(existingLoan?.id) { mutableStateOf(initialEmi) }
    var showCustomMonths by remember(existingLoan?.id) { mutableStateOf(hasCustomVariableSchedule) }

    val monthTexts = remember(existingLoan?.id) {
        mutableStateMapOf<String, String>().apply {
            existingLoan?.payments?.forEach { (k, emi) ->
                if (emi.amount > 0) put(k, formatInputAmount(emi.amount))
            }
        }
    }

    val months = remember(startKey, monthsCount) {
        DebtCatalog.buildMonthsFromKey(startKey, monthsCount)
    }

    fun applyEqualEmis(amtStr: String, targetMonths: List<com.rohan.debtsync.model.MonthSlot>) {
        val amt = amtStr.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return
        val formatted = formatInputAmount(amt)
        monthTexts.clear()
        targetMonths.forEach { m -> monthTexts[m.key] = formatted }
    }

    val activeTotal = months.sumOf { (monthTexts[it.key]?.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) }
    val activeEmiCount = months.count { (monthTexts[it.key]?.toDoubleOrNull() ?: 0.0) > 0.0 }

    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingLoan == null) "Add Loan Account" else "Edit ${existingLoan.platform}",
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val parsedAmounts = months.associate { m ->
                        m.key to (monthTexts[m.key]?.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    }
                    val dueDay = dueDayText.toIntOrNull()?.coerceIn(1, 31)
                    onSave(name, dueDay, parsedAmounts)
                },
                enabled = name.isNotBlank() && activeEmiCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Save")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Loan / Platform Name") },
                placeholder = { Text("e.g. Home Loan, Bike EMI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dueDayText,
                onValueChange = {
                    val digits = it.filter { ch -> ch.isDigit() }.take(2)
                    val num = digits.toIntOrNull()
                    dueDayText = if (num != null && num > 31) "31" else digits
                },
                label = { Text("Due Day of Month (1-31, for EMI reminder)") },
                placeholder = { Text("e.g. 5") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Start Month Stepper
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.subtleSurface)
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Start Month", fontSize = 11.sp, color = palette.textSecondary)
                    Text(
                        DebtCatalog.slotFromKey(startKey).label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val nextStart = DebtCatalog.offsetMonthKey(startKey, -1)
                        startKey = nextStart
                        val newMonths = DebtCatalog.buildMonthsFromKey(nextStart, monthsCount)
                        if (emiAmountText.toDoubleOrNull()?.let { it > 0 } == true && !showCustomMonths) {
                            applyEqualEmis(emiAmountText, newMonths)
                        }
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = palette.textPrimary)
                    }
                    IconButton(onClick = {
                        val nextStart = DebtCatalog.offsetMonthKey(startKey, 1)
                        startKey = nextStart
                        val newMonths = DebtCatalog.buildMonthsFromKey(nextStart, monthsCount)
                        if (emiAmountText.toDoubleOrNull()?.let { it > 0 } == true && !showCustomMonths) {
                            applyEqualEmis(emiAmountText, newMonths)
                        }
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = palette.textPrimary)
                    }
                }
            }

            // EMI Amount + Unlimited Months Count
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = emiAmountText,
                    onValueChange = { raw ->
                        val cleaned = sanitizeDecimalInput(raw)
                        emiAmountText = cleaned
                        if ((cleaned.toDoubleOrNull() ?: 0.0) > 0.0) {
                            applyEqualEmis(cleaned, months)
                        }
                    },
                    label = { Text("Monthly EMI") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.1f)
                )
                OutlinedTextField(
                    value = monthsText,
                    onValueChange = { raw ->
                        val digits = raw.filter { ch -> ch.isDigit() }.take(3)
                        monthsText = digits
                        val parsed = digits.toIntOrNull()?.coerceIn(1, 360)
                        if (parsed != null) {
                            monthsCount = parsed
                            val newMonths = DebtCatalog.buildMonthsFromKey(startKey, parsed)
                            if ((emiAmountText.toDoubleOrNull() ?: 0.0) > 0.0 && !showCustomMonths) {
                                applyEqualEmis(emiAmountText, newMonths)
                            }
                        }
                    },
                    label = { Text("Months") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.75f)
                )
            }

            // Quick tenure presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(3, 6, 12, 24, 36).forEach { preset ->
                    val selected = monthsCount == preset
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentSoftBg else palette.subtleSurface)
                            .border(
                                1.dp,
                                if (selected) palette.accent else palette.cardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                monthsCount = preset
                                monthsText = preset.toString()
                                val newMonths = DebtCatalog.buildMonthsFromKey(startKey, preset)
                                if ((emiAmountText.toDoubleOrNull() ?: 0.0) > 0.0 && !showCustomMonths) {
                                    applyEqualEmis(emiAmountText, newMonths)
                                }
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${preset}m",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) palette.accent else palette.textSecondary
                        )
                    }
                }
            }

            // Smart Live Summary Banner
            if (activeEmiCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSoftBg)
                        .border(1.dp, palette.accentBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "$activeEmiCount EMIs · Total ${formatMoney(activeTotal, currency)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.accent
                    )
                    Text(
                        "${months.first().label} → ${months.last().label}",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }

            // Expandable Custom Per-Month Editor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.subtleSurface)
                    .clickable { showCustomMonths = !showCustomMonths }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Customize Individual Months ($monthsCount)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textPrimary
                )
                Icon(
                    imageVector = if (showCustomMonths) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showCustomMonths) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.forEach { m ->
                        OutlinedTextField(
                            value = monthTexts[m.key] ?: "",
                            onValueChange = { monthTexts[m.key] = sanitizeDecimalInput(it) },
                            label = { Text(m.label) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val nextCount = (monthsCount + 1).coerceAtMost(360)
                            monthsCount = nextCount
                            monthsText = nextCount.toString()
                            val updatedMonths = DebtCatalog.buildMonthsFromKey(startKey, nextCount)
                            val fillVal = emiAmountText.toDoubleOrNull()?.takeIf { it > 0.0 }
                            if (fillVal != null) {
                                monthTexts[updatedMonths.last().key] = formatInputAmount(fillVal)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add 1 More Month")
                    }
                }
            }
        }
    }
}

private fun formatInputAmount(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

private fun sanitizeDecimalInput(raw: String): String {
    var dotSeen = false
    val sb = StringBuilder()
    for (ch in raw) {
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !dotSeen -> {
                dotSeen = true
                sb.append(ch)
            }
        }
    }
    return sb.toString()
}
