package com.aniko.player

/**
 * Открывать ли плеер сразу в полноэкранном режиме (`PlayerUiState.isFullscreen = true`) при входе
 * на экран плеера (живой фидбек пользователя 2026-09-17, Desktop).
 *
 * - Desktop: `true` — «fullscreen» там не захват экрана ОС, а раскладка видео на всю высоту окна
 *   приложения (`PlayerScreen` — `videoHeight = screenHeight`; отдельные окна видео/оверлея
 *   VLCJ-архитектуры синхронизируются по bounds автоматически). В компактном режиме на десктопе
 *   кнопки озвучки/скорости/качества и навигации по сериям не видны без лишнего клика.
 * - Android/iOS: `false` — телефон открывает плеер в компактном chrome (P13-дизайн), полноэкранный
 *   режим там включается кнопкой/жестом и ведёт себя иммерсивно (см. [HideSystemBarsEffect],
 *   [LockLandscapeOrientationEffect]).
 */
expect fun playerOpensFullscreen(): Boolean
