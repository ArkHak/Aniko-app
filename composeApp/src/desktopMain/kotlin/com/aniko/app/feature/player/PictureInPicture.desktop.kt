package com.aniko.app.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aniko.player.EmbedVideoController

/**
 * Desktop-заглушка PiP (P16.T8): на Desktop полноэкранный оверлей не рисуется вовсе — видео
 * играет в системном браузере, где окно «картинка в картинке» даёт сам браузер (P8.T1).
 */
@Composable
actual fun rememberPlayerPictureInPicture(controller: EmbedVideoController): PlayerPictureInPicture =
    remember { UnsupportedPlayerPictureInPicture }
