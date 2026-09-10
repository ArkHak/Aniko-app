package com.aniko.app.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * Android-реализация уровней жестов (P16.T9).
 *
 * Яркость — через `Window.attributes.screenBrightness` ТЕКУЩЕЙ Activity, а не через системную
 * настройку `Settings.System.SCREEN_BRIGHTNESS`: системная требует `WRITE_SETTINGS` (выдаётся
 * только вручную из настроек ОС) и меняла бы яркость всему устройству навсегда. Оконная яркость
 * действует, пока Activity на экране, и снимается в [release].
 *
 * Громкость — медиапоток (`STREAM_MUSIC`) через [AudioManager]. Именно поток, а не
 * `volumeControlStream`: второе лишь выбирает поток для кнопок устройства и не даёт выставить
 * значение из кода. `FLAG_SHOW_UI` намеренно НЕ передаётся — у нас свой индикатор, системная
 * плашка поверх видео была бы вторым UI на тот же жест.
 */
private class AndroidPlayerSystemLevels(
    private val activity: Activity,
) : PlayerSystemLevels {
    private val audioManager: AudioManager? =
        activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override val isSupported: Boolean = audioManager != null

    override fun brightness(): Float {
        val override = activity.window?.attributes?.screenBrightness ?: BRIGHTNESS_UNSET
        return if (override >= 0f) override else systemBrightness()
    }

    /**
     * Системная яркость нужна как стартовое значение первого жеста: до него override не выставлен
     * (`-1`), и свайп начинался бы всегда с нуля. Чтение `Settings.System` разрешений не требует.
     */
    private fun systemBrightness(): Float =
        runCatching {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(DEFAULT_BRIGHTNESS_PERCENT * SYSTEM_BRIGHTNESS_MAX)
            .toFloat()
            .div(SYSTEM_BRIGHTNESS_MAX)
            .coerceIn(MIN_BRIGHTNESS, 1f)

    override fun setBrightness(value: Float) {
        val window = activity.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceIn(MIN_BRIGHTNESS, 1f)
        window.attributes = attributes
    }

    override fun volume(): Float {
        val audio = audioManager ?: return 0f
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun setVolume(value: Float) {
        val audio = audioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (value.coerceIn(0f, 1f) * max).roundToInt()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, SILENT_VOLUME_FLAGS)
    }

    override fun release() {
        val window = activity.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attributes
    }
}

@Composable
actual fun rememberPlayerSystemLevels(): PlayerSystemLevels {
    val context = LocalContext.current
    // `remember(context)`: LocalContext в этом приложении — Activity (единственная Activity,
    // singleTask, см. `MainActivity`), но при её пересоздании (смена конфигурации вне
    // `configChanges`) ссылка меняется, и старый контроллер обязан быть заменён, иначе `release`
    // вернул бы яркость уже мёртвому окну.
    // `if/else`, а не `?.let { } ?: stub`: detekt считает результат `let` ненулевым и объявляет
    // обе ветки-фолбэка недостижимым кодом (тот же ложный вызов, что и в `EmbedVideoController`).
    val levels =
        remember(context) {
            val activity = context.findActivity()
            if (activity == null) UnsupportedPlayerSystemLevels else AndroidPlayerSystemLevels(activity)
        }
    // Возврат яркости окна системе обязателен: без него выставленное в плеере значение осталось бы
    // применённым ко всему приложению до перезапуска.
    DisposableEffect(levels) { onDispose { levels.release() } }
    return levels
}

/** Activity из любого контекста Compose: `LocalContext` бывает обёрнут (themed wrapper и т.п.). */
internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Пол — 2%: ниже окно становится практически чёрным и вернуть яркость свайпом вслепую нельзя. */
private const val MIN_BRIGHTNESS = 0.02f

private const val BRIGHTNESS_UNSET = -1f

private const val SYSTEM_BRIGHTNESS_MAX = 255f

private const val DEFAULT_BRIGHTNESS_PERCENT = 0.5f

/** `0` — без `FLAG_SHOW_UI`: системную плашку громкости заменяет наш индикатор. */
private const val SILENT_VOLUME_FLAGS = 0
