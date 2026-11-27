package com.rohan.debtsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.model.DebtCatalog
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.ui.theme.LocalDebtPalette

@Composable
fun AddLoanDialog(
    existingLoan: LoanAccount?,
    onDismiss: () -> Unit,
    onSave: (platform: String, dueDay: Int?, amounts: Map<String, Double>) -> Unit
) {
    val palette = LocalDebtPalette.current
    val months = remember(existingLoan?.id) {
        DebtCatalog.allMonthsFor(listOfNotNull(existingLoan))
    }

    var name by remember(existingLoan?.id) { mutableStateOf(existingLoan?.platform ?: "") }
    var dueDayText by remember(existingLoan?.id) { mutableStateOf(existingLoan?.dueDay?.toString() ?: "") }
    var qfAmount by remember(existingLoan?.id) { mutableStateOf("") }
    var qfMonths by remember(existingLoan?.id) { mutableStateOf("3") }

    val monthTexts = remember(existingLoan?.id) {
        mutableStateMapOf<String, String>().apply {
            months.forEach { m ->
                val v = existingLoan?.payments?.get(m.key)?.amount
                put(m.key, if (v != null && v > 0) formatInputAmount(v) else "")
            }
        }
    }

    val hasPositiveEmi = monthTexts.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingLoan == null) "Add Loan Account" else "Edit ${existingLoan.platform}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Platform / Loan Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Due Day of Month (1-31, for EMI reminder)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Quick-Fill Equal EMIs (starting ${months.first().label})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qfAmount,
                        onValueChange = { qfAmount = sanitizeDecimalInput(it) },
                        label = { Text("EMI Amt") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = qfMonths,
                        onValueChange = { qfMonths = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Months") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedButton(
                    onClick = {
                        val amt = qfAmount.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@OutlinedButton
                        val cnt = (qfMonths.toIntOrNull() ?: 1).coerceIn(1, months.size)
                        val formatted = formatInputAmount(amt)
                        months.forEachIndexed { idx, m ->
                            monthTexts[m.key] = if (idx < cnt) formatted else ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Quick-Fill")
                }

                Text(
                    "Monthly Schedule",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmounts = monthTexts.mapValues {
                        (it.value.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    }
                    val dueDay = dueDayText.toIntOrNull()?.coerceIn(1, 31)
                    onSave(name, dueDay, parsedAmounts)
                },
                enabled = name.isNotBlank() && hasPositiveEmi
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
