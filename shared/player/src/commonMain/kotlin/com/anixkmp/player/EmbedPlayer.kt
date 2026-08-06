package com.anixkmp.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Простой embed-рендерер для MVP плеера.
 *
 * Архитектурное решение (Фаза 5, зафиксировано пользователем, не пересматривать):
 * ВСЁ воспроизведение идёт через встраиваемую веб-страницу источника (Kodik/Sibnet/...),
 * независимо от того, отдаёт ли сервер прямой поток или iframe-страницу
 * (см. `EpisodeTargetDto.iframe` в `:shared:data`). Нативный [PlayerController]/[VideoSurface]
 * из этого же модуля НЕ используются здесь — это отдельный, более простой механизм,
 * задел на нативное воспроизведение остаётся нетронутым на будущее.
 *
 * Платформенные реализации:
 * - Android — `android.webkit.WebView` в `AndroidView`.
 * - iOS — `WKWebView` в `UIKitView`.
 * - Desktop — полноценного WebView в Compose Desktop без тяжёлых зависимостей (JCEF/KCEF) нет,
 *   поэтому осознанно упрощаем: открываем URL в системном браузере и показываем заглушку.
 */
@Composable
expect fun EmbedPlayerView(url: String, modifier: Modifier = Modifier)

/**
 * `true`, если [url] безопасно передавать в системный WebView/браузер — только `http`/`https`.
 *
 * `url` приходит из ответа Anixart API (`episode/target`) и формально untrusted: без этой
 * проверки платформенные реализации передали бы схему как есть в `WebView.loadUrl` /
 * `WKWebView.loadRequest` / `Desktop.browse`, что для `javascript:`/`file:`/подобных схем
 * потенциально небезопасно (см. код-ревью Фазы 5).
 */
fun isSafeEmbedUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
