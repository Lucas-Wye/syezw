package org.syezw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

import org.syezw.Utils.daysFromTodayTo
import org.syezw.ui.theme.SyezwTheme
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
                AppDestinations.HOME -> FavoritesScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.FAVORITES -> ToolsScreen(name="Master", modifier = Modifier.padding(innerPadding))
                AppDestinations.PROFILE -> ProfileScreen(modifier = Modifier.padding(innerPadding))
            }
        }

    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Love", Icons.Default.Favorite),
    FAVORITES("Tools", Icons.Default.Home),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun FavoritesScreen(modifier: Modifier = Modifier) {
    var days by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        days = daysFromTodayTo(2025, 4, 6).toString()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "在一起已经${days ?: "0"}天啦！",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = LoveColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ToolsScreen(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Text(text = "这里是个人中心", modifier = modifier)
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//     SyezwTheme {
//         Greeting("Android")
//     }
//}
