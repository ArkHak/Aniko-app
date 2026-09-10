package com.aniko.player

import androidx.compose.runtime.Composable

/**
 * Скрывает системные панели (статус-бар с часами/батареей + навигационную панель) на весь
 * период композиции вызывающей стороны, восстанавливая их при выходе (2026-09-10, ревью
 * замечание #3) — тот же паттерн, что и у [LockLandscapeOrientationEffect] (тот же вызывающий
 * блок `if (isFullscreen && !pipActive)` в `PlayerScreen.kt`): полноэкранный плеер должен
 * выглядеть иммерсивно (видео на весь экран, как у обычных видео-приложений), а не с системными
 * часами/батареей поверх.
 *
 * - Android: `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` на входе,
 *   `.show(...)` на выходе (`BEHAVIOR_SHOW_BARS_BY_SWIPE` — свайп от края временно возвращает
 *   панели, стандартное поведение системного видео-плеера), см. `HideSystemBars.android.kt`.
 * - iOS/Desktop: no-op — на iOS статус-бар в альбомной ориентации видео и так не показывается
 *   системой почти всегда (edge-to-edge), отдельного API в общем коде не заводим без
 *   зафиксированного визуального дефекта; Desktop-окно не имеет системных панелей вовсе.
 */
@Composable
expect fun HideSystemBarsEffect()
