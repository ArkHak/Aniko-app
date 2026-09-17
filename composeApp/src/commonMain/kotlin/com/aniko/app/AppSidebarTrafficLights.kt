package com.aniko.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Кнопки traffic lights (close/minimize/zoom) в шапке постоянного сайдбара на Desktop
 * (desktop-артборд мокапа Claude Design, 2026-09-15: сайдбар 232px, блок кнопок с
 * `padding:6px 8px 20px` над брендом). Подключается из `App.kt` через слот
 * `sidebarHeader` [com.aniko.ui.adaptive.AdaptiveScaffold] — на `Expanded` сайдбар виден
 * всегда, поэтому кнопки управления окном живут в нём, а не только в draggable-полосе
 * `AnikoDesktopChrome` (та остаётся для перетаскивания `undecorated`-окна).
 *
 * На Android/iOS оконного хрома нет — actual пустой (слот сайдбара там всё равно не
 * рендерится: сайдбар существует только на `Expanded`, а `Expanded` на этих платформах
 * встречается лишь у десктопных окон; пустой actual — страховка контракта, не рабочий путь).
 *
 * Параметр [modifier] без дефолта намеренно: `expect`-функции не поддерживают значения
 * по умолчанию, вызывающая сторона передаёт padding мокапа явно.
 */
@Composable
expect fun AppSidebarTrafficLights(modifier: Modifier)
