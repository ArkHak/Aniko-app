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
 * осознанно упрощаем (согласовано в плане фазы 5/8: JCEF физически рисуется поверх Compose,
 * баг JetBrains CMP-6001, — не тащим). Открываем не сам исходный [url] напрямую, а локальную
 * обёртку [KodikProxyServer] в системном браузере через [Desktop.browse] — источники вроде
 * Kodik сверяют в своём JS, что страница загружена внутри `<iframe>` (см. KDoc
 * `EmbedPlayer.android.kt`/`KodikProxyServer.kt`), иначе рисуют «данной страницы не существует»
 * независимо от Referer. [referer] сознательно не используется: живой тест (2026-08-23) показал,
 * что попытка подделать его на сервере (переотдавая содержимое страницы с локального порта)
 * ломает собственные same-origin XHR-запросы страницы (CORS) — простой `<iframe src="url">`
 * без переотдачи содержимого работает корректно и без Referer. На месте плеера показываем
 * заглушку с кнопкой «Открыть ещё раз» на случай, если пользователь закрыл вкладку браузера.
 *
 * Используется `BasicText` из `compose.foundation`, а не Material `Text`/`Button` — модуль
 * `:shared:player` намеренно не тянет зависимость на compose.material3 ради одной заглушки.
 */
@Composable
actual fun EmbedPlayerView(
    url: String,
    // Не используется намеренно — см. KDoc класса выше и KDoc `KodikProxyServer`.
    @Suppress("UNUSED_PARAMETER") referer: String?,
    // Не используется намеренно: на Desktop нет видео-поверхности под контролем приложения,
    // JS-мост здесь физически некуда ставить — см. KDoc `EmbedVideoController` (desktopMain).
    @Suppress("UNUSED_PARAMETER") controller: EmbedVideoController?,
    modifier: Modifier,
) {
    var reopenSignal by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, reopenSignal) {
        val wrapperUrl = KodikProxyServer.wrapperUrl(url)
        openUrlInSystemBrowser(wrapperUrl)
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // P2.T10: намеренно английский текст, не через Strings/Lyricist. `:shared:player`
            // не зависит от `:shared:ui` (там живёт i18n-слой) — заводить эту зависимость ради
            // двух подписей заглушки не входит в объём этой задачи (см. KDoc класса выше:
            // desktop-плеер — уже задокументированная упрощённая заглушка Фазы 5, полноценный
            // WebView не тащим). Полная локализация этого экрана — будущая задача.
            BasicText(
                text = "Opened in the system browser",
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
                    text = "Open again",
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
