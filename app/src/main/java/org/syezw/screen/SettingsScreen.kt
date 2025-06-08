package org.syezw.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import org.syezw.model.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel, modifier: Modifier = Modifier
) {
    val currentAuthor by settingsViewModel.defaultAuthor.collectAsState()
    val currentDateTogether by settingsViewModel.dateTogether.collectAsState()

    var authorInput by remember(currentAuthor) { mutableStateOf(currentAuthor) }
    var dateTogetherInput by remember(currentDateTogether) { mutableStateOf(currentDateTogether) }
    var dateError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold() { paddingValues ->
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
                        SimpleDateFormat(it, Locale.getDefault()) // Test format
                        null // No error
                    } catch (e: IllegalArgumentException) {
                        "Invalid date pattern"
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
            Button(
                onClick = {
                    if (dateError == null) { // Only save if format is valid
                        settingsViewModel.updateDefaultAuthor(authorInput)
                        settingsViewModel.updateDate(dateTogetherInput)
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