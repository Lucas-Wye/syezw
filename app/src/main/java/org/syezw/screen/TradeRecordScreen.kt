package org.syezw.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import org.syezw.model.SettingsViewModel
import org.syezw.model.TradeOrderType
import org.syezw.model.TradeRecordItem
import org.syezw.model.TradeRecordState
import java.text.DecimalFormat
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeRecordScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradeState by settingsViewModel.tradeRecordState.collectAsState(
        initial = TradeRecordState()
    )
    var currentTab by remember { mutableStateOf(TradeTab.HISTORY) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                ),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tradeState.title, fontSize = 20.sp)
                        Text(
                            text = tradeState.accountMasked,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_text_tertiary)
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "返回",
                            tint = colorResource(id = org.syezw.R.color.trade_text_primary)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(colorResource(id = org.syezw.R.color.trade_background))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Surface(
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // 股票名字
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tradeState.stockName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_text_primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tradeState.stockCode,
                            fontSize = 16.sp,
                            color = colorResource(id = org.syezw.R.color.trade_text_secondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tradeState.stockPrice,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_red)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatSignedPercent(tradeState.stockChangePercent),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_red)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "进入",
                            tint = colorResource(id = org.syezw.R.color.trade_text_tertiary)
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = colorResource(id = org.syezw.R.color.trade_divider)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "参考盈亏",
                            fontSize = 16.sp,
                            // fontWeight = FontWeight.Bold,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_text_primary)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "数据说明",
                                fontSize = 13.sp,
                                color = colorResource(id = org.syezw.R.color.trade_text_tertiary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = org.syezw.R.drawable.ic_help_outline),
                                contentDescription = "数据说明",
                                tint = colorResource(id = org.syezw.R.color.trade_text_tertiary),
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val profitValue = formatSignedAmount(tradeState.referenceProfit)
                        val profitColor =
                            if (profitValue.isNegative) colorResource(id = org.syezw.R.color.trade_green)
                            else colorResource(id = org.syezw.R.color.trade_red)
                        Text(
                            text = profitValue.text,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            // fontWeight = FontWeight.Bold,
                            color = profitColor                            
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatSignedPercent(tradeState.referenceProfitPercent),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_text_primary)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            TradeTabRow(
                currentTab = currentTab,
                onTabChange = { currentTab = it }
            )
            if (currentTab == TradeTab.HISTORY) {
                // 持仓天数、买入次数、卖出次数
                Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    SummaryRow(
                        holdDays = tradeState.holdDays,
                        buyTimes = tradeState.buyTimes,
                        sellTimes = tradeState.sellTimes
                    )
                }
                // 交易记录
                Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .padding(start = 10.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "交易记录",
                            fontSize = 16.sp,
                            // fontWeight = FontWeight.Bold,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = org.syezw.R.color.trade_text_primary)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "个股清仓记录",
                                fontSize = 14.sp,
                                color = colorResource(id = org.syezw.R.color.trade_text_tertiary)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "进入",
                                tint = colorResource(id = org.syezw.R.color.trade_text_tertiary)
                            )
                        }
                    }
                }
                TradeRecordList(
                    records = tradeState.historyRecords,
                    showFee = true,
                    showDateLabel = true
                )
            } else {
                TradeRecordList(
                    records = tradeState.dayRecords,
                    showFee = false,
                    showDateLabel = false
                )
            }
        }
    }
}

private enum class TradeTab { HISTORY, DAY }

@Composable
private fun TradeTabRow(
    currentTab: TradeTab,
    onTabChange: (TradeTab) -> Unit
) {
    Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButtonLabel(
                text = "历史交易",
                selected = currentTab == TradeTab.HISTORY,
                onClick = { onTabChange(TradeTab.HISTORY) }
            )
            TextButtonLabel(
                text = "当日交易",
                selected = currentTab == TradeTab.DAY,
                onClick = { onTabChange(TradeTab.DAY) }
            )
        }
    }
}

@Composable
private fun TextButtonLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            fontSize = 16.sp,
            // fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colorResource(id = org.syezw.R.color.trade_red) else MaterialTheme.colorScheme.onSurfaceVariant            
        )
    }
}

@Composable
private fun SummaryRow(holdDays: String, buyTimes: String, sellTimes: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryItem(label = "持仓天数", value = holdDays)
        SummaryItem(label = "买入次数", value = buyTimes)
        SummaryItem(label = "卖出次数", value = sellTimes)
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colorResource(id = org.syezw.R.color.trade_text_secondary), maxLines = 1)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            value,
            fontSize = 14.sp,
            color = colorResource(id = org.syezw.R.color.trade_text_primary),
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun TradeRecordList(
    records: List<TradeRecordItem>,
    showFee: Boolean,
    showDateLabel: Boolean
) {
    if (records.isEmpty()) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        records.forEach { record ->
            Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isBuy = record.type == TradeOrderType.BUY || record.type == TradeOrderType.OPEN
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when (record.type) {
                                        TradeOrderType.BUY -> "买入"
                                        TradeOrderType.SELL -> "卖出"
                                        TradeOrderType.OPEN -> "建仓"
                                    },
                                    fontSize = 16.sp,
                                    color = if (isBuy) {
                                        colorResource(id = org.syezw.R.color.trade_red)
                                    } else {
                                        colorResource(id = org.syezw.R.color.trade_green)
                                    },
                                    maxLines = 1
                                )
                                Text(
                                    text = formatDate(record.year, record.month, record.day),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorResource(id = org.syezw.R.color.trade_text_tertiary),
                                    textAlign = TextAlign.End,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(28.dp))
                        Box(modifier = Modifier.weight(1f)) {}
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val amount = computeAmount(record.price, record.quantity, if (showFee) 2 else 3)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MetricItem(label = "价格", value = record.price)
                        }
                        Spacer(modifier = Modifier.width(28.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            MetricItem(label = "金额", value = amount)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MetricItem(label = "数量", value = record.quantity)
                        }
                        Spacer(modifier = Modifier.width(28.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (showFee) {
                                MetricItem(label = "费用", value = record.fee)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            fontSize = 16.sp,
            color = colorResource(id = org.syezw.R.color.trade_text_secondary),
            maxLines = 1
        )
        Text(
            value,
            fontSize = 16.sp,
            color = colorResource(id = org.syezw.R.color.trade_text_primary),
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}

private data class SignedAmount(val text: String, val isNegative: Boolean)

private fun formatSignedAmount(raw: String): SignedAmount {
    val cleaned = raw.replace(",", "").trim()
    val value = cleaned.toDoubleOrNull()
    return if (value == null) {
        SignedAmount(raw, false)
    } else {
        val isNegative = value < 0
        val absText = DecimalFormat("#,##0.000").format(value.absoluteValue)
        SignedAmount(if (isNegative) "-$absText" else "+$absText", isNegative)
    }
}

private fun formatSignedRaw(raw: String): String {
    val cleaned = raw.replace(",", "").trim()
    val value = cleaned.toDoubleOrNull() ?: return raw
    val trimmed = raw.trim()
    return if (value > 0 && !trimmed.startsWith("+")) {
        "+$trimmed"
    } else {
        trimmed
    }
}

private fun formatSignedPercent(raw: String): String {
    val cleaned = raw.replace("%", "").replace(",", "").trim()
    val value = cleaned.toDoubleOrNull() ?: return raw
    val formatted = DecimalFormat("0.00").format(kotlin.math.abs(value))
    return when {
        value > 0 -> "+$formatted%"
        value < 0 -> "-$formatted%"
        else -> "$formatted%"
    }
}

private fun formatDate(year: String, month: String, day: String): String {
    if (year.isBlank() && month.isBlank() && day.isBlank()) return ""
    return "${year.trim()}-${month.trim()}-${day.trim()}"
}

private fun computeAmount(priceRaw: String, qtyRaw: String, decimals: Int): String {
    val price = priceRaw.replace(",", "").trim().toDoubleOrNull()
    val qty = qtyRaw.replace(",", "").trim().toDoubleOrNull()
    if (price == null || qty == null) return ""
    val amount = price * qty
    val pattern = "#,##0.${"0".repeat(decimals)}"
    return DecimalFormat(pattern).format(amount)
}
