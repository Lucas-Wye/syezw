package org.syezw

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents the different primary destinations within the application.
 * Each destination has a user-facing [label] and an [icon] for UI representation.
 */
enum class AppDestinations(
    val label: String, // Or use @StringRes for localization
    val icon: ImageVector,
) {
    HOME(label = "Love", icon = Icons.Default.Favorite), // Consistent named arguments

    TODO(label = "Todo", icon = Icons.Default.Check), // Consistent naming: "Todo"

    PHOTO(label = "Photo", icon = Icons.Default.AccountBox),

    DIARY(label = "Diary", icon = Icons.Default.MailOutline)
}
