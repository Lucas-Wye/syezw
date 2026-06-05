package org.syezw.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Priority
import org.syezw.model.SettingsViewModel
import org.syezw.service.LocationService
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenTradeRecord: () -> Unit = {}
) {
    var showTradeSettings by rememberSaveable { mutableStateOf(false) }
    if (showTradeSettings) {
        TradeSettingsScreen(
            settingsViewModel = settingsViewModel,
            onBack = { showTradeSettings = false },
            modifier = modifier
        )
        return
    }

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

    val gpsEnabled by settingsViewModel.gpsEnabled.collectAsState(initial = false)
    val gpsPriority by settingsViewModel.gpsPriority.collectAsState(initial = "balanced")
    val gpsIntervalMs by settingsViewModel.gpsIntervalMs.collectAsState(initial = 10_000L)
    val gpsFastestIntervalMs by settingsViewModel.gpsFastestIntervalMs.collectAsState(initial = 5_000L)

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

    fun hasLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isSystemLocationEnabled(): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun startGpsService() {
        val priorityValue = when (gpsPriority) {
            "high_accuracy" -> Priority.PRIORITY_HIGH_ACCURACY
            "balanced" -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            "low_power" -> Priority.PRIORITY_LOW_POWER
            "no_power" -> Priority.PRIORITY_PASSIVE
            else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        LocationService.start(
            context = context,
            priority = priorityValue,
            intervalMs = gpsIntervalMs,
            fastestIntervalMs = gpsFastestIntervalMs,
            author = currentAuthor
        )
        Toast.makeText(context, "GPS tracking started", Toast.LENGTH_SHORT).show()
    }

    fun stopGpsService() {
        LocationService.stop(context)
        Toast.makeText(context, "GPS tracking stopped", Toast.LENGTH_SHORT).show()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
            } else {
                true
            }
            if (fineGranted || coarseGranted) {
                if (!backgroundGranted) {
                    Toast.makeText(context, "Background location permission is recommended for GPS tracking", Toast.LENGTH_LONG).show()
                }
                startGpsService()
            } else {
                Toast.makeText(context, "Location permission is required for GPS tracking", Toast.LENGTH_LONG).show()
            }
        }
    )

    fun requestLocationPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            locationPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            startGpsService()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                requestLocationPermissions()
            } else {
                Toast.makeText(context, "Notification permission is required for foreground service", Toast.LENGTH_LONG).show()
            }
        }
    )

    fun toggleGps(enabled: Boolean) {
        settingsViewModel.setGpsEnabled(enabled)
        if (enabled) {
            if (!isSystemLocationEnabled()) {
                Toast.makeText(context, "Please enable Location in system settings", Toast.LENGTH_LONG).show()
                settingsViewModel.setGpsEnabled(false)
                return
            }
            
            if (!hasNotificationPermission()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    requestLocationPermissions()
                }
            } else if (!hasLocationPermissions()) {
                requestLocationPermissions()
            } else {
                startGpsService()
            }
        } else {
            stopGpsService()
        }
    }

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
            
            // 添加周期记录功能的开关
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            // GPS
            Text(
                text = "GPS Location Tracking",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable Background GPS",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = gpsEnabled,
                    onCheckedChange = { enabled ->
                        toggleGps(enabled)
                    }
                )
            }
            if (gpsEnabled) {
                var priorityExpanded by remember { mutableStateOf(false) }
                val priorityLabel = when (gpsPriority) {
                    "high_accuracy" -> "High Accuracy"
                    "balanced" -> "Balanced Power"
                    "low_power" -> "Low Power"
                    "no_power" -> "No Power (Passive)"
                    else -> "Balanced Power"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Location Priority",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Box {
                        TextButton(onClick = { priorityExpanded = true }) {
                            Text(priorityLabel)
                        }
                        DropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { priorityExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("High Accuracy") },
                                onClick = {
                                    settingsViewModel.setGpsPriority("high_accuracy")
                                    priorityExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Balanced Power") },
                                onClick = {
                                    settingsViewModel.setGpsPriority("balanced")
                                    priorityExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Low Power") },
                                onClick = {
                                    settingsViewModel.setGpsPriority("low_power")
                                    priorityExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("No Power (Passive)") },
                                onClick = {
                                    settingsViewModel.setGpsPriority("no_power")
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                var intervalExpanded by remember { mutableStateOf(false) }
                val intervalLabel = when (gpsIntervalMs) {
                        30_000L -> "30 seconds"
                        300_000L -> "5 minutes"
                        900_000L -> "15 minutes"
                        1_800_000L -> "30 minutes"
                        3_600_000L -> "60 minutes"
                        else -> "${gpsIntervalMs / 1000} seconds"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Update Interval",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Box {
                            TextButton(onClick = { intervalExpanded = true }) {
                                Text(intervalLabel)
                            }
                            DropdownMenu(
                                expanded = intervalExpanded,
                                onDismissRequest = { intervalExpanded = false }
                            ) {
                                listOf(5_000L to "5 seconds", 10_000L to "10 seconds", 30_000L to "30 seconds", 60_000L to "1 minute", 300_000L to "5 minutes").forEach { (ms, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            settingsViewModel.setGpsIntervalMs(ms)
                                            settingsViewModel.setGpsFastestIntervalMs(ms / 2)
                                            intervalExpanded = false
                                        }
                                    )
                                }
                            }
                        }
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
                Text("保存设置")
            }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { settingsViewModel.exportGpsData() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出GPS")
                }
                OutlinedButton(
                    onClick = { settingsViewModel.exportSyncLogs() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出日志")
                }
            }

            // 检查本地的日记图片
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showTradeSettings = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("trade设置")
                }
                OutlinedButton(
                    onClick = onOpenTradeRecord,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("trade记录")
                }
            }
            Text(
                text = "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
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
