package com.aniko.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aniko.shared.ui.generated.resources.Res
import aniko.shared.ui.generated.resources.material_symbols_rounded
import org.jetbrains.compose.resources.Font

/**
 * Единая точка использования иконок в проекте (P13, Track B — точное соответствие макету
 * Claude Design). Рендерит переменный шрифт "Material Symbols Rounded"
 * (`shared/ui/src/commonMain/composeResources/font/material_symbols_rounded.ttf`, subset на 31
 * имя из [MaterialSymbolsCodepoints]) через [Text] по Unicode-codepoint — ось `FILL` переключает
 * outline↔filled (напр. выбранная вкладка профиля/таба навигации), `wght`/`GRAD`/`opsz`
 * зафиксированы на значениях мокапа.
 *
 * Размер иконки: если вызывающий код передал `modifier.size(x)` (как делают ~44 места
 * использования по проекту), этот размер и используется; иначе — [DefaultIconSize] (24dp), тот
 * же дефолт, что был у M3 `Icon()` для векторов без собственного intrinsic-размера. Дефолт задаётся
 * через `modifier.then(Modifier.size(DefaultIconSize))` (тот же порядок, что в исходнике M3
 * `Icon()` — `defaultSizeFor`) — размер-модификатор caller'а, если он есть, идёт первым (внешним)
 * и побеждает; наш `size(DefaultIconSize)` — последним (внутренним) и просто сужает то, что
 * осталось, если caller ничего не передал.
 *
 * **Важно, найдено живым запуском на iOS-симуляторе (не видно по коду/detekt/компиляции)**: до
 * этого фикса размер брался из АМБИЕНТНЫХ constraints `BoxWithConstraints` (что бы ни отдал
 * родитель) — рабочая гипотеза была, что «bounded constraints = вызывающий код явно запросил
 * размер». Это неверно: `NavigationBarItem` отдаёт слоту иконки собственные bounded constraints
 * (область под indicator-пилюлю), НЕ совпадающие с 24dp и не являющиеся намеренным запросом
 * размера — `AnixIcon` растягивался на них, давая иконки нижней навигации в разы больше макета.
 * M3 `Icon()` никогда не подстраивался под амбиентные constraints — всегда фиксированный дефолт,
 * если caller явно не попросил другое; исправление возвращает то же поведение.
 *
 * @param name имя иконки Material Symbols (напр. "home", "arrow_back", "search") — то же имя,
 *   что в референсном мокапе и в ключах [MaterialSymbolsCodepoints.map].
 * @param filled ось FILL: `true` — закрашенный вариант (обычно активное/выбранное состояние,
 *   напр. таб профиля), `false` (по умолчанию) — контурный.
 */
@Composable
fun AnixIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val codepoint =
        MaterialSymbolsCodepoints.map[name]
            ?: error(
                "AnixIcon: unknown icon name \"$name\" — add a mapping in " +
                    "MaterialSymbolsCodepoints.kt::map (and make sure the glyph is included in the " +
                    "material_symbols_rounded.ttf subset).",
            )
    // Все codepoint'ы Material Symbols лежат в Private Use Area — однобайтовые в UTF-16, поэтому
    // `.toChar()` безопасен. Не `java.lang.Character.toChars` — тот JVM-only, здесь нужен
    // multiplatform-код (commonMain собирается и под iOS/Desktop).
    val glyph = codepoint.toChar().toString()
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            }
        } else {
            // Как и у M3 Icon() с contentDescription == null: иконка декоративна и не должна
            // озвучиваться. Text сам по себе добавляет узел с текстом-глифом в семантическое
            // дерево — без clearAndSetSemantics screen reader читал бы private-use-area символ.
            Modifier.clearAndSetSemantics {}
        }
    BoxWithConstraints(
        // .size(DefaultIconSize) последним: caller'ский modifier (если задаёт свой size) идёт
        // первым и побеждает — тот же порядок, что defaultSizeFor() в исходнике M3 Icon(). Без
        // этого фиксированного дефолта размер брался из ambient constraints, которые сюда
        // прилетают (см. KDoc выше про находку на NavigationBarItem).
        modifier = modifier.then(semanticsModifier).size(DefaultIconSize),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val sizeDp =
            when {
                constraints.hasBoundedWidth && constraints.hasBoundedHeight -> minOf(maxWidth, maxHeight)
                constraints.hasBoundedWidth -> maxWidth
                constraints.hasBoundedHeight -> maxHeight
                else -> DefaultIconSize
            }
                // Некоторые layout-родители (напр. intrinsic-проход Row/Column, SubcomposeLayout)
                // на время присылают constraints с maxWidth/maxHeight == 0 — при sizeDp == 0.dp
                // fontSize уходит в 0.sp, а с ним и lineHeight (= fontSize ниже), и Skia падает
                // с IllegalStateException("Check failed.") в TextStyle.setHeight (height =
                // lineHeight/fontSize = 0/0 = NaN). Подтверждено крашем при живом запуске
                // Desktop-таргета. 1.dp — не видимый глазом размер на промежуточном проходе,
                // который в любом случае отбрасывается финальным measure.
                .coerceAtLeast(1.dp)
        val fontSize = with(density) { sizeDp.toSp() }
        // remember: FontVariation.Settings/FontFamily — обычные (не-@Composable) конструкторы,
        // пересоздавать их на каждую рекомпозицию незачем — меняются только по [filled]. Сам
        // Font(...) ниже @Composable (управляет своим State/кешем typeface в самой библиотеке
        // ресурсов), его нельзя занести внутрь remember{} — composable-вызовы там запрещены.
        val variationSettings =
            remember(filled) {
                FontVariation.Settings(
                    FontVariation.Setting(name = "FILL", value = if (filled) 1f else 0f),
                    FontVariation.Setting(name = "wght", value = 400f),
                    FontVariation.Setting(name = "GRAD", value = 0f),
                    FontVariation.Setting(name = "opsz", value = 24f),
                )
            }
        val font = Font(resource = Res.font.material_symbols_rounded, variationSettings = variationSettings)
        val fontFamily = remember(font) { FontFamily(font) }
        Text(
            text = glyph,
            color = tint,
            fontSize = fontSize,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            fontFamily = fontFamily,
        )
    }
}

/** Дефолтный размер иконки без явного `modifier.size(...)` — см. KDoc [AnixIcon]. */
private val DefaultIconSize = 24.dp
