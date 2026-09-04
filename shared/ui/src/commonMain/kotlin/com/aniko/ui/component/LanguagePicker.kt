package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.jetBrainsMonoFontFamily

/**
 * Компактный переключатель языка (P5.T9, UI-часть) — используется в Settings и в футере
 * сайдбара ([com.aniko.ui.adaptive.AdaptiveScaffold] на Expanded).
 *
 * Тот же паттерн, что уже был у `GalleryControls` в `composeApp/.../gallery/TokenGalleryScreen.kt`
 * (`ChipRow` с `listOf(null, "en", "ru")`), вынесенный в `shared/ui`, чтобы им мог пользоваться
 * и сайдбар, и Settings — не только галерея токенов.
 *
 * [currentTag] — `null` означает "следовать системному языку", `"en"`/`"ru"` — явный выбор.
 * Подпись чипа рисуется JetBrains Mono (макет Claude Design) — единственный вызов `ChipRow` в
 * проекте с `labelFontFamily` вместо дефолтного шрифта темы.
 */
@Composable
fun AnixLanguagePicker(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    ChipRow(
        items = listOf(null, "en", "ru"),
        isSelected = { it == currentTag },
        label = {
            when (it) {
                "en" -> "EN"
                "ru" -> "RU"
                else -> strings.galleryLanguageSystem
            }
        },
        onClick = onSelect,
        modifier = modifier,
        labelFontFamily = jetBrainsMonoFontFamily(),
    )
}
