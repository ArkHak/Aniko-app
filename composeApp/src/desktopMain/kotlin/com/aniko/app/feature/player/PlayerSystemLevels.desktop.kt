package com.aniko.app.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop-заглушка жестов уровней (P16.T9).
 *
 * Полноэкранный оверлей на Desktop вообще не рисуется: видео играет в системном браузере
 * (`controller.isSupported == false`, P8.T1), а яркость/громкость окна в переносимом виде
 * недоступны — `ComposeWindow` не даёт ни того, ни другого.
 */
@Composable
actual fun rememberPlayerSystemLevels(): PlayerSystemLevels = remember { UnsupportedPlayerSystemLevels }
