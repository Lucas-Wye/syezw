package org.syezw.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
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
    val unusedImageState by settingsViewModel.unusedDiaryImageState.collectAsState()
    val apiBaseUrl by settingsViewModel.remoteApiBaseUrl.collectAsState(initial = "")
    val apiKey by settingsViewModel.remoteApiKey.collectAsState(initial = "")
    val aesPassphrase by settingsViewModel.aesPassphrase.collectAsState(initial = "")
    val uploadProgress by settingsViewModel.uploadProgress.collectAsState()
    val downloadProgress by settingsViewModel.downloadProgress.collectAsState()
    val lastUploadSummary by settingsViewModel.lastUploadSummary.collectAsState()
    val lastDownloadSummary by settingsViewModel.lastDownloadSummary.collectAsState()

    // 从 ViewModel 获取周期记录功能的开启状态
    // 注意：isPeriodTrackingEnabled 是一个 StateFlow，它总是有初始值，所以这里不需要提供 initial 参数
    val isPeriodTrackingEnabled by settingsViewModel.isPeriodTrackingEnabled.collectAsState()

    var authorInput by remember(currentAuthor) { mutableStateOf(currentAuthor) }
    var dateTogetherInput by remember(currentDateTogether) { mutableStateOf(currentDateTogether) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var showUnusedImagesDialog by remember { mutableStateOf(false) }
    var apiBaseUrlInput by remember(apiBaseUrl) { mutableStateOf(apiBaseUrl) }
    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var aesPassphraseInput by remember(aesPassphrase) { mutableStateOf(aesPassphrase) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope() // 获取协程作用域
    val scrollState = rememberScrollState()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                settingsViewModel.checkUnusedDiaryImages()
                showUnusedImagesDialog = true
            }
        }
    )

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
                .padding(16.dp)
                .verticalScroll(scrollState),
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

            Text(
                text = "Remote Sync",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiBaseUrlInput,
                onValueChange = { apiBaseUrlInput = it },
                label = { Text("API Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            OutlinedTextField(
                value = aesPassphraseInput,
                onValueChange = { aesPassphraseInput = it },
                label = { Text("Passphrase") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { settingsViewModel.syncUpload() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("上传")
                }
                Button(
                    onClick = { settingsViewModel.syncDownload() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下载")
                }
            }

            if (uploadProgress.inProgress || uploadProgress.percent > 0) {
                LinearProgressIndicator(
                    progress = { uploadProgress.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "上传进度: ${uploadProgress.percent}% ${uploadProgress.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!lastUploadSummary.isNullOrBlank()) {
                Text(
                    text = lastUploadSummary.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (downloadProgress.inProgress || downloadProgress.percent > 0) {
                LinearProgressIndicator(
                    progress = { downloadProgress.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "下载进度: ${downloadProgress.percent}% ${downloadProgress.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!lastDownloadSummary.isNullOrBlank()) {
                Text(
                    text = lastDownloadSummary.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedButton(
                onClick = { settingsViewModel.exportSyncLogs() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出同步日志")
            }

            Text(
                text = "Diary Image Check",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    val permission = Manifest.permission.READ_MEDIA_IMAGES
                    val hasPermission =
                        ContextCompat.checkSelfPermission(context, permission) ==
                                PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        settingsViewModel.checkUnusedDiaryImages()
                        showUnusedImagesDialog = true
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (unusedImageState.isChecking) "检查中..." else "检查未使用图片")
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
                    settingsViewModel.updateRemoteApiBaseUrl(apiBaseUrlInput)
                    settingsViewModel.updateRemoteApiKey(apiKeyInput)
                    settingsViewModel.updateAesPassphrase(aesPassphraseInput)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = dateError == null // Disable button if format is invalid
            ) {
                Text("Save Settings")
            }
        }
    }

    if (showUnusedImagesDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnusedImagesDialog = false },
            title = { Text("未使用图片检查结果") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (unusedImageState.isChecking) {
                        Text("检查中，请稍候...")
                    } else {
                        unusedImageState.lastCheckedAt?.let { ts ->
                            Text(
                                text = "最近检查: ${
                                    SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss",
                                        Locale.getDefault()
                                    ).format(java.util.Date(ts))
                                }",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "扫描目录图片数: ${unusedImageState.scannedPaths.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "日记引用图片数: ${unusedImageState.usedPaths.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (unusedImageState.unusedPaths.isEmpty()) {
                            Text("未发现未使用图片")
                        } else {
                            Text("未使用图片：(${unusedImageState.unusedPaths.size})")
                            unusedImageState.unusedPaths.forEach { path ->
                                Text(text = path, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (unusedImageState.usedPaths.isNotEmpty()) {
                            Text("日记引用图片路径：(${unusedImageState.usedPaths.size})")
                            unusedImageState.usedPaths.forEach { path ->
                                Text(text = path, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (unusedImageState.scannedPaths.isNotEmpty()) {
                            Text("扫描到的图片路径：(${unusedImageState.scannedPaths.size})")
                            unusedImageState.scannedPaths.forEach { path ->
                                Text(text = path, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnusedImagesDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
