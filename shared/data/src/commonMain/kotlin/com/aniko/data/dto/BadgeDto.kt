package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Один значок из коллекции УЖЕ ПОЛУЧЕННЫХ пользователем бейджей (`profile/preference/badge/all/{page}`,
 * `PageableResponseDto<BadgeDto>.content`).
 *
 * Сверено вживую 2026-08-23 (эмулятор `Pixel_6_Pro_API_33`, `GET profile/preference/badge/all/0`
 * с реальным токеном — `docs/api/samples/profile_preference_badge_all_page0.json`, токен и часть
 * персональных полей вложенного `profile` вырезаны вручную перед коммитом): форма ответа совпала
 * 1:1 с decompiled `database/entity/profile/Badge.java` — `id`, `type`, `name`, `image_url`,
 * `timestamp`, расхождений не найдено. `total_page_count: 0` при непустом `content`/`total_count: 2`
 * — не расхождение специфичное для этого эндпоинта: та же картина в `discover_watching_page0.json`
 * (`total_count: 88`, `total_page_count: 0`), похоже сервер вообще не считает эту величину нигде.
 *
 * [type] — `0` (`TYPE_STATIC`) обычная картинка, `1` (`TYPE_ANIMATION`) Lottie-анимация; в v1 UI
 * анимация не проигрывается — [imageUrl] рисуется как статичная картинка в обоих случаях (см.
 * `Achievement.isAnimated` в `:shared:model` — поле сохранено на будущее, сейчас не используется).
 */
@Serializable
data class BadgeDto(
    val id: Long = 0,
    val type: Int = 0,
    val name: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    val timestamp: Long = 0,
)
