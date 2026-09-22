package com.aniko.data.librarypreferences

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище пользовательских настроек экрана «Мои списки».
 *
 * Тот же паттерн, что [com.aniko.data.playerpreferences.PlayerPreferencesStore]: значение не
 * секретное, поэтому обычный [Settings] (plaintext), синглтон в `dataModule`, чтение — реактивным
 * [StateFlow]. Сейчас здесь одна настройка — [viewMode] (вид «Список»/«Сетка постеров»).
 *
 * Выбор общий для всех вкладок экрана и всех размеров окна и переживает перезапуск приложения.
 * Значение хранится строкой (`"list"`/`"grid"`), а не порядковым номером enum'а: перестановка или
 * переименование констант [LibraryViewMode] не должна молча превращать сохранённый выбор в другой.
 */
class LibraryPreferencesStore(
    private val settings: Settings,
) {
    private val _viewMode = MutableStateFlow(decode(settings.getStringOrNull(KEY_VIEW_MODE)))

    /**
     * Явно выбранный вид либо `null`, если пользователь ничего не выбирал (или в `Settings` лежит
     * неизвестное значение). `null` — не «список» и не «сетка», а «использовать вид по умолчанию
     * для текущего размера окна»: этот выбор — забота экрана, `shared/data` про окна не знает.
     */
    val viewMode: StateFlow<LibraryViewMode?> = _viewMode.asStateFlow()

    /** [mode] `null` сбрасывает выбор обратно на «по умолчанию» — запись удаляется, а не хранится. */
    fun setViewMode(mode: LibraryViewMode?) {
        if (mode == null) {
            settings.remove(KEY_VIEW_MODE)
        } else {
            settings.putString(KEY_VIEW_MODE, encode(mode))
        }
        _viewMode.value = mode
    }

    private companion object {
        const val KEY_VIEW_MODE = "library.view_mode"
        const val VALUE_LIST = "list"
        const val VALUE_GRID = "grid"

        fun encode(mode: LibraryViewMode): String =
            when (mode) {
                LibraryViewMode.List -> VALUE_LIST
                LibraryViewMode.Grid -> VALUE_GRID
            }

        fun decode(raw: String?): LibraryViewMode? =
            when (raw) {
                VALUE_LIST -> LibraryViewMode.List
                VALUE_GRID -> LibraryViewMode.Grid
                else -> null
            }
    }
}
