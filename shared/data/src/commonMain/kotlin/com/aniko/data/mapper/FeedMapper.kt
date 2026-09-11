package com.aniko.data.mapper

import com.aniko.data.dto.ArticleBlockDto
import com.aniko.data.dto.ArticleDto
import com.aniko.data.dto.ChannelCompactDto
import com.aniko.model.Article
import com.aniko.model.ArticleChannel
import com.aniko.network.ApiConfig

fun ArticleDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): Article =
    Article(
        id = id,
        channel = channel?.toDomain(staticBaseUrl),
        author = author?.toDomain(staticBaseUrl),
        previewText = payload?.blocks.orEmpty().toPreviewText(),
        creationDateSeconds = creationDate,
        commentCount = commentCount,
        repostCount = repostCount,
        voteCount = voteCount,
        isPinned = isPinned,
    )

fun ChannelCompactDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): ArticleChannel =
    ArticleChannel(
        id = id,
        title = title.orEmpty(),
        avatarUrl = avatar?.takeIf { it.isNotBlank() }?.toAbsoluteUrl(staticBaseUrl),
        isVerified = isVerified,
    )

/**
 * Plain-текст превью карточки ленты (см. KDoc [ArticleDto] в `shared/data`) — только
 * paragraph/header-блоки, остальные типы (media/embed/quote/delimiter/list) пропускаются: для
 * MVP-превью карточки нужен только текст, не полный рендер контента.
 *
 * Живая проверка 2026-09-11 (реальный аккаунт, `feed/latest/all/0`): текст блоков содержит
 * числовые HTML-сущности (`&#x1f348;` — эмодзи), не только теги — [decodeHtmlEntities]
 * применяется ПОСЛЕ снятия тегов (снятие тегов первым — чтобы декодированный `&lt;`/`&gt;` не
 * породил фальшивый тег, который потом случайно вырежется вместе с реальным текстом).
 */
internal fun List<ArticleBlockDto>.toPreviewText(): String =
    asSequence()
        .filter { it.type == BLOCK_TYPE_PARAGRAPH || it.type == BLOCK_TYPE_HEADER }
        .mapNotNull { it.data?.text }
        .map { decodeHtmlEntities(htmlTagRegex.replace(it, "")) }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .take(PREVIEW_MAX_LENGTH)

/** Декодирует числовые (`&#39;`/`&#x1f348;`) и базовые именованные (`&amp;`/`&lt;`/`&gt;`/
 *  `&quot;`/`&apos;`) HTML-сущности — без `java.lang.Character` (не компилируется под iOS-таргет
 *  `shared/data`): суррогатная пара считается вручную для кодпоинтов вне BMP (эмодзи). */
internal fun decodeHtmlEntities(text: String): String =
    htmlEntityRegex.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(HEX_RADIX)?.let(::codePointToString)
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointToString)
            else -> NAMED_HTML_ENTITIES[body]
        } ?: match.value
    }

private fun codePointToString(codePoint: Int): String =
    if (codePoint <= MAX_BMP_CODE_POINT) {
        codePoint.toChar().toString()
    } else {
        val adjusted = codePoint - SUPPLEMENTARY_OFFSET
        val high = (adjusted shr HIGH_SURROGATE_SHIFT) + HIGH_SURROGATE_BASE
        val low = (adjusted and LOW_SURROGATE_MASK) + LOW_SURROGATE_BASE
        charArrayOf(high.toChar(), low.toChar()).concatToString()
    }

private val NAMED_HTML_ENTITIES =
    mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )

private val htmlEntityRegex = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private const val MAX_BMP_CODE_POINT = 0xFFFF
private const val SUPPLEMENTARY_OFFSET = 0x10000
private const val HIGH_SURROGATE_SHIFT = 10
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_MASK = 0x3FF
private const val LOW_SURROGATE_BASE = 0xDC00
private const val HEX_RADIX = 16

private const val BLOCK_TYPE_PARAGRAPH = "paragraph"
private const val BLOCK_TYPE_HEADER = "header"
private const val PREVIEW_MAX_LENGTH = 240
private val htmlTagRegex = Regex("<[^>]*>")
