package com.aniko.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Единый заголовок-страница (аналог `<h1>`) для контентной области раздела на широком окне
 * (`AnixWindowSize.Expanded`, постоянный сайдбар слева) — «Каталог», «Мои списки», «Расписание»
 * и другие разделы, у которых нет собственного `TopAppBar` в Expanded-раскладке.
 *
 * До этого компонента каждый экран заводил собственную копию стиля/отступа: размер (20sp) и вес
 * ([FontWeight.ExtraBold]) уже совпадали во всех трёх найденных местах, но базовый
 * `MaterialTheme.typography`-токен, `lineHeight` и величина отступа — нет (`CatalogFilterPanel`
 * не имел отступа у заголовка вообще, `LibraryExpandedHeader`/`ScheduleScreen` расставляли
 * [AnixThemeTokens.dimens] по-разному). Теперь стиль и отступ зашиты в одном месте — разнобой не
 * может повториться на новом экране, даже если тот забудет про паддинг.
 *
 * Стиль — [MaterialTheme.typography]`.headlineMedium` (Manrope SemiBold 20/25, iOS-шкала
 * «Title 3», см. [com.aniko.ui.theme.anixTypography]) с весом, усиленным до [FontWeight.ExtraBold]:
 * это единственный слот типографики, который уже даёт нужные 20sp БЕЗ переопределения размера —
 * не изобретаем новый размер/lineHeight, только поднимаем вес до значения, использованного во всех
 * трёх найденных прототипах.
 *
 * Отступ — [AnixThemeTokens.dimens.spaceM] по горизонтали и вертикали, зашит внутрь composable, а
 * не передаётся снаружи: так новый экран не может ни забыть его выставить, ни подставить другое
 * значение шкалы токенов. [modifier] — для позиционирования/дополнительных отступов вызывающей
 * стороны ПОВЕРХ встроенного (например `Modifier.weight(1f)` внутри `Row`), а не взамен него —
 * если экран уже даёт такой же отступ контейнером выше (см. вызовы в `LibraryExpandedLayout.kt`/
 * `ScheduleScreen.kt`), тот отступ нужно убрать, чтобы не удвоить его.
 */
@Composable
fun ExpandedScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = dimens.spaceM, vertical = dimens.spaceM),
    )
}
