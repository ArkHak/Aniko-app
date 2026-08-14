package com.aniko.model

/**
 * Баннер-карусель главного экрана — `POST discover/interesting` (публичный эндпоинт, без
 * токена, см. KDoc `ReleaseApi.discoverInteresting`). Домен-модель над `InterestingDto`
 * (`shared/data`), которая по имени и форме соответствует decompiled
 * `database/entity/release/Interesting.java`.
 */
data class InterestingBanner(
    val id: Int,
    val title: String,
    val description: String? = null,
    val imageUrl: String,
    /** Распарсенный `action` (`Int?`, `toIntOrNull()`) — id релиза-цели перехода, если он числовой. */
    val releaseId: Int? = null,
    val type: Int = 0,
)
