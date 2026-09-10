package com.aniko.app.feature.player

import androidx.compose.runtime.Composable

/**
 * Яркость окна и громкость медиапотока — то, что меняют вертикальные жесты полноэкранного плеера
 * (P16.T9).
 *
 * Почему интерфейс, а не прямой вызов платформенных API в оверлее: обе величины живут в
 * платформенном слое, у которого нет общего KMP-аналога (Android — `Window`+`AudioManager`,
 * iOS — программно меняется только яркость экрана, громкость системы приложению недоступна,
 * Desktop — ни того, ни другого в переносимом виде). Оверлей знает только «сдвинь на N%» и
 * «сколько сейчас».
 *
 * Контракт единиц: все значения — доля `0f..1f`, а не проценты и не шаги кнопок громкости.
 *
 * `isSupported == false` означает «на этой платформе жестов нет»: оверлей в этом случае не
 * вешает обработчик вовсе (см. [rememberPlayerLevelGesture]), а не гоняет no-op команды.
 */
interface PlayerSystemLevels {
    /** `false` — платформа не даёт менять уровни из приложения (см. KDoc класса). */
    val isSupported: Boolean

    /** Текущая яркость как доля `0f..1f`: приложение её ещё не переопределяло — системная. */
    fun brightness(): Float

    /** Выставляет яркость ОКНА приложения (системную настройку не трогает). */
    fun setBrightness(value: Float)

    /** Текущая громкость медиапотока, `0f..1f`. */
    fun volume(): Float

    /** Меняет громкость медиапотока; системный UI громкости не показывается — свой индикатор. */
    fun setVolume(value: Float)

    /**
     * Возвращает яркость окна системе (Android: `BRIGHTNESS_OVERRIDE_NONE`). Обязателен к вызову
     * при уходе с экрана: иначе выставленная в плеере яркость осталась бы «прибитой» ко всему
     * приложению до его перезапуска.
     */
    fun release()
}

/** Заглушка для платформ и контекстов без доступа к окну (iOS/Desktop, не-Activity контекст). */
internal object UnsupportedPlayerSystemLevels : PlayerSystemLevels {
    override val isSupported: Boolean = false

    override fun brightness(): Float = 0f

    override fun setBrightness(value: Float) = Unit

    override fun volume(): Float = 0f

    override fun setVolume(value: Float) = Unit

    override fun release() = Unit
}

/**
 * Контроллер уровней, привязанный к жизненному циклу композиции: на `onDispose` возвращает
 * яркость окна системе (см. [PlayerSystemLevels.release]).
 */
@Composable
expect fun rememberPlayerSystemLevels(): PlayerSystemLevels

/**
 * Чувствительность вертикального жеста: доля уровня за одну высоту жестовой области.
 *
 * `1.4` вместо `1.0` выбрано по живому ощущению на эмуляторе: при `1.0` громкость/яркость в
 * полноэкранном плеере (где жестовая область — весь экран, а палец ходит на треть высоты)
 * приходилось «докатывать» двумя-тремя свайпами до нужного значения.
 */
internal const val PLAYER_LEVEL_DRAG_SENSITIVITY: Float = 1.4f

/**
 * Новый уровень после вертикального сдвига на [dragAmountPx] в области высотой [heightPx].
 *
 * Знак: `dragAmount` в Compose растёт ВНИЗ, а уровень обязан расти ВВЕРХ (свайп вверх —
 * громче/ярче), отсюда минус. Вынесено отдельной чистой функцией, чтобы знак и кламп были
 * покрыты юнит-тестом, а не проверялись только глазами на устройстве.
 */
internal fun playerLevelAfterDrag(
    current: Float,
    dragAmountPx: Float,
    heightPx: Float,
): Float {
    if (heightPx <= 0f) return current.coerceIn(0f, 1f)
    return (current - dragAmountPx / heightPx * PLAYER_LEVEL_DRAG_SENSITIVITY).coerceIn(0f, 1f)
}
