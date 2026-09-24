package com.aniko.data.mapper

/**
 * Убирает HTML-разметку из текстовых полей API Anixart (`description` релизов/баннеров/
 * коллекций): сервер отдаёт их с тегами — минимум `<br>` (живая находка 2026-09-24 на
 * расширенной карточке «Блич: Тысячелетняя кровавая война — Бедствие»), возможны ссылки и
 * сущности. Compose `Text` разметку не рендерит — без очистки пользователь видит сырые
 * `<br><br>` прямо в синопсисе.
 *
 * Очистка намеренно на уровне маппера DTO → domain (а не в UI): потребителей несколько
 * (`HeroSynopsis` во всех раскладках шапки, `CatalogResultsGrid`, `HomeBanner`,
 * `CollectionsScreen`), и в БД кэша пишется уже чистый текст. Устаревшие кэшированные записи
 * с сырой разметкой перезаписываются при ближайшем сетевом обновлении — миграция БД не нужна.
 *
 * `<br>` превращается в перевод строки, остальные теги вырезаются (текст ссылки сохраняется),
 * сущности декодируются общим [decodeHtmlEntities] (тот же, что в `FeedMapper.toPreviewText`) —
 * ПОСЛЕ снятия тегов, чтобы декодированный `&lt;` не породил фальшивый тег (тот же порядок и
 * обоснование, что в `FeedMapper`).
 */
internal fun String.stripHtmlMarkup(): String {
    val withBreaks = BR_TAG.replace(this, "\n")
    val withoutTags = ANY_TAG.replace(withBreaks, "")
    return decodeHtmlEntities(withoutTags)
        .replace(TRAILING_SPACES_BEFORE_NEWLINE, "\n")
        .replace(EXCESSIVE_NEWLINES, "\n\n")
        .trim()
}

private val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]*>")
private val TRAILING_SPACES_BEFORE_NEWLINE = Regex("[ \\t]+\\n")
private val EXCESSIVE_NEWLINES = Regex("\\n{3,}")
