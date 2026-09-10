package com.aniko.app.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aniko.player.EmbedVideoController

/**
 * iOS-заглушка PiP (P16.T8) — `isSupported == false`, кнопка PiP в оверлее не показывается.
 *
 * Почему CUT: программный вход в PiP на iOS возможен только через `AVPictureInPictureController`,
 * который работает над `AVPlayerLayer` — то есть над видео, которым владеет приложение. Наше видео
 * — чужой `<video>` внутри `WKWebView`: система умеет уводить его в PiP по своему жесту (это
 * поведение `WKWebView` по умолчанию, `allowsPictureInPictureMediaPlayback`), но ни войти в PiP из
 * Compose, ни нарисовать там свои кнопки нельзя. Честная заглушка вместо неработающей кнопки —
 * тот же принцип, что у `PlayerPipAutoEnter` (см. `REELWAVE_PLAN.md`, отчёт P16.T8).
 */
@Composable
actual fun rememberPlayerPictureInPicture(controller: EmbedVideoController): PlayerPictureInPicture =
    remember { UnsupportedPlayerPictureInPicture }
