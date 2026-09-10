package com.aniko.data.catalogfilter

import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
import com.aniko.network.AnixJson
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Локальная «Моя вкладка» каталога (P16.T2) — один сохранённый набор фильтров.
 *
 * Почему ОДИН, а не список: в Anixart 10 «Моя вкладка» — это одна пользовательская вкладка главной
 * (`hint_custom_tab_title` / `my_custom_filter_tab`), а не библиотека пресетов. Делать список
 * значило бы придумать фичу, которой в сверяемом продукте нет.
 *
 * Хранение: [Settings] (plaintext, как `LocalVoicePinStore`) + JSON через общий [AnixJson], а не
 * плоские ключи: набор фильтров — структура с необязательными полями (год-диапазон, жанры), и
 * раскладывать её по ключам значило бы вручную поддерживать совместимость формата. DTO объявлен
 * здесь, а не в `shared/model`: модель намеренно без зависимостей и без `kotlinx.serialization`
 * (см. `shared/model/build.gradle.kts`).
 */
class LocalCatalogFilterStore(
    private val settings: Settings,
) {
    private val myTabFlow = MutableStateFlow(readMyTab())

    /** Текущая сохранённая вкладка или `null`, если пользователь её ещё не сохранял. */
    fun myTab(): Flow<CatalogFilter?> = myTabFlow.asStateFlow()

    /** Сохранить [filter] как «Мою вкладку» (перезаписывает прежнюю). */
    suspend fun save(filter: CatalogFilter) {
        settings.putString(KEY_MY_TAB, AnixJson.encodeToString(SavedCatalogFilterDto.serializer(), filter.toDto()))
        myTabFlow.value = filter
    }

    /** Забыть сохранённую вкладку. */
    suspend fun clear() {
        settings.remove(KEY_MY_TAB)
        myTabFlow.value = null
    }

    private fun readMyTab(): CatalogFilter? =
        settings
            .getStringOrNull(KEY_MY_TAB)
            ?.let { raw ->
                // Битое/устаревшее значение (например, из версии с другим набором полей) не должно
                // ронять каталог: `ignoreUnknownKeys` в AnixJson уже прощает лишние поля, а любой
                // сбой разбора здесь честно деградирует до «вкладки нет».
                runCatching { AnixJson.decodeFromString(SavedCatalogFilterDto.serializer(), raw) }.getOrNull()
            }?.toDomain()

    private companion object {
        const val KEY_MY_TAB = "catalog.my_tab"
    }
}

@Serializable
private data class SavedCatalogFilterDto(
    val contentType: String = CatalogContentType.ANIME.name,
    val sort: String = CatalogSort.POPULARITY.name,
    val statusId: Int? = null,
    val genres: List<String> = emptyList(),
    val genresExcludeMode: Boolean = false,
    val startYear: Int? = null,
    val endYear: Int? = null,
)

private fun CatalogFilter.toDto(): SavedCatalogFilterDto =
    SavedCatalogFilterDto(
        contentType = contentType.name,
        sort = sort.name,
        statusId = statusId,
        genres = genres.toList(),
        genresExcludeMode = genresExcludeMode,
        startYear = startYear,
        endYear = endYear,
    )

private fun SavedCatalogFilterDto.toDomain(): CatalogFilter =
    CatalogFilter(
        // Неизвестное значение перечисления (набор поменялся между версиями) трактуется как
        // дефолт, а не как исключение: вкладка остаётся рабочей, теряется только один параметр.
        contentType = CatalogContentType.entries.firstOrNull { it.name == contentType } ?: CatalogContentType.ANIME,
        sort = CatalogSort.entries.firstOrNull { it.name == sort } ?: CatalogSort.POPULARITY,
        statusId = statusId,
        genres = genres.toSet(),
        genresExcludeMode = genresExcludeMode,
        startYear = startYear,
        endYear = endYear,
    )
