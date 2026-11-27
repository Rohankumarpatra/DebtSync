package com.rohan.debtsync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.DebtCatalog
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.PaymentStatus
import com.rohan.debtsync.model.formatMoney
import com.rohan.debtsync.ui.theme.LocalDebtPalette
import java.util.Locale

@Composable
fun OverviewScreen(
    vm: DebtViewModel,
    onAddLoan: () -> Unit,
    onEditLoan: (LoanAccount) -> Unit,
    onDeleteLoan: (LoanAccount) -> Unit
) {
    val palette = LocalDebtPalette.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // KPI Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "AMOUNT LEFT",
                        value = formatMoney(vm.totalAmountLeft, vm.currency),
                        subtitle = "of ${formatMoney(vm.totalScheduled, vm.currency)}",
                        valueColor = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "TOTAL PAID",
                        value = formatMoney(vm.totalPaid, vm.currency),
                        subtitle = "${vm.totalEmisPaid} EMIs done",
                        valueColor = palette.success,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "EMIS LEFT",
                        value = "${vm.totalEmisLeft}",
                        subtitle = "${vm.settledAccountsCount}/${vm.loans.size} settled",
                        valueColor = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    ProgressKpiCard(
                        progress = vm.progressFraction,
                        targetLabel = vm.debtFreeTargetLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Stacked Bar Chart Card
        item {
            MonthlyChartCard(loans = vm.loans, currency = vm.currency)
        }

        // Monthly Checklist Card
        item {
            MonthlyChecklistCard(vm = vm)
        }

        // Section Header
        item {
            Text(
                text = "Loan Accounts (${vm.loans.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        }

        // Loan Account Cards or Empty State
        if (vm.loans.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.cardBg),
                    border = BorderStroke(1.dp, palette.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "No loan accounts yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            "Tap Add Loan below to enter your EMI schedule and due day for automatic reminders.",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onAddLoan,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("Add Your First Loan")
                        }
                    }
                }
            }
        } else {
            items(vm.loans, key = { it.id }) { loan ->
                LoanAccountCard(
                    loan = loan,
                    currency = vm.currency,
                    onToggleEmi = { monthKey -> vm.togglePayment(loan.id, monthKey) },
                    onEdit = { onEditLoan(loan) },
                    onDelete = { onDeleteLoan(loan) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(64.dp)) }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalDebtPalette.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, softWrap = false)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = palette.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun ProgressKpiCard(
    progress: Float,
    targetLabel: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalDebtPalette.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PROGRESS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.accentSoftBg
                ) {
                    Text(
                        targetLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                String.format(Locale.US, "%.1f%%", progress * 100f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = palette.accent,
                trackColor = palette.divider
            )
        }
    }
}

@Composable
private fun MonthlyChartCard(loans: List<LoanAccount>, currency: CurrencyMode) {
    val palette = LocalDebtPalette.current
    val allMonths = DebtCatalog.allMonthsFor(loans)
    val activeMonths = allMonths.filterIndexed { idx, m ->
        idx < 7 || loans.any { (it.payments[m.key]?.amount ?: 0.0) > 0.0 }
    }.take(12)

    val paidList = activeMonths.map { m ->
        loans.sumOf { l ->
            val p = l.payments[m.key]
            if (p != null && p.status == PaymentStatus.PAID) p.amount else 0.0
        }
    }
    val upcomingList = activeMonths.map { m ->
        loans.sumOf { l ->
            val p = l.payments[m.key]
            if (p != null && p.status == PaymentStatus.UPCOMING) p.amount else 0.0
        }
    }
    val maxVal = activeMonths.indices.maxOfOrNull { paidList[it] + upcomingList[it] }?.coerceAtLeast(1.0) ?: 1.0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Monthly Settlement", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
                    Text("Peak: ${formatMoney(maxVal, currency, compact = true)}", fontSize = 11.sp, color = palette.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot("Paid", palette.success)
                    LegendDot("Upcoming", palette.accent)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val count = activeMonths.size.coerceAtLeast(1)
                val slotW = size.width / count
                val barW = (slotW * 0.56f).coerceAtMost(28.dp.toPx())
                val radiusPx = 4.dp.toPx()
                val chartH = size.height

                val gridColor = palette.divider.copy(alpha = 0.6f)
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val y = chartH * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                for (i in 0 until count) {
                    val paidH = ((paidList[i] / maxVal) * chartH).toFloat()
                    val upH = ((upcomingList[i] / maxVal) * chartH).toFloat()
                    val left = i * slotW + (slotW - barW) / 2f

                    if (upH > 0f) {
                        drawRoundRect(
                            color = palette.accent,
                            topLeft = Offset(left, chartH - paidH - upH),
                            size = Size(barW, upH),
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    }
                    if (paidH > 0f) {
                        drawRoundRect(
                            color = palette.success,
                            topLeft = Offset(left, chartH - paidH),
                            size = Size(barW, paidH),
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                activeMonths.forEach { m ->
                    Text(
                        text = m.shortLabel,
                        fontSize = 9.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val palette = LocalDebtPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.sp, color = palette.textSecondary)
    }
}

@Composable
private fun MonthlyChecklistCard(vm: DebtViewModel) {
    val palette = LocalDebtPalette.current
    var expanded by remember { mutableStateOf(false) }
    val allMonths = DebtCatalog.allMonthsFor(vm.loans)
    val currentMonth = allMonths.find { it.key == vm.focusMonthKey } ?: allMonths.first()
    val itemsForMonth = vm.loans.mapNotNull { loan ->
        val emi = loan.payments[vm.focusMonthKey]
        if (emi != null && emi.amount > 0) loan to emi else null
    }
    val remaining = itemsForMonth.filter { it.second.status == PaymentStatus.UPCOMING }.sumOf { it.second.amount }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Monthly Checklist", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
                    Text(
                        "${itemsForMonth.count { it.second.status == PaymentStatus.PAID }} of ${itemsForMonth.size} EMIs paid",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                Box {
                    OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(currentMonth.label, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        allMonths.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                onClick = {
                                    vm.focusMonthKey = m.key
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (itemsForMonth.isEmpty()) {
                Text("No EMIs scheduled for ${currentMonth.label}.", fontSize = 12.sp, color = palette.textSecondary)
            } else {
                itemsForMonth.forEach { (loan, emi) ->
                    val isPaid = emi.status == PaymentStatus.PAID
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPaid) palette.successBg else palette.subtleSurface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                loan.platform,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (isPaid) palette.success else palette.textPrimary,
                                textDecoration = if (isPaid) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Text(
                                formatMoney(emi.amount, vm.currency) + (loan.dueDay?.let { " · Day $it" } ?: ""),
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                        Button(
                            onClick = { vm.togglePayment(loan.id, vm.focusMonthKey) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaid) palette.success else palette.accent,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isPaid) "Paid ✓" else "Mark Paid", fontSize = 12.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.divider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MONTH REMAINING", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                    Text(formatMoney(remaining, vm.currency), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = palette.textPrimary)
                }
                val hasUpcomingInMonth = remaining > 0.0
                Button(
                    onClick = { vm.markFocusMonthPaid() },
                    enabled = hasUpcomingInMonth,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.success,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (hasUpcomingInMonth || itemsForMonth.isEmpty()) "Mark Month Paid" else "All Paid ✓",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanAccountCard(
    loan: LoanAccount,
    currency: CurrencyMode,
    onToggleEmi: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalDebtPalette.current
    val activeMonths = DebtCatalog.allMonthsFor(listOf(loan)).filter { (loan.payments[it.key]?.amount ?: 0.0) > 0.0 }

    val badgeBg = when {
        !loan.hasSchedule -> palette.subtleSurface
        loan.isSettled -> palette.successBg
        else -> palette.warningBg
    }
    val badgeText = when {
        !loan.hasSchedule -> "No EMIs"
        loan.isSettled -> "Settled"
        else -> "Pending"
    }
    val badgeTextColor = when {
        !loan.hasSchedule -> palette.textSecondary
        loan.isSettled -> palette.success
        else -> palette.warning
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(loan.platform, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = palette.textPrimary)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeBg
                        ) {
                            Text(
                                badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "${loan.emisLeft} of ${loan.totalEmis} EMIs left" + (loan.dueDay?.let { " · Due day $it" } ?: ""),
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = palette.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("LEFT", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                    Text(formatMoney(loan.amountLeft, currency), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = palette.textPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("PAID", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                    Text(formatMoney(loan.totalPaid, currency), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.success)
                }
            }

            LinearProgressIndicator(
                progress = { loan.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (loan.isSettled) palette.success else palette.accent,
                trackColor = palette.divider
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeMonths.forEach { m ->
                    val emi = loan.payments[m.key]!!
                    val isPaid = emi.status == PaymentStatus.PAID
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPaid) palette.successBg else palette.subtleSurface)
                            .border(
                                1.dp,
                                if (isPaid) palette.successBorder else palette.cardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleEmi(m.key) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${m.shortLabel} ${formatMoney(emi.amount, currency, compact = true)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPaid) palette.success else palette.textPrimary
                        )
                        if (isPaid) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = palette.success, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}
