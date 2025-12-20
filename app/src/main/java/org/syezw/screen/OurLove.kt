package org.syezw.screen

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.syezw.Utils
import org.syezw.Utils.daysFromTodayTo
import org.syezw.Utils.isSpecial
import org.syezw.preference.SettingsManager
import org.syezw.ui.theme.DayColor
import org.syezw.ui.theme.LoveColor


@Composable
fun OurLove(
    viewModel: OurLoveViewModel, modifier: Modifier = Modifier
) {
    var days by rememberSaveable { mutableStateOf<Long?>(null) }
    val dateTogether by viewModel.currentDateTogether.collectAsState()
    val bgImageUri by viewModel.loveBgImageUri.collectAsState()
    val bgEnabled by viewModel.loveBgEnabled.collectAsState()

    LaunchedEffect(Unit) {
        val dateComponents = Utils.extractDateComponents(dateTogether)
        days = if (dateComponents != null) {
            daysFromTodayTo(dateComponents.first, dateComponents.second, dateComponents.third)
        } else {
            daysFromTodayTo(2025, 4, 6)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 显示背景图片（如果启用）
            if (bgEnabled && bgImageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(bgImageUri)),
                    contentDescription = "Love Background",
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 文字内容
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
}

class OurLoveViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    val currentDateTogether: StateFlow<String> = settingsManager.dateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsManager.DEFAULT_DATE_VALUE
    )

    val loveBgImageUri: StateFlow<String?> = settingsManager.loveBgImageUriFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val loveBgEnabled: StateFlow<Boolean> = settingsManager.loveBgEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
}

class OurLoveViewModelFactory(private val settingsManager: SettingsManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OurLoveViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return OurLoveViewModel(settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}