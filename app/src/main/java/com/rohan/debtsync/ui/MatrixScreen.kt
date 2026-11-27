package com.rohan.debtsync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.model.DebtCatalog
import com.rohan.debtsync.model.PaymentStatus
import com.rohan.debtsync.model.formatMoney
import com.rohan.debtsync.ui.theme.LocalDebtPalette

private enum class MatrixStatusFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    SETTLED("Settled")
}

@Composable
fun MatrixScreen(vm: DebtViewModel) {
    val palette = LocalDebtPalette.current
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(MatrixStatusFilter.ALL) }
    val allMonths = DebtCatalog.allMonthsFor(vm.loans)
    val activeMonths = allMonths.filter { m ->
        vm.loans.any { (it.payments[m.key]?.amount ?: 0.0) > 0.0 }
    }.ifEmpty { allMonths.take(7) }

    val filtered = vm.loans.filter { loan ->
        val matchesSearch = search.isBlank() || loan.platform.contains(search.trim(), ignoreCase = true)
        val matchesStatus = when (statusFilter) {
            MatrixStatusFilter.ALL -> true
            MatrixStatusFilter.PENDING -> !loan.isSettled && loan.hasSchedule
            MatrixStatusFilter.SETTLED -> loan.isSettled
        }
        matchesSearch && matchesStatus
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search platform...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.cardBg,
                unfocusedContainerColor = palette.cardBg,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.cardBorder
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MatrixStatusFilter.entries.forEach { f ->
                val selected = statusFilter == f
                FilterChip(
                    selected = selected,
                    onClick = { statusFilter = f },
                    label = { Text(f.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.cardBg,
                        labelColor = palette.textSecondary,
                        selectedContainerColor = palette.accentSoftBg,
                        selectedLabelColor = palette.accent
                    )
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            border = BorderStroke(1.dp, palette.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.weight(1f)
        ) {
            val hScroll = rememberScrollState()
            val vScroll = rememberScrollState()

            Column(
                modifier = Modifier
                    .horizontalScroll(hScroll)
                    .verticalScroll(vScroll)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.subtleSurface)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MatrixHeaderCell("PLATFORM", 120)
                    activeMonths.forEach { m -> MatrixHeaderCell(m.shortLabel.uppercase(), 86) }
                    MatrixHeaderCell("LEFT", 96)
                    MatrixHeaderCell("PAID", 90)
                    MatrixHeaderCell("EMIS", 56)
                }
                HorizontalDivider(color = palette.divider)

                if (filtered.isEmpty()) {
                    Text(
                        if (vm.loans.isEmpty()) "No loan accounts added yet." else "No matching loan accounts.",
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(vertical = 20.dp, horizontal = 8.dp)
                    )
                }

                filtered.forEach { loan ->
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            loan.platform,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.width(120.dp).padding(horizontal = 6.dp)
                        )
                        activeMonths.forEach { m ->
                            val emi = loan.payments[m.key]
                            if (emi == null || emi.amount <= 0.0) {
                                Text(
                                    "—",
                                    fontSize = 12.sp,
                                    color = palette.textMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(86.dp)
                                )
                            } else {
                                val paid = emi.status == PaymentStatus.PAID
                                Column(
                                    modifier = Modifier
                                        .width(86.dp)
                                        .padding(horizontal = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (paid) palette.successBg else palette.subtleSurface)
                                        .border(
                                            1.dp,
                                            if (paid) palette.successBorder else palette.cardBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { vm.togglePayment(loan.id, m.key) }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        formatMoney(emi.amount, vm.currency, compact = true),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (paid) palette.success else palette.textPrimary
                                    )
                                    Text(
                                        if (paid) "Paid ✓" else "Due",
                                        fontSize = 9.sp,
                                        color = if (paid) palette.success else palette.warning
                                    )
                                }
                            }
                        }
                        Text(
                            formatMoney(loan.amountLeft, vm.currency, compact = true),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(96.dp).padding(horizontal = 6.dp)
                        )
                        Text(
                            formatMoney(loan.totalPaid, vm.currency, compact = true),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.success,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(90.dp).padding(horizontal = 6.dp)
                        )
                        Text(
                            "${loan.emisLeft}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(56.dp)
                        )
                    }
                    HorizontalDivider(color = palette.divider)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.subtleSurface)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                        modifier = Modifier.width(120.dp).padding(horizontal = 6.dp)
                    )
                    activeMonths.forEach { m ->
                        val colTotal = filtered.sumOf { it.payments[m.key]?.amount ?: 0.0 }
                        Text(
                            formatMoney(colTotal, vm.currency, compact = true),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(86.dp)
                        )
                    }
                    Text(
                        formatMoney(filtered.sumOf { it.amountLeft }, vm.currency, compact = true),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(96.dp).padding(horizontal = 6.dp)
                    )
                    Text(
                        formatMoney(filtered.sumOf { it.totalPaid }, vm.currency, compact = true),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.success,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(90.dp).padding(horizontal = 6.dp)
                    )
                    Text(
                        "${filtered.sumOf { it.emisLeft }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixHeaderCell(text: String, widthDp: Int) {
    val palette = LocalDebtPalette.current
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(widthDp.dp).padding(horizontal = 4.dp)
    )
}
