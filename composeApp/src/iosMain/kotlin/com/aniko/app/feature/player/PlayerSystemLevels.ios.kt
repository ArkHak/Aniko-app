package com.aniko.app.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS-заглушка жестов уровней (P16.T9) — `isSupported == false`, обработчик в оверлее не вешается.
 *
 * Почему CUT, а не реализация:
 * - громкость: у приложения нет публичного API менять системную громкость (`MPVolumeView` —
 *   приватный трюк с `UISlider`, который Apple отклоняет при ревью и который ломается между
 *   версиями iOS);
 * - яркость: `UIScreen.mainScreen.brightness` пишет в СИСТЕМНУЮ яркость — после выхода из плеера
 *   устройство осталось бы с изменённой яркостью, что для жеста внутри плеера недопустимо.
 *
 * Жесты уровней в трекере заведены как Android-фича (Волна 4 «Плеер-продвинутый (Android)`); на
 * iOS остаются системные (шторка управления), как и было до этой задачи.
 */
@Composable
actual fun rememberPlayerSystemLevels(): PlayerSystemLevels = remember { UnsupportedPlayerSystemLevels }
