package com.aniko.app.feature.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aniko.player.EmbedVideoController
import com.aniko.ui.i18n.LocalStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-реализация PiP (P16.T8).
 *
 * Механика:
 * - вход — `Activity.enterPictureInPictureMode(params)`;
 * - кнопки окошка — [RemoteAction] c `PendingIntent.getBroadcast` в наш же `BroadcastReceiver`,
 *   зарегистрированный динамически. Не `MediaSession`: сессия нужна для системного «сейчас играет»
 *   и внешних контролов, а PiP-действия — часть самого окна Activity;
 * - приёмник зарегистрирован `RECEIVER_NOT_EXPORTED` (API 33+) и `Intent` адресуется нашему
 *   пакету: чужое приложение не должно уметь «нажимать» наши кнопки;
 * - `setAutoEnterEnabled(true)` (API 31+) — сворачивание приложения само уводит плеер в окошко.
 *   До 31 такой настройки нет, там вход делает `Activity.onUserLeaveHint` через
 *   [PlayerPipAutoEnter] (см. `MainActivity`).
 *
 * Иконки действий — системные (`android.R.drawable.ic_media_*`): рисовать свои ради трёх кнопок
 * в окошке смысла нет, а системные гарантированно есть на любом устройстве.
 */
@Suppress("TooManyFunctions") // 5 методов контракта + 7 приватных шагов PiP-параметров.
private class AndroidPictureInPicture(
    private val activity: ComponentActivity,
    private val controller: EmbedVideoController,
    private val strings: PlayerPipStrings,
) : PlayerPictureInPicture {
    private val activeFlow = MutableStateFlow(activity.isInPictureInPictureMode)
    private var autoEnterEnabled = false
    private var playing = false
    private var receiverRegistered = false

    private val controlsReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.getIntExtra(PIP_CONTROL_EXTRA, PIP_CONTROL_NONE)) {
                    PlayerPipControl.TOGGLE_PLAY.ordinal -> controller.togglePlayPause()
                    PlayerPipControl.REWIND.ordinal -> controller.seekBy(-PIP_SEEK_STEP_MS)
                    PlayerPipControl.FORWARD.ordinal -> controller.seekBy(PIP_SEEK_STEP_MS)
                }
            }
        }

    override val isSupported: Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override val isActive: StateFlow<Boolean> = activeFlow

    init {
        registerControlsReceiver()
    }

    /** Смена PiP-режима приходит из `MainActivity.onPictureInPictureModeChanged` (см. её KDoc). */
    fun onPictureInPictureModeChanged(active: Boolean) {
        activeFlow.value = active
    }

    override fun setAutoEnterEnabled(enabled: Boolean) {
        autoEnterEnabled = enabled
        // Два механизма, и оба нужны:
        // - Android 12+ — `setAutoEnterEnabled(true)` в параметрах: система сама уводит Activity в
        //   PiP при сворачивании (живая проверка на Pixel_6_Pro_API_33: `applyParams autoEnter=true
        //   ok=true`, окно появляется без нашего кода);
        // - Android 11 и ниже — такой настройки нет, вход делает `onUserLeaveHint` через
        //   [PlayerPipAutoEnter] (см. `MainActivity`). Хук остаётся и на новых версиях: он
        //   страхует случай, когда система авто-вход не выполнила (например, поверх идёт
        //   системный диалог), а [enterIfIdle] не даёт войти дважды.
        PlayerPipAutoEnter.onUserLeaveHint = if (enabled) ({ enterIfIdle() }) else null
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Параметры надо переприменить: иначе флаг вступит в силу только со следующего входа.
            applyParams()
        }
    }

    /** Вход в PiP, если его ещё нет: `onUserLeaveHint` и авто-вход системы могут сработать оба. */
    private fun enterIfIdle() {
        if (activity.isInPictureInPictureMode) return
        enter()
    }

    override fun setPlaying(playing: Boolean) {
        if (this.playing == playing) return
        this.playing = playing
        if (isSupported && activity.isInPictureInPictureMode) applyParams()
    }

    override fun enter() {
        if (!isSupported) return
        // `runCatching`: вход в PiP может быть отклонён системой (например, окно занято другим
        // приложением в PiP) — это не ошибка нашего кода и не должно ронять плеер.
        runCatching { activity.enterPictureInPictureMode(params()) }
    }

    private fun applyParams() {
        if (!isSupported) return
        runCatching { activity.setPictureInPictureParams(params()) }
    }

    private fun params(): PictureInPictureParams {
        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(Rational(PIP_ASPECT_WIDTH, PIP_ASPECT_HEIGHT))
                .setActions(actions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
        }
        return builder.build()
    }

    private fun actions(): List<RemoteAction> =
        listOf(
            remoteAction(
                icon = android.R.drawable.ic_media_rew,
                title = strings.seekBackward,
                control = PlayerPipControl.REWIND,
            ),
            remoteAction(
                icon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                title = if (playing) strings.pause else strings.play,
                control = PlayerPipControl.TOGGLE_PLAY,
            ),
            remoteAction(
                icon = android.R.drawable.ic_media_ff,
                title = strings.seekForward,
                control = PlayerPipControl.FORWARD,
            ),
        )

    private fun remoteAction(
        icon: Int,
        title: String,
        control: PlayerPipControl,
    ): RemoteAction =
        RemoteAction(
            Icon.createWithResource(activity, icon),
            title,
            title,
            PendingIntent.getBroadcast(
                activity,
                control.ordinal,
                Intent(PIP_CONTROL_ACTION)
                    .setPackage(activity.packageName)
                    .putExtra(PIP_CONTROL_EXTRA, control.ordinal),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    private fun registerControlsReceiver() {
        if (!isSupported) return
        val filter = IntentFilter(PIP_CONTROL_ACTION)
        val registered =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.registerReceiver(controlsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    activity.registerReceiver(controlsReceiver, filter)
                }
            }.isSuccess
        receiverRegistered = registered
    }

    fun dispose() {
        if (receiverRegistered) {
            runCatching { activity.unregisterReceiver(controlsReceiver) }
            receiverRegistered = false
        }
        PlayerPipAutoEnter.onUserLeaveHint = null
    }
}

/** Подписи PiP-действий, снятые из локализованных строк в композиции (см. `remember...`). */
internal data class PlayerPipStrings(
    val play: String,
    val pause: String,
    val seekBackward: String,
    val seekForward: String,
)

@Composable
actual fun rememberPlayerPictureInPicture(controller: EmbedVideoController): PlayerPictureInPicture {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val labels =
        remember(strings) {
            PlayerPipStrings(
                play = strings.playerPlay,
                pause = strings.playerPause,
                seekBackward = strings.playerSeekBackward,
                seekForward = strings.playerSeekForward,
            )
        }
    // Ключи `remember`: контекст (Activity может пересоздаться) и сам мост — контроллер PiP
    // держит ссылку на него, чтобы PiP-кнопки попадали в JS-команды.
    // `if/else`, а не `?.let { } ?: stub` — см. комментарий в `PlayerSystemLevels.android.kt`
    // про ложный detekt на `let`-результате.
    val pip: PlayerPictureInPicture =
        remember(context, labels, controller) {
            val activity = context.findActivity() as? ComponentActivity
            if (activity == null) {
                // Контекст не Activity: PiP-окна нет вовсе (заглушка из commonMain).
                UnsupportedPlayerPictureInPicture
            } else {
                AndroidPictureInPicture(activity, controller, labels)
            }
        }
    DisposableEffect(pip) {
        // Подписка на смену PiP-режима идёт через `MainActivity` (framework-колбэк), поэтому её
        // регистрация обязана сниматься вместе с контроллером — иначе Activity осталась бы
        // ссылаться на disposed-инстанс.
        val androidPip = pip as? AndroidPictureInPicture
        PlayerPipModeBridge.onChanged =
            androidPip?.let { instance ->
                { active -> instance.onPictureInPictureModeChanged(active) }
            }
        onDispose {
            PlayerPipModeBridge.onChanged = null
            androidPip?.setAutoEnterEnabled(false)
            androidPip?.dispose()
        }
    }
    return pip
}

/**
 * Мост «система сворачивает приложение» → «войти в PiP» для Android < 12, где у PiP-параметров нет
 * `setAutoEnterEnabled`. Подписывается сам контроллер PiP, дёргает — `MainActivity.onUserLeaveHint`
 * (см. её KDoc): единственный официальный колбэк «пользователь уходит с экрана домой/в задачи».
 */
internal object PlayerPipAutoEnter {
    var onUserLeaveHint: (() -> Unit)? = null
}

/**
 * Второй мост к `MainActivity`: смена PiP-режима (`onPictureInPictureModeChanged`) — framework-колбэк
 * Activity, доступный только ей. Через него оверлей узнаёт, что пора спрятаться и оставить видео.
 */
internal object PlayerPipModeBridge {
    var onChanged: ((Boolean) -> Unit)? = null
}

private const val PIP_CONTROL_ACTION = "com.aniko.app.action.PIP_CONTROL"

private const val PIP_CONTROL_EXTRA = "control"

private const val PIP_CONTROL_NONE = -1
