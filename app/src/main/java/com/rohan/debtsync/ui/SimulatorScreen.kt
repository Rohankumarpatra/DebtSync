package com.rohan.debtsync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.model.SimStrategy
import com.rohan.debtsync.model.formatMoney
import com.rohan.debtsync.ui.theme.LocalDebtPalette

@Composable
fun SimulatorScreen(vm: DebtViewModel) {
    val palette = LocalDebtPalette.current
    val sim = vm.runSimulation()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.cardBg),
                border = BorderStroke(1.dp, palette.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extra Monthly Payment", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
                        Text(
                            formatMoney(vm.simExtraBudget, vm.currency, compact = true),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = palette.accent
                        )
                    }

                    Slider(
                        value = vm.simExtraBudget.toFloat().coerceIn(0f, 25000f),
                        onValueChange = { vm.simExtraBudget = (it / 500f).toInt() * 500.0 },
                        valueRange = 0f..25000f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accent,
                            activeTrackColor = palette.accent,
                            inactiveTrackColor = palette.divider
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SimStrategy.entries.forEach { strat ->
                            val selected = vm.simStrategy == strat
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) palette.accentSoftBg else palette.subtleSurface)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) palette.accent else palette.cardBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { vm.simStrategy = strat }
                                    .padding(12.dp)
                            ) {
                                Text(strat.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                                Text(strat.subtitle, fontSize = 11.sp, color = palette.textSecondary)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.summaryBannerBg)
                            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Baseline Finish", fontSize = 12.sp, color = palette.summaryBannerMuted)
                            Text(
                                "${sim.baselineLabel} (${sim.baselineMonths} mo)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.summaryBannerText
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Accelerated Finish", fontSize = 12.sp, color = palette.summaryBannerMuted)
                            Text(
                                "${sim.acceleratedLabel} (${sim.acceleratedMonths} mo)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.success
                            )
                        }
                        HorizontalDivider(color = palette.divider)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Time Saved", fontSize = 12.sp, color = palette.summaryBannerMuted)
                            Text(
                                if (sim.monthsSaved > 0) "${sim.monthsSaved} month(s) faster" else "Same finish month",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.success
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Projected Payoff Order", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
        }

        if (sim.order.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.cardBg),
                    border = BorderStroke(1.dp, palette.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            if (vm.loans.isEmpty()) "No active loans yet" else "You're Debt Free!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = palette.success
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (vm.loans.isEmpty()) {
                                "Add loan accounts on the Overview tab to simulate accelerated payoff."
                            } else {
                                "All scheduled EMIs are settled."
                            },
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }

        itemsIndexed(sim.order) { idx, item ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.cardBg),
                border = BorderStroke(1.dp, palette.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(palette.accentSoftBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.accent)
                        }
                        Column {
                            Text(item.platform, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                            Text(
                                "Left: ${formatMoney(item.originalLeft, vm.currency)} · ${item.emisLeft} EMIs",
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.successBg
                    ) {
                        Text(
                            item.payoffMonthLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.success,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(64.dp)) }
    }
}
