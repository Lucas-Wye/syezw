package org.syezw.screen

import android.app.DatePickerDialog
import android.content.Context
import android.widget.DatePicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.syezw.model.OvulationPrediction
import org.syezw.model.PeriodRecord
import org.syezw.model.PeriodViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun PeriodTrackingScreen(viewModel: PeriodViewModel, modifier: Modifier = Modifier) {
    val records by viewModel.periodRecords.collectAsState()
    val avgCycleLast3 by viewModel.avgCycleLast3.collectAsState()
    val avgCycleLast5 by viewModel.avgCycleLast5.collectAsState()
    val daysSinceLastPeriod by viewModel.daysSinceLastPeriod.collectAsState()
    val lastCycleDuration by viewModel.lastCycleDuration.collectAsState()
    val ovulationPrediction by viewModel.ovulationPrediction.collectAsState()
    val predictedNextPeriodDate by viewModel.predictedNextPeriodDate.collectAsState()
    val lastPeriodDuration by viewModel.lastPeriodDuration.collectAsState()
    val avgPeriodDurationLast3 by viewModel.avgPeriodDurationLast3.collectAsState()

    val context = LocalContext.current
    var recordToEditNotes by remember { mutableStateOf<PeriodRecord?>(null) }
    var recordToEditEndDate by remember { mutableStateOf<PeriodRecord?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { viewModel.exportData(context, it) }
        }
    )

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.importData(context, it) }
        }
    )

    Scaffold(
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp) // 给按钮之间增加间距
            ) {
                // 添加
                FloatingActionButton(onClick = {
                    showDatePicker(
                        context = context,
                        onDateSelected = { viewModel.addPeriodStartDate(it) },
                        onDismiss = null
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加新经期")
                }
                // 导入按钮
                FloatingActionButton(
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("application/json"))
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Import Diaries")
                }
                // 导出按钮
                FloatingActionButton(
                    onClick = {
                        val timestamp =
                            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                                java.util.Date()
                            )
                        createDocumentLauncher.launch("period_data_$timestamp.json")
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Export Diaries")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            StatisticsCard(
                avg3 = avgCycleLast3,
                avg5 = avgCycleLast5,
                sinceLast = daysSinceLastPeriod,
                lastDuration = lastCycleDuration,
                ovulationPrediction = ovulationPrediction,
                predictedNextPeriodDate = predictedNextPeriodDate,
                lastPeriodDuration = lastPeriodDuration,
                avgPeriodDuration3 = avgPeriodDurationLast3
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("历史记录", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(records) { record ->
                    PeriodRecordItem(
                        record = record,
                        onEditEndDateClick = { recordToEditEndDate = record },
                        onEditNotesClick = { recordToEditNotes = record },
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    recordToEditNotes?.let { record ->
        NotesEditDialog(
            record = record,
            onDismiss = { recordToEditNotes = null },
            onSave = { newNotes ->
                viewModel.updateRecordNotes(record, newNotes)
                recordToEditNotes = null
            }
        )
    }

    recordToEditEndDate?.let { record ->
        showDatePicker(
            context = context,
            onDateSelected = { newEndDate ->
                viewModel.updatePeriodEndDate(record, newEndDate)
                recordToEditEndDate = null
            },
            onDismiss = { recordToEditEndDate = null },
            initialDate = record.startDate
        )
    }
}

@Composable
fun StatisticsCard(
    avg3: Int,
    avg5: Int,
    sinceLast: Long?,
    lastDuration: Long?,
    ovulationPrediction: OvulationPrediction?,
    predictedNextPeriodDate: LocalDate?,
    lastPeriodDuration: Long?,
    avgPeriodDuration3: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (sinceLast != null) {
                Text(
                    buildAnnotatedString {
                        append("距离上次: ")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        ) {
                            append("$sinceLast")
                        }
                        append(" 天")
                    },
                    fontSize = 18.sp
                )
            }
            Text(
                "上次周期: ${if (lastDuration != null) "$lastDuration 天" else "数据不足"}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("最近3次平均", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (avg3 > 0) "$avg3 天" else "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("最近5次平均", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (avg5 > 0) "$avg5 天" else "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("上次经期时长", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (lastPeriodDuration != null) "$lastPeriodDuration 天" else "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("平均经期时长(最近3次)", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (avgPeriodDuration3 > 0) "$avgPeriodDuration3 天" else "-",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OvulationInfo(
                prediction = ovulationPrediction,
                predictedNextPeriodDate = predictedNextPeriodDate
            )
        }
    }
}

@Composable
private fun PeriodRecordItem(
    record: PeriodRecord,
    onEditEndDateClick: () -> Unit,
    onEditNotesClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEditEndDateClick)
        ) {
            Text(
                text = "${record.startDate.format(dateFormatter)} - ${
                    record.endDate.format(
                        dateFormatter
                    )
                }",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "持续 ${record.realDuration} 天，与上次间隔 ${record.daysSinceLast?.let { "$it 天" } ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (!record.notes.isNullOrBlank()) {
                Text(
                    "备注: ${record.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEditNotesClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "编辑备注")
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除记录",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesEditDialog(record: PeriodRecord, onDismiss: () -> Unit, onSave: (notes: String) -> Unit) {
    var notesInput by remember { mutableStateOf(record.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = record.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))) },
        text = {
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("情况说明 (可留空)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        },
        confirmButton = { Button(onClick = { onSave(notesInput) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun showDatePicker(
    context: Context,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: (() -> Unit)?,
    initialDate: LocalDate? = null
) {
    val calendar = Calendar.getInstance()
    if (initialDate != null) {
        calendar.set(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
    }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onDateSelected(LocalDate.of(year, month + 1, dayOfMonth)) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    if (onDismiss != null) {
        datePickerDialog.setOnDismissListener { onDismiss() }
    }
    datePickerDialog.show()
}

@Composable
fun OvulationInfo(prediction: OvulationPrediction?, predictedNextPeriodDate: LocalDate?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "周期预测",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (prediction != null && predictedNextPeriodDate != null) {
            val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
            Text(
                "预测经期: ${predictedNextPeriodDate.format(dateFormatter)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "易孕期: ${prediction.startDate.format(dateFormatter)} - ${
                    prediction.endDate.format(
                        dateFormatter
                    )
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "预计排卵日: ${prediction.peakDate.format(dateFormatter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            Text(
                "记录不足，无法预测",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
