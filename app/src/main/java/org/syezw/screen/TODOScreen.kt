package org.syezw.screen

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.syezw.data.TodoTask
import org.syezw.model.TodoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TODOScreen(
    viewModel: TodoViewModel, modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current


    val exportTodosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"), onResult = { uri ->
            uri?.let {
                viewModel.exportTodosToJson(context, it)
            }
        })

    val importTodosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            uri?.let {
                viewModel.importTodosFromJson(context, it)
            }
        })

    Scaffold(
        modifier = modifier, floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        viewModel.clearInputFields()
                        showAddEditDialog = true
                    }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Task")
                }
                FloatingActionButton(
                    onClick = { importTodosLauncher.launch(arrayOf("application/json")) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Import Tasks")
                }
                FloatingActionButton(
                    onClick = {
                        val timestamp =
                            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        exportTodosLauncher.launch("tasks_export_$timestamp.json")
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Export Tasks")
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
            if (uiState.tasks.isEmpty()) {
                item {
                    Text(
                        "No tasks yet. Tap the '+' button to add one!",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            items(uiState.tasks, key = { it.id }) { task ->
                TodoTaskItem(
                    task = task,
                    onEditClick = {
                        viewModel.selectTask(task)
                        showAddEditDialog = true
                    },
                    onDeleteClick = { viewModel.deleteTask(task) },
                    onToggleComplete = { viewModel.toggleCompletion(task) })
            }
        }

        if (showAddEditDialog) {
            AddEditTodoDialog( // Renamed to AddEditTodoDialog
                viewModel = viewModel, onDismiss = {
                    showAddEditDialog = false
                    // viewModel.clearInputFields() // Clearing when dialog opens is often better
                })
        }
    }
}

@Composable
fun TodoTaskItem(
    task: TodoTask, onEditClick: () -> Unit, onDeleteClick: () -> Unit, onToggleComplete: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted, onCheckedChange = { onToggleComplete() })
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name, // Changed from title
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                        Date(
                            task.createdAt
                        )
                    ), style = MaterialTheme.typography.bodySmall
                )
                if (task.isCompleted && task.completedAt != null) {
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                            Date(task.completedAt)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Task")
            }
            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Task")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this task: \"${task.name}\"?") }, // Using task.name
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirmDialog = false
                    }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            })
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTodoDialog( // Updated for new fields
    viewModel: TodoViewModel, onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current // For Toasts

    // Create a local, stable reference to selectedTask
    val currentSelectedTask = uiState.selectedTask

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentSelectedTask == null) "Add New Task" else "Edit Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.currentName, // Changed to currentName
                    onValueChange = { viewModel.updateCurrentName(it) }, // Changed to updateCurrentName
                    label = { Text("Task Name*") },
                    isError = uiState.currentName.isBlank(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uiState.currentName.isBlank()) {
                        Toast.makeText(context, "Task name cannot be empty.", Toast.LENGTH_SHORT)
                            .show()
                        return@Button
                    }
                    // Use the local variable here
                    val taskToSave = currentSelectedTask?.copy(
                        name = uiState.currentName, isCompleted = currentSelectedTask.isCompleted
                        // If currentSelectedTask is not null, completedAt should already be part of it
                        // and copy() will preserve it unless explicitly overridden.
                    ) ?: TodoTask( // If new task
                        name = uiState.currentName,
                        createdAt = System.currentTimeMillis(),
                        isCompleted = false,
                        completedAt = null // Explicitly set to null for new, incomplete tasks
                    )

                    if (currentSelectedTask == null) {
                        viewModel.addTask(taskToSave.name) // Simplified addTask
                    } else {
                        viewModel.updateTask(taskToSave)
                    }
                    onDismiss()
                }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        })
}