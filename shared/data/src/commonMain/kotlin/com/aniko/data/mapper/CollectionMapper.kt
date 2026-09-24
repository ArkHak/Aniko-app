package com.aniko.data.mapper

import com.aniko.data.dto.CollectionDto
import com.aniko.model.AnixCollection
import com.aniko.network.ApiConfig

fun CollectionDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): AnixCollection =
    AnixCollection(
        id = id,
        creator = creator?.toDomain(staticBaseUrl),
        title = title,
        description = description.stripHtmlMarkup(),
        imageUrl = image.takeIf { it.isNotBlank() }?.toAbsoluteUrl(staticBaseUrl),
        favoritesCount = favoritesCount,
        commentCount = commentCount,
    )
