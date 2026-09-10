package com.aniko.ui.theme

import androidx.compose.runtime.Composable

/**
 * Синхронизирует цвет иконок системных панелей (Android: часы/батарея/сеть в статус-баре, а
 * также кнопки в навигационной панели) с текущей темой приложения (2026-09-10, ревью
 * замечание #4).
 *
 * До этой правки `MainActivity.onCreate()` вызывал `enableEdgeToEdge()` без аргументов ОДИН раз
 * при старте — та функция резолвит стиль иконок из системной настройки тёмного режима ОС на
 * момент запуска и больше никогда не переоценивает его. Переключение темы в `AnixThemePicker`
 * корректно перекрашивало фон/контент через [AppTheme], но иконки статус-бара оставались
 * замороженными на исходном (системном) значении — на тёмном фоне могли остаться тёмные же
 * иконки, невидимые на глаз.
 *
 * Вызывается из [AppTheme] с тем же [darkTheme], что определяет цветовую схему — единая точка
 * правды, без риска рассинхронизации с UI-содержимым. Android — реальный эффект
 * (`WindowInsetsControllerCompat.isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars`,
 * см. `SystemBarStyle.android.kt`); iOS/Desktop — no-op (статус-бара в этом смысле нет:
 * iOS сам адаптирует стиль под контент, Desktop-окно system-bar не имеет).
 */
@Composable
expect fun SystemBarStyleEffect(darkTheme: Boolean)
