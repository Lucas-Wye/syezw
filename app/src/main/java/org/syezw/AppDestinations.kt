package org.syezw

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME(label = "Love", icon = Icons.Default.Favorite), 
    TODO(label = "Todo", icon = Icons.Default.Check),
    DIARY(label = "Diary", icon = Icons.Default.MailOutline),
    SETTINGS("Settings", Icons.Filled.Settings),
}