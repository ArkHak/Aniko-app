package com.aniko.model

/**
 * UI-сортировка каталога (P7.T3-T6, `docs/REELWAVE_PLAN.md`). Маппится на числовые константы
 * `FilterRequestDto.SORT_*` в `shared/data` (см. `FilterRequestDto.Companion`) — здесь
 * намеренно только 4 варианта из мокапа, не все 8 значений API (asc-варианты не показаны в UI).
 */
enum class CatalogSort {
    RECENTLY_UPDATED,
    RATING,
    YEAR,
    POPULARITY,
}

/**
 * Тип контента каталога (P16.T1) — верхние табы «Аниме»/«Дунхуа».
 *
 * В Anixart 10 это ровно два таба главной, и оба — пресет по стране релиза (декомпилированный
 * `HomeFragment`: `tab_title_anime` → `country_japan`, `tab_title_donghua` → `country_china`),
 * третьего варианта «всё» там нет. Страновая строка API (русскоязычная, как и значения
 * `AnixGenres`) живёт в `shared/data` — рядом с остальным маппингом в `FilterRequestDto`, чтобы
 * `shared/model` не знал про значения бэкенда.
 */
enum class CatalogContentType {
    ANIME,
    DONGHUA,
}

/**
 * Состояние фильтра каталога — UI-слой над `FilterRequestDto` (`POST filter/{page}`).
 * Маппинг в `FilterRequestDto` — в `shared/data` (`ReleaseRepository.filterPaginator`), чтобы
 * `shared/model` не знал про DTO/сериализацию.
 */
data class CatalogFilter(
    /** Таб «Аниме/Дунхуа» (P16.T1) — уезжает в `FilterRequestDto.country`. */
    val contentType: CatalogContentType = CatalogContentType.ANIME,
    val sort: CatalogSort = CatalogSort.POPULARITY,
    /** `FilterRequestDto.statusId` — статус выхода релиза (`ReleaseStatus`-подобный id). */
    val statusId: Int? = null,
    val genres: Set<String> = emptySet(),
    /** `true` — исключить выбранные жанры (`FilterRequestDto.isGenresExcludeModeEnabled`). */
    val genresExcludeMode: Boolean = false,
    val startYear: Int? = null,
    val endYear: Int? = null,
)

/**
 * Справочник жанров каталога.
 *
 * У Anixart API нет отдельного эндпоинта-справочника жанров (см. P0.T3 в плане, секция
 * Catalog/Search) — `Release.genres` приходит плоской строкой через запятую, список возможных
 * значений нигде не перечислен. [popular] — захардкоженный набор популярных жанров ровно в том
 * виде, в котором `FilterRequestDto.genres` ожидает их отправлять на сервер (значения самого
 * API, не пользовательский UI-текст — переводить/локализовать нельзя, иначе фильтр перестанет
 * матчиться на бэкенде).
 */
object AnixGenres {
    // P2.T10: это не хардкод UI-текста, а значения самого API (параметр `genres` тела
    // `POST filter/{page}`) — сервер ожидает их ровно в таком, русскоязычном виде. Тот же
    // прецедент, что и `ReleaseMapper.toReleaseStatus` в `shared/data` (матчинг на русские
    // значения поля `status.name`), задокументированный в KDoc `ForbiddenCyrillicStringLiteral`
    // как легитимное исключение вне i18n-слоя.
    @Suppress("ForbiddenCyrillicStringLiteral")
    val popular: List<String> =
        listOf(
            "экшен",
            "приключения",
            "романтика",
            "драма",
            "комедия",
            "фэнтези",
            "школа",
            "спорт",
            "ужасы",
            "мистика",
            "сэйнэн",
            "сёнэн",
            "меха",
            "музыка",
            "повседневность",
            "психологическое",
            "сверхъестественное",
            "триллер",
        )
}
