package com.aniko.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Очистка HTML-разметки из текстовых полей API ([stripHtmlMarkup], см. её KDoc): сервер отдаёт
 * описания с `<br>` (живая находка 2026-09-24 на расширенной карточке «Блич: Тысячелетняя
 * кровавая война — Бедствие»), ссылками и сущностями — Compose `Text` их не рендерит, пользователь
 * видел сырой тег в синопсисе.
 */
class HtmlMarkupStripperTest {
    @Test
    fun brTags_becomeNewlines() {
        // Каждый <br> — перевод строки: пара тегов даёт разрыв абзаца.
        assertEquals(
            "Первая часть.\n\nВторая часть.",
            "Первая часть.<br><br>Вторая часть.".stripHtmlMarkup(),
        )
    }

    @Test
    fun brVariants_allHandled() {
        assertEquals("a\nb\nc\nd", "a<br>b<br/>c<BR />d".stripHtmlMarkup())
    }

    @Test
    fun linkTag_keepsLinkText() {
        assertEquals(
            "Подробности на сайте.",
            "Подробности на <a href=\"https://example.com\">сайте</a>.".stripHtmlMarkup(),
        )
    }

    @Test
    fun entities_decoded() {
        assertEquals(
            "Том & Джерри — \"классика\" …",
            "Том &amp; Джерри &mdash; &quot;классика&quot; &hellip;".stripHtmlMarkup(),
        )
    }

    @Test
    fun numericEntities_decoded() {
        // U+0410 «А» (hex) и U+0411 «Б» (dec 1041).
        assertEquals("А Б", "&#x410; &#1041;".stripHtmlMarkup())
    }

    @Test
    fun plainText_unchanged() {
        assertEquals("Просто текст, 2 серии.", "Просто текст, 2 серии.".stripHtmlMarkup())
    }

    @Test
    fun excessiveNewlines_collapsed() {
        assertEquals("a\n\nb", "a<br><br><br><br>b".stripHtmlMarkup())
    }

    @Test
    fun surroundingWhitespace_trimmed() {
        assertEquals("текст", "  <br>текст<br>  ".stripHtmlMarkup())
    }
}
