package org.syezw.screen

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.syezw.model.SettingsViewModel
import org.syezw.model.TradeOrderType
import org.syezw.model.TradeRecordItem
import org.syezw.model.TradeRecordState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradeState by settingsViewModel.tradeRecordState.collectAsState(initial = TradeRecordState())

    var title by remember(tradeState.title) { mutableStateOf(tradeState.title) }
    var accountMasked by remember(tradeState.accountMasked) { mutableStateOf(tradeState.accountMasked) }
    var stockName by remember(tradeState.stockName) { mutableStateOf(tradeState.stockName) }
    var recordStockCode by remember(tradeState.stockCode) { mutableStateOf(tradeState.stockCode) }
    var stockPrice by remember(tradeState.stockPrice) { mutableStateOf(tradeState.stockPrice) }
    var stockChangePercent by remember(tradeState.stockChangePercent) { mutableStateOf(tradeState.stockChangePercent) }
    var referenceProfit by remember(tradeState.referenceProfit) { mutableStateOf(tradeState.referenceProfit) }
    var referenceProfitPercent by remember(tradeState.referenceProfitPercent) { mutableStateOf(tradeState.referenceProfitPercent) }
    var holdDays by remember(tradeState.holdDays) { mutableStateOf(tradeState.holdDays) }
    var buyTimes by remember(tradeState.buyTimes) { mutableStateOf(tradeState.buyTimes) }
    var sellTimes by remember(tradeState.sellTimes) { mutableStateOf(tradeState.sellTimes) }
    var historyRecords by remember(tradeState.historyRecords) { mutableStateOf(tradeState.historyRecords) }
    var dayRecords by remember(tradeState.dayRecords) { mutableStateOf(tradeState.dayRecords) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("交易设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("交易记录设置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") })
            OutlinedTextField(value = accountMasked, onValueChange = { accountMasked = it }, label = { Text("账户展示") })
            OutlinedTextField(value = stockName, onValueChange = { stockName = it }, label = { Text("股票名称") })
            OutlinedTextField(value = recordStockCode, onValueChange = { recordStockCode = it }, label = { Text("股票代码") })
            OutlinedTextField(value = stockPrice, onValueChange = { stockPrice = it }, label = { Text("股票价格") })
            OutlinedTextField(value = stockChangePercent, onValueChange = { stockChangePercent = it }, label = { Text("涨跌幅") })
            OutlinedTextField(value = referenceProfit, onValueChange = { referenceProfit = it }, label = { Text("参考盈亏") })
            OutlinedTextField(value = referenceProfitPercent, onValueChange = { referenceProfitPercent = it }, label = { Text("盈亏比例") })
            OutlinedTextField(value = holdDays, onValueChange = { holdDays = it }, label = { Text("持仓天数") })
            OutlinedTextField(value = buyTimes, onValueChange = { buyTimes = it }, label = { Text("买入次数") })
            OutlinedTextField(value = sellTimes, onValueChange = { sellTimes = it }, label = { Text("卖出次数") })

            Text("历史交易", style = MaterialTheme.typography.titleSmall)
            RecordEditor(
                records = historyRecords,
                onChange = { historyRecords = it },
                showFee = true
            )

            Text("当日交易", style = MaterialTheme.typography.titleSmall)
            RecordEditor(
                records = dayRecords,
                onChange = { dayRecords = it },
                showFee = false
            )

            Button(
                onClick = {
                    settingsViewModel.updateTradeRecordState(
                        TradeRecordState(
                            title = title,
                            accountMasked = accountMasked,
                            stockName = stockName,
                            stockCode = recordStockCode,
                            stockPrice = stockPrice,
                            stockChangePercent = stockChangePercent,
                            referenceProfit = referenceProfit,
                            referenceProfitPercent = referenceProfitPercent,
                            holdDays = holdDays,
                            buyTimes = buyTimes,
                            sellTimes = sellTimes,
                            historyRecords = historyRecords,
                            dayRecords = dayRecords
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeTypeDropdown(
    selected: TradeOrderType,
    onSelected: (TradeOrderType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(TradeOrderType.BUY, TradeOrderType.SELL, TradeOrderType.OPEN)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = when (selected) {
                TradeOrderType.BUY -> "买入"
                TradeOrderType.SELL -> "卖出"
                TradeOrderType.OPEN -> "建仓"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("交易类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                TradeOrderType.BUY -> "买入"
                                TradeOrderType.SELL -> "卖出"
                                TradeOrderType.OPEN -> "建仓"
                            }
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordEditor(
    records: List<TradeRecordItem>,
    onChange: (List<TradeRecordItem>) -> Unit,
    showFee: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (records.isEmpty()) {
            Text("暂无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        records.forEachIndexed { index, record ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TradeTypeDropdown(
                            selected = record.type,
                            onSelected = { selected ->
                                val updated = records.toMutableList()
                                updated[index] = record.copy(type = selected)
                                onChange(updated)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            onChange(records.toMutableList().also { it.removeAt(index) })
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = record.year,
                            onValueChange = { value ->
                                val updated = records.toMutableList()
                                updated[index] = record.copy(year = value)
                                onChange(updated)
                            },
                            label = { Text("年") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = record.month,
                            onValueChange = { value ->
                                val updated = records.toMutableList()
                                updated[index] = record.copy(month = value)
                                onChange(updated)
                            },
                            label = { Text("月") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = record.day,
                            onValueChange = { value ->
                                val updated = records.toMutableList()
                                updated[index] = record.copy(day = value)
                                onChange(updated)
                            },
                            label = { Text("日") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = record.price,
                            onValueChange = { value ->
                                val updated = records.toMutableList()
                                updated[index] = record.copy(price = value)
                                onChange(updated)
                            },
                            label = { Text("价格") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = record.quantity,
                            onValueChange = { value ->
                                val updated = records.toMutableList()
                                updated[index] = record.copy(quantity = value)
                                onChange(updated)
                            },
                            label = { Text("数量") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showFee) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = record.fee,
                                onValueChange = { value ->
                                    val updated = records.toMutableList()
                                    updated[index] = record.copy(fee = value)
                                    onChange(updated)
                                },
                                label = { Text("费用") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = { onChange(records + TradeRecordItem()) }) {
            Icon(Icons.Filled.Add, contentDescription = "新增")
            Spacer(modifier = Modifier.width(4.dp))
            Text("新增记录")
        }
    }
}
