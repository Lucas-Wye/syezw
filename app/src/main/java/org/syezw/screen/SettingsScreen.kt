package org.syezw.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.syezw.model.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel, modifier: Modifier = Modifier
) {
    // 1. 为 collectAsState 提供初始值
    val currentAuthor by settingsViewModel.defaultAuthor.collectAsState(initial = "")
    val currentDateTogether by settingsViewModel.dateTogether.collectAsState(initial = "")
    val loveBgImageUri by settingsViewModel.loveBgImageUri.collectAsState(initial = null)
    val loveBgEnabled by settingsViewModel.loveBgEnabled.collectAsState(initial = false)

    // 从 ViewModel 获取周期记录功能的开启状态
    // 注意：isPeriodTrackingEnabled 是一个 StateFlow，它总是有初始值，所以这里不需要提供 initial 参数
    val isPeriodTrackingEnabled by settingsViewModel.isPeriodTrackingEnabled.collectAsState()

    var authorInput by remember(currentAuthor) { mutableStateOf(currentAuthor) }
    var dateTogetherInput by remember(currentDateTogether) { mutableStateOf(currentDateTogether) }
    var dateError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope() // 获取协程作用域

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let { settingsViewModel.importData(it) }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                // 获取持久化权限
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                coroutineScope.launch {
                    settingsViewModel.setLoveBgImageUri(it.toString())
                }
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { importLauncher.launch(null) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "恢复备份")
                }
                FloatingActionButton(
                    onClick = { settingsViewModel.exportData() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "导出备份")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = authorInput,
                onValueChange = { authorInput = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = dateTogetherInput,
                onValueChange = {
                    dateTogetherInput = it
                    // Basic validation on input change
                    dateError = try {
                        // 日期格式应该从 ViewModel 或一个统一的地方获取
                        // 为了简单起见，这里我们先硬编码
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                            isLenient = false
                        }.parse(it)
                        null // No error
                    } catch (e: Exception) {
                        "无效的日期格式 (YYYY-MM-DD)"
                    }
                },
                label = { Text("Together Date") },
                placeholder = { Text("e.g., 2025-04-06") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = dateError != null
            )
            if (dateError != null) {
                Text(
                    text = dateError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 添加周期记录功能的开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable Period Tracking",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isPeriodTrackingEnabled,
                    onCheckedChange = { isEnabled ->
                        // 当开关状态改变时，立刻更新 ViewModel
                        // 2. 在协程中调用挂起函数
                        coroutineScope.launch {
                            settingsViewModel.setPeriodTrackingEnabled(isEnabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 添加Love背景图片功能
            Text(
                text = "Love Screen Background",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable Background Image",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = loveBgEnabled,
                    onCheckedChange = { isEnabled ->
                        coroutineScope.launch {
                            settingsViewModel.setLoveBgEnabled(isEnabled)
                        }
                    }
                )
            }

            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loveBgImageUri != null) "更改背景图片" else "选择背景图片")
            }

            if (loveBgImageUri != null) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsViewModel.setLoveBgImageUri(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("移除背景图片")
                }
            }

            Button(
                onClick = {
                    if (dateError == null) { // Only save if format is valid
                        // 3. 在协程中调用挂起函数
                        coroutineScope.launch {
                            settingsViewModel.updateDefaultAuthor(authorInput)
                            settingsViewModel.updateDate(dateTogetherInput)
                        }
                        // 注意：开关状态已经实时保存，这里无需再次保存
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = dateError == null // Disable button if format is invalid
            ) {
                Text("Save Settings")
            }
        }
    }
}
