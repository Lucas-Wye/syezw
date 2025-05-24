package org.syezw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.syezw.Utils.daysFromTodayTo
import org.syezw.Utils.isSpecial
import org.syezw.ui.theme.DayColor
import org.syezw.ui.theme.LoveColor

@Composable
fun OurLove(modifier: Modifier = Modifier) {
    var days by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        days = daysFromTodayTo(2025, 4, 6)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
