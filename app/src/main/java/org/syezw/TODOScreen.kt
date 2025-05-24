package org.syezw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.syezw.model.TodoViewModel


@Composable
fun TODOScreen(viewModel: TodoViewModel = viewModel(), modifier: Modifier = Modifier) {
    var taskName by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "TODO List",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            label = { Text("Enter task name") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a new task") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (taskName.isNotBlank()) {
                viewModel.addTask(taskName)
                taskName = ""
            }
        }) {
            Text("Add Task")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(viewModel.tasks.size) { index ->
                val task = viewModel.tasks[index]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (task.isCompleted) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .padding(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = {
                            viewModel.toggleTaskCompletion(task)
                        }
                    )
                    Text(
                        text = task.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        textAlign = TextAlign.Start,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = { viewModel.removeTask(task) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Delete Task")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}