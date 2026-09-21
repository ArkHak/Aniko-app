package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Современное поле поиска («пилюля») на [BasicTextField] — замена `OutlinedTextField` в местах,
 * где нужна плотная строка поиска фиксированной высоты.
 *
 * Почему не Material3 `OutlinedTextField` (найдено на Каталоге Desktop/Expanded): минимальная
 * высота M3-поля — 56.dp, а Каталог задавал ему `height(42.dp)` и ещё `padding(bottom)` внутрь
 * этой высоты — подсказка и вводимый текст обрезались, на месте поля оставался пустой серый
 * прямоугольник. Здесь высота — не меньше `minTouchTarget` (48.dp) и растёт по содержимому
 * (`heightIn(min)`), поэтому крупный системный шрифт поле не режет.
 *
 * Внешний вид: заливка `surface` + рамка `outline` (1.dp), в фокусе — рамка `primary` 2.dp;
 * ведущая иконка поиска, подсказка [placeholder] (`onSurface` с альфой 0.6 — контраст
 * ≥ 4.5:1 в обеих темах), кнопка очистки ✕ при непустом [value]. Цвета/скругление — из токенов
 * темы ([MaterialTheme.colorScheme], `AnixDimens.cornerPill`).
 *
 * **Доступность (TalkBack) — как решено.** Раньше `OutlinedTextField` под CMP отдавал пустое поле
 * без имени: `modifier`/`label` M3-поля не попадают на узел с `EditableText`/`SetText`. Здесь
 * `modifier.semantics { contentDescription = placeholder }` ставится на тот же [BasicTextField],
 * что несёт семантику редактируемого текста, — имя и `SetText` лежат в одном узле дерева (это
 * проверяется тестом `AnixSearchFieldSemanticsTest`); декоративные иконка и текст подсказки
 * скрыты от скринридера (`clearAndSetSemantics {}`), чтобы подсказка не озвучивалась дважды;
 * кнопка очистки — отдельный узел с [clearContentDescription] и ролью кнопки (48.dp).
 * ВАЖНО: озвучку именно на устройстве с TalkBack этот код не проверялся — структура дерева
 * семантики подтверждена тестом на Desktop, но чтение имени на Android нужно проверить руками.
 *
 * IME-действие — `Search`: по нему (и по Enter на Desktop) вызывается [onSearch], фокус
 * снимается и клавиатура скрывается. Запрос при этом применяется уже по мере ввода — debounce
 * живёт во ViewModel экрана, поле о нём ничего не знает.
 *
 * @param value текущий текст запроса.
 * @param onValueChange вызывается при каждом изменении текста (в том числе `""` по ✕).
 * @param placeholder подсказка пустого поля; она же — доступное имя поля.
 * @param clearContentDescription доступное имя кнопки очистки ✕.
 * @param modifier модификатор корневой «пилюли» (ширину задаёт вызывающая сторона).
 * @param onSearch вызывается по IME-действию Search / Enter, до снятия фокуса.
 */
// Публичный API поля; блок цельный (рамка/иконка/ввод/✕) — делить ради лимита вредно.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun AnixSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val dimens = AnixThemeTokens.dimens
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val shape = RoundedCornerShape(dimens.cornerPill)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    val hintColor = colors.onSurface.copy(alpha = HINT_ALPHA)

    Row(
        modifier =
            modifier
                .heightIn(min = dimens.minTouchTarget)
                .background(colors.surface, shape)
                .border(
                    width = if (isFocused) FOCUSED_BORDER_WIDTH else BORDER_WIDTH,
                    color = if (isFocused) colors.primary else colors.outline,
                    shape = shape,
                )
                // Тап по иконке/полям пилюли тоже фокусирует поле (сам BasicTextField ловит тапы
                // только в своей области). Дочерний текст потребляет свои тапы — двойной обработки нет.
                .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } }
                .padding(start = dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnixIcon(
            name = "search",
            contentDescription = null,
            modifier = Modifier.size(SEARCH_ICON_SIZE),
            tint = hintColor,
        )
        Spacer(Modifier.width(dimens.space12))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = placeholder },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        onSearch()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = hintColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Подсказка декоративна: имя поля уже задано contentDescription выше.
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            ClearButton(
                contentDescription = clearContentDescription,
                onClick = {
                    onValueChange("")
                    focusRequester.requestFocus()
                },
            )
        } else {
            // Правый отступ пустого состояния = ширина области ✕, чтобы поле не «прыгало» по мере ввода.
            Spacer(Modifier.width(dimens.spaceM))
        }
    }
}

/** Кнопка очистки: область 48.dp ([com.aniko.ui.theme.AnixDimens.minTouchTarget]), внутри — круг 20.dp с ✕. */
@Composable
private fun ClearButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            Modifier
                .size(dimens.minTouchTarget)
                .clickable(onClick = onClick)
                // clearAndSetSemantics ПОСЛЕ clickable (внутренний): действие onClick остаётся в семантике узла.
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CLEAR_CIRCLE_SIZE)
                    .background(onSurface.copy(alpha = CLEAR_CIRCLE_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnixIcon(
                name = "close",
                contentDescription = null,
                modifier = Modifier.size(CLEAR_ICON_SIZE),
                tint = onSurface.copy(alpha = CLEAR_ICON_ALPHA),
            )
        }
    }
}

/** Альфа подсказки/ведущей иконки поверх `onSurface`: ≥ 4.5:1 на `surface` в светлой и тёмной темах. */
private const val HINT_ALPHA = 0.6f
private const val CLEAR_CIRCLE_ALPHA = 0.14f
private const val CLEAR_ICON_ALPHA = 0.75f
private val BORDER_WIDTH = 1.dp
private val FOCUSED_BORDER_WIDTH = 2.dp
private val SEARCH_ICON_SIZE = 22.dp
private val CLEAR_CIRCLE_SIZE = 20.dp
private val CLEAR_ICON_SIZE = 14.dp
