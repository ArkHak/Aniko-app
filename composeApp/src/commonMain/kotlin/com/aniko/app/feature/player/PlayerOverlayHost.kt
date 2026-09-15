package com.aniko.app.feature.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Хост оверлея плеера ([PlayerOverlay]/[CompactPlayerChrome]) — Step 2/3 пересмотра P8.T1 (см.
 * журнал `docs/REELWAVE_PLAN.md`).
 *
 * На Android/iOS — чистый passthrough (`Box(modifier) { content() }`): [content] рисуется ровно
 * там же, где и раньше — прямым ребёнком той же композиции, что и `EmbedPlayerView`. Оборачивание
 * нужно только ради Desktop-actual — единая точка вызова в [PlayerScreen], а не `expect/actual`
 * ветвление внутри самого экрана (см. правило проекта "expect/actual вместо platform checks").
 *
 * **На Desktop** реальный `PlayerOverlay`/`CompactPlayerChrome` физически не может остаться
 * встроенным ребёнком главного окна — с VLCJ-рендером видео (`EmbedPlayer.desktop.kt`) видео-кадр
 * рисует ОТДЕЛЬНОЕ top-level Compose `Window` поверх главного (см. её KDoc: `CallbackMediaPlayerComponent`
 * активно перерисовывает себя поверх любого соседа в одном дереве компонентов — подтверждено
 * живыми спайками этой ветки, `compose.interop.blending`, чинящее это же для JCEF, для VLCJ не
 * работает), а значит инлайновый Compose-контент под ним в главном окне оказался бы физически
 * невидим и некликабелен. Desktop-actual поэтому реально мигрирует [content] в СВОЁ отдельное
 * top-level окно (транспарентное, `alwaysOnTop`, границы синхронизированы с [EmbedPlayerView] через
 * общий `trackScreenBounds` из `:shared:player`) — тот же принцип, подход (a) из отчёта задачи:
 * реальный `ComposeWindow` вместо ручной AWT-отрисовки, что и позволяет переиспользовать ГОТОВЫЕ
 * [PlayerOverlay]/[CompactPlayerChrome] один в один, без хендпорта контролов на `Graphics2D`.
 *
 * `:shared:player` не подключает эту функцию сам (`PlayerOverlay`/`CompactPlayerChrome` живут в
 * `composeApp`, не в `:shared:player` — граница модулей, см. их собственный KDoc) — `expect/actual`
 * заведён здесь, а не там, именно поэтому.
 */
@Composable
expect fun PlayerOverlayHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)
