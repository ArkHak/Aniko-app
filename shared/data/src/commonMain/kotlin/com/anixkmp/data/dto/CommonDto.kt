package com.anixkmp.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Общая обёртка Anixart: любой ответ несёт `code`, где `0` — успех.
 *
 * Сверено вживую: все запросы части A (`discover/watching`, `discover/interesting`,
 * `release/{id}`, `search/releases/{page}`) с валидным токеном вернули `code: 0`.
 * `[TODO: verify live]` — набор ненулевых кодов (кроме путей `auth/…`, см. `LoginViewModel`)
 * пока не подтверждён, т.к. не было воспроизведено ни одной ошибки сервера в этой сессии.
 */
interface ApiCodeAware {
    val code: Int
}

/**
 * `PageableResponse<T>` — постраничная выдача.
 *
 * Сверено вживую (`docs/api/samples/discover_watching_page0.json`,
 * `.../search_releases_page0_no_api_version_header.json`) и статически
 * (`network/response/PageableResponse.java`, поля с `@JsonProperty`): `content`,
 * `current_page`, `total_page_count`, `total_count` — все имена подтверждены 1:1.
 * В decompiled-модели `total_count` — `Long`, в живых сэмплах наблюдались только
 * значения, укладывающиеся в Int32 (например `88`); оставлено как `Int?` ради простоты,
 * `[TODO: verify live]`, если каталог когда-нибудь перевалит за ~2 млрд элементов.
 */
@Serializable
data class PageableResponseDto<T>(
    override val code: Int = 0,
    val content: List<T> = emptyList(),
    @SerialName("current_page") val currentPage: Int = 0,
    @SerialName("total_page_count") val totalPageCount: Int = 0,
    @SerialName("total_count") val totalCount: Int? = null,
) : ApiCodeAware

/** Ответ без полезной нагрузки (add/delete в списках, watch/unwatch и т.п.). */
@Serializable
data class SimpleResponseDto(
    override val code: Int = 0,
) : ApiCodeAware
