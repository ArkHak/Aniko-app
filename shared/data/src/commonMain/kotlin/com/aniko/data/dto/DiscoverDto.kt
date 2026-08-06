package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `Interesting` — карточка `discover/interesting` (баннеры/подборки на главном экране
 * оригинального приложения; `action` — идентификатор цели перехода, семантика самого числа
 * (releaseId? collectionId? articleId? зависит от `type`) не расшифрована в этой сессии).
 *
 * Сверено вживую (`docs/api/samples/discover_interesting.json`) и статически
 * (`database/entity/release/Interesting.java`, `@JsonProperty` только на `is_hidden` —
 * Kotlin `isHidden` без аннотации сериализовался бы как `hidden`, поэтому там аннотация
 * обязательна; остальные поля без аннотаций и совпадают с именем свойства as-is).
 */
@Serializable
data class InterestingDto(
    val id: Int = 0,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val type: Int = 0,
    val action: String? = null,
    @SerialName("is_hidden") val isHidden: Boolean = false,
)
