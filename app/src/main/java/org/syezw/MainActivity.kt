package org.syezw

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import org.syezw.data.AppDatabase
import org.syezw.model.DiaryViewModel
import org.syezw.model.DiaryViewModelFactory
import org.syezw.model.PeriodViewModel
import org.syezw.model.PeriodViewModelFactory
import org.syezw.model.SettingsViewModel
import org.syezw.model.SettingsViewModelFactory
import org.syezw.model.TodoViewModel
import org.syezw.model.TodoViewModelFactory
import org.syezw.preference.SettingsManager
import org.syezw.screen.DiaryScreen
import org.syezw.screen.OurLove
import org.syezw.screen.OurLoveViewModel
import org.syezw.screen.OurLoveViewModelFactory
import org.syezw.screen.PeriodTrackingScreen
import org.syezw.screen.SettingsScreen
import org.syezw.screen.TODOScreen
import org.syezw.screen.TradeRecordScreen
import org.syezw.ui.theme.SyezwTheme

val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic backup (every 30 days)
        val backupRequest = androidx.work.PeriodicWorkRequestBuilder<org.syezw.worker.BackupWorker>(
            30, java.util.concurrent.TimeUnit.DAYS
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MonthlyBackup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )

        setContent {
            SyezwTheme {
                SyezwAppScreen()
            }
        }
    }
}

@Composable
fun SyezwAppScreen() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showTradeRecordScreen by rememberSaveable { mutableStateOf(false) }
    val hideNavigation = showTradeRecordScreen
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val database = AppDatabase.getDatabase(context)
    val settingsManager = remember { SettingsManager(context.dataStore) }

    // --- ViewModel Instantiation ---
    val todoViewModel: TodoViewModel = viewModel(
        factory = TodoViewModelFactory(database.todoTaskDao(), settingsManager)
    )
    val diaryViewModel: DiaryViewModel = viewModel(
        factory = DiaryViewModelFactory(database.diaryDao(), settingsManager)
    )
    val ourLoveViewModel: OurLoveViewModel = viewModel(
        factory = OurLoveViewModelFactory(settingsManager)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(application, database, context.dataStore)
    )
    val periodViewModel: PeriodViewModel = viewModel(
        factory = PeriodViewModelFactory(database.periodDao())
    )

    // 从 SettingsViewModel 监听周期记录的开关状态
    val isPeriodTrackingEnabled by settingsViewModel.isPeriodTrackingEnabled.collectAsState()

    // 根据开关状态，动态地创建可见的导航目标列表
    val visibleDestinations = remember(isPeriodTrackingEnabled) {
        AppDestinations.entries.filter { destination ->
            destination != AppDestinations.PERIOD || isPeriodTrackingEnabled
        }
    }

    if (showTradeRecordScreen) {
        TradeRecordScreen(
            settingsViewModel = settingsViewModel,
            modifier = Modifier.fillMaxSize(),
            onBack = { showTradeRecordScreen = false }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                visibleDestinations.forEach { destination ->
                    item(
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination }
                    )
                }
            }) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                val screenModifier = Modifier.padding(innerPadding)

                when (currentDestination) {
                    AppDestinations.HOME -> OurLove(
                        viewModel = ourLoveViewModel, modifier = screenModifier
                    )

                    AppDestinations.TODO -> TODOScreen(
                        viewModel = todoViewModel, modifier = screenModifier
                    )

                    AppDestinations.DIARY -> DiaryScreen(
                        viewModel = diaryViewModel,
                        settingsViewModel = settingsViewModel,
                        modifier = screenModifier,
                        onNavigateToEditEntry = { entryId ->
                            if (entryId != null) {
                                diaryViewModel.getEntryById(entryId)
                            } else {
                                diaryViewModel.clearInputFields()
                            }
                        })

                    AppDestinations.PERIOD -> PeriodTrackingScreen(
                        viewModel = periodViewModel, modifier = screenModifier
                    )

                AppDestinations.SETTINGS -> SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    modifier = screenModifier,
                    onOpenTradeRecord = { showTradeRecordScreen = true }
                )
            }
        }
    }
}
}
