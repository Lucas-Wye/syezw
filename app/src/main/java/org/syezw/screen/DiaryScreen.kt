package org.syezw.screen

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.syezw.data.Diary
import org.syezw.model.DiaryViewModel
import org.syezw.ui.theme.diaryBackgroundColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier,
    onNavigateToEditEntry: (Int?) -> Unit // Pass null for new entry, id for existing
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val exportDiaryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"), onResult = { uri ->
            uri?.let {
                viewModel.exportDiariesToJson(context, it)
            }
        })

    val importDiaryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            uri?.let {
                viewModel.importDiariesFromJson(context, it)
            }
        })

    Scaffold(
        modifier = modifier, floatingActionButton = {
            Row( // 使用 Row 水平排列按钮
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp) // 按钮之间的间距
            ) {
                // 添加日记
                FloatingActionButton(
                    onClick = {
                        viewModel.clearInputFields() // 为新条目做准备
                        showAddEditDialog = true
                    }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Diary")
                }
                // 导入按钮
                FloatingActionButton(
                    onClick = {
                        importDiaryLauncher.launch(arrayOf("application/json"))
                    },
                    modifier = Modifier.padding(end = 4.dp),
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
                        exportDiaryLauncher.launch("diaries_export_$timestamp.json")
                    },
                    modifier = Modifier.padding(end = 8.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Export Diaries")
                }
            }
        }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.entries.isEmpty()) {
                item {
                    Text(
                        "No diary entries yet. Tap the '+' button to add one!",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
            itemsIndexed(uiState.entries) { index, entry -> // Use itemsIndexed
                val backgroundColor = diaryBackgroundColors[index % diaryBackgroundColors.size]
                DiaryEntryItem(entry = entry, backgroundColor = backgroundColor, onEditClick = {
                    viewModel.getEntryById(entry.id)
                    showAddEditDialog = true
                }, onDeleteClick = { viewModel.deleteEntry(entry) })
            }
        }


        if (showAddEditDialog) {
            AddEditDiaryDialog(
                viewModel = viewModel, onDismiss = { showAddEditDialog = false })
        }
    }
}

@Composable
fun DiaryEntryItem(
    entry: Diary, backgroundColor: Color, onEditClick: () -> Unit, onDeleteClick: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(entry.content, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", Locale.getDefault()
                    ).format(java.util.Date(entry.timestamp))
                } | ${entry.author}", style = MaterialTheme.typography.bodySmall
            )
            if (entry.tags.isNotEmpty()) {
                Text(
                    "#${entry.tags.joinToString(" #")}", style = MaterialTheme.typography.bodySmall
                )
            }
            entry.location?.let {
                Text("地点: $it", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = { showDeleteConfirmDialog = true }) { // 点击时显示对话框
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false }, // 点击对话框外部或返回键时关闭
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this diary?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick() // 执行删除操作
                        showDeleteConfirmDialog = false // 关闭对话框
                    }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false } // 关闭对话框
                ) {
                    Text("Cancel")
                }
            })
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDiaryDialog(
    viewModel: DiaryViewModel, onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var tagInput by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState =
        rememberDatePickerState(initialSelectedDateMillis = uiState.currentTimestamp)
    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().apply { timeInMillis = uiState.currentTimestamp }
            .get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().apply { timeInMillis = uiState.currentTimestamp }
            .get(Calendar.MINUTE),
        is24Hour = true // Or use Locale settings
    )

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uiState.selectedEntry == null) "Add Diary" else "Edit Diary") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.currentContent,
                    onValueChange = { viewModel.updateContent(it) },
                    label = { Text("Diary Content*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 250.dp),
                    isError = uiState.currentContent.isBlank() // Simple validation
                )
                // Time Picker
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(16.dp), // Adjust padding as needed
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm", Locale.getDefault()
                        ).format(java.util.Date(uiState.currentTimestamp)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge // Or another appropriate style
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Select Time")
                }
                // Tag Input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Add Tag*") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (tagInput.isNotBlank()) {
                            viewModel.addTag(tagInput.trim())
                            tagInput = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Tag")
                    }
                }
                if (uiState.currentTags.isEmpty()) {
                    Text("Please add at least one tag.", color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.currentTags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { /* Could make them deletable here */ },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove Tag",
                                    modifier = Modifier.clickable { viewModel.removeTag(tag) })
                            })
                    }
                }
                // Location
                OutlinedTextField(
                    value = uiState.currentLocation ?: "",
                    onValueChange = { viewModel.updateLocation(it.ifBlank { null }) },
                    label = { Text("Location (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("* Required fields", style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uiState.currentContent.isNotBlank() && uiState.currentTags.isNotEmpty()) {
                        viewModel.saveDiaryEntry()
                        onDismiss()
                    }
                    // Else, rely on field error indicators or show a toast
                }, enabled = uiState.currentContent.isNotBlank() && uiState.currentTags.isNotEmpty()
            ) {
                Text("Save")
            }
        },
    )

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                showDatePicker = false
                datePickerState.selectedDateMillis?.let {
                    // Keep current time, only update date
                    val cal =
                        Calendar.getInstance().apply { timeInMillis = uiState.currentTimestamp }
                    val selectedCal = Calendar.getInstance().apply { timeInMillis = it }
                    cal.set(Calendar.YEAR, selectedCal.get(Calendar.YEAR))
                    cal.set(Calendar.MONTH, selectedCal.get(Calendar.MONTH))
                    cal.set(Calendar.DAY_OF_MONTH, selectedCal.get(Calendar.DAY_OF_MONTH))
                    viewModel.updateTimestamp(cal.timeInMillis)
                }
                showTimePicker = true // Show time picker after date
            }) { Text("OK") }
        }, dismissButton = {
            TextButton(onClick = {
                showDatePicker = false
            }) { Text("Cancel") }
        }) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        TimePickerDialog( // You'll need to create this wrapper or find a library like Material 3
            onDismissRequest = { showTimePicker = false }, confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    val cal =
                        Calendar.getInstance().apply { timeInMillis = uiState.currentTimestamp }
                    cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(Calendar.MINUTE, timePickerState.minute)
                    viewModel.updateTimestamp(cal.timeInMillis)
                }) { Text("OK") }
            }, dismissButton = {
                TextButton(onClick = {
                    showTimePicker = false
                }) { Text("Cancel") }
            }) {
            TimePicker(state = timePickerState, modifier = Modifier.padding(16.dp))
        }
    }
}

// A simple wrapper for TimePickerDialog as it's not as straightforward as DatePickerDialog
// in Material 3 compose currently for a standalone dialog.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onDismissRequest: () -> Unit,
    confirmButton: @Composable (() -> Unit),
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}