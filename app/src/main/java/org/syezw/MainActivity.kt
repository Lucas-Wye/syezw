package org.syezw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel

import org.syezw.Utils.daysFromTodayTo
import org.syezw.Utils.isSpecial
import org.syezw.ui.theme.SyezwTheme
import org.syezw.ui.theme.DayColor
import org.syezw.ui.theme.LoveColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SyezwTheme {
                SyezwApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun SyezwApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> OurLove(modifier = Modifier.padding(innerPadding))
                AppDestinations.TODO -> TODOScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.PHOTO -> PhotoScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.DIARY -> DiaryScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Love", Icons.Default.Favorite),
    TODO("TODO", Icons.Default.Check),
    PHOTO("Photo", Icons.Default.AccountBox),
    DIARY(label = "DIARY", Icons.Default.MailOutline)
}

@Composable
fun OurLove(modifier: Modifier = Modifier) {
    var days by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        days = daysFromTodayTo(2025, 4, 6)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "在一起已经${(days ?: 0).toString()}天啦！",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DayColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (isSpecial((days ?: 0) + 1)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "♡今天是第${((days ?: 0) + 1).toString()}天哦(｡･ω･｡)ﾉ",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = LoveColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TODOScreen(TodoViewModel: TodoViewModel = viewModel(), modifier: Modifier = Modifier) {
    var taskName by remember { mutableStateOf("") }

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
        BasicTextField(
            value = taskName,
            onValueChange = { taskName = it },
            decorationBox = { innerTextField ->
                if (taskName.isEmpty()) {
                    Text("Enter task name")
                }
                innerTextField()
            }
        )
        Button(onClick = {
            if (taskName.isNotBlank()) {
                TodoViewModel.addTask(taskName)
                taskName = ""
            }
        }) {
            Text("Add Task")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TaskList(TodoViewModel.tasks, TodoViewModel)
    }
}

@Composable
fun TaskList(tasks: List<Task>, viewModel: TodoViewModel) {
    LazyColumn {
        items(tasks.size) { index ->
            val task = tasks[index]

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

@Composable
fun PhotoScreen(modifier: Modifier = Modifier) {
    Text(text = "傻瓜，这里还没有东西呢", modifier = modifier)
}

@Composable
fun DiaryScreen(modifier: Modifier = Modifier) {
    Text(text = "傻瓜，这里还没有东西！", modifier = modifier)
}

// @Preview(showBackground=true)
// @Composable
// fun ToolsScreenPreview() {
//     SyezwTheme {
//         TODOScreen()
//     }
// }
