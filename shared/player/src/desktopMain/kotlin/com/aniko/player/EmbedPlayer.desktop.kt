package com.aniko.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI

/**
 * Desktop: полноценного WebView в Compose Desktop без тяжёлых зависимостей (JCEF/KCEF) нет —
 * осознанно упрощаем (согласовано в плане фазы 5, не тащим JCEF/KCEF): открываем URL в системном
 * браузере через [Desktop.browse], а на месте плеера показываем заглушку с кнопкой «Открыть ещё
 * раз» на случай, если пользователь закрыл вкладку браузера.
 *
 * Используется `BasicText` из `compose.foundation`, а не Material `Text`/`Button` — модуль
 * `:shared:player` намеренно не тянет зависимость на compose.material3 ради одной заглушки.
 */
@Composable
actual fun EmbedPlayerView(
    url: String,
    referer: String?,
    modifier: Modifier,
) {
    var reopenSignal by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, reopenSignal) {
        openUrlInSystemBrowser(url)
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                text = "Открыто в системном браузере",
                style = TextStyle(color = Color.White),
            )
            Box(
                modifier =
                    Modifier
                        .border(width = 1.dp, color = Color.White, shape = RoundedCornerShape(8.dp))
                        .clickable { reopenSignal++ }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                BasicText(
                    text = "Открыть ещё раз",
                    style = TextStyle(color = Color.White),
                )
            }
        }
    }
}

private fun openUrlInSystemBrowser(url: String) {
    // Untrusted URL из ответа API — открываем в браузере только http/https (см. код-ревью Фазы 5).
    if (!isSafeEmbedUrl(url)) return
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
