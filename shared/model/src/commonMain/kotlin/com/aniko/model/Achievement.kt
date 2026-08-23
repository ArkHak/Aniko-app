package com.aniko.model

/**
 * Один значок («достижение»), уже полученный текущим пользователем (`profile/preference/badge/all/{page}`).
 *
 * API отдаёт только коллекцию УЖЕ ПОЛУЧЕННЫХ бейджей (см. KDoc `BadgeDto` в `:shared:data`) — данных
 * о неполученных ачивках («заблокировано») сервер не даёт, поэтому в отличие от макета (там часть
 * чипов нарисована тусклой как "не получено") UI показывает только реально заработанные значки.
 */
data class Achievement(
    val id: Long,
    val name: String,
    val badgeUrl: String,
    /** `true` — Lottie-анимация (`Badge.TYPE_ANIMATION`), `false` — статичная картинка. Пока не используется в UI. */
    val isAnimated: Boolean,
    /** Unix-секунды момента получения значка. */
    val earnedAt: Long,
)
