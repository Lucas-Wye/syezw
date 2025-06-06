package org.syezw

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel

import org.syezw.data.AppDatabase
import org.syezw.model.DiaryViewModel
import org.syezw.model.DiaryViewModelFactory
import org.syezw.model.TodoViewModel
import org.syezw.model.TodoViewModelFactory
import org.syezw.ui.theme.SyezwTheme

val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context) // Assuming shared database

    val todoViewModel: TodoViewModel = viewModel(
        factory = TodoViewModelFactory(database.todoTaskDao())
    )
    val diaryViewModel: DiaryViewModel = viewModel(
        factory = DiaryViewModelFactory(database.diaryDao()) // Use diaryEntryDao
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label // Use label for content description
                    )
                },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination })
            }
        }) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val screenModifier = Modifier.padding(innerPadding)

            when (currentDestination) {
                AppDestinations.HOME -> OurLove(modifier = screenModifier)
                AppDestinations.TODO -> TODOScreen(
                    viewModel = todoViewModel, modifier = screenModifier
                )

                AppDestinations.PHOTO -> PhotoScreen(modifier = screenModifier)
                AppDestinations.DIARY -> DiaryScreen( // Pass the DiaryViewModel
                    viewModel = diaryViewModel,
                    modifier = screenModifier,
                    onNavigateToEditEntry = { entryId ->
                        // Here you would navigate to an Add/Edit screen
                        // For simplicity now, we can show a dialog or another composable
                        // This part needs a proper navigation solution (Jetpack Navigation Compose recommended)
                        if (entryId != null) {
                            diaryViewModel.getEntryById(entryId)
                        } else {
                            diaryViewModel.clearInputFields() // For new entry
                        }
                        // For now, let's assume we show the add/edit UI within DiaryScreen or a dialog
                    })
            }
        }
    }
}