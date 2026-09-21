package com.aniko.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Круг с иконкой строго по центру — единственный примитив «иконка в круглой подложке» оверлея
 * плеера: back/fullscreen и play/pause компактного режима ([PlayerCircleIconButton]), индикатор
 * перемотки по двойному тапу ([PlayerSeekFlashOverlay]) и кнопка закрытия пикера озвучки
 * (`AudioPickerHeader`). До появления примитива каждое из этих мест собирало
 * `Box(size).clip(CircleShape).background(..) { AnixIcon(..) }` само.
 *
 * Центровку гарантируют две вещи: (1) иконка ВСЕГДА получает явный `size(iconSize)` — слот иконки
 * квадратный, и `Box(contentAlignment = Center)` ставит его в геометрический центр круга; (2) сам
 * глиф внутри слота центрирует [AnixIcon] (em-квадрат шрифта по центру слота, см. её KDoc про
 * «Центровку глифа» — раньше именно там глиф съезжал на 0.1em вниз). Смещения относительно круга
 * измеряет `PlayerIconCenteringTest` (офскрин-рендер, допуск ≤ 1px).
 *
 * Оптического сдвига для `play_arrow` нет намеренно: центр масс закрашенного треугольника в глифе
 * Material Symbols уже лежит в ~0.013em от центра em-квадрата (сам глиф нарисован со сдвигом
 * вправо), то есть слот-центрированный `play_arrow` и есть «оптически» центрированный — 0.4px при
 * 28dp и 0.7px при 56dp.
 *
 * @param iconName имя иконки Material Symbols (см. [AnixIcon]).
 * @param diameter диаметр круга.
 * @param iconSize размер слота иконки (квадрат) — не больше [diameter].
 * @param background заливка круга (обычно полупрозрачный скрим поверх видео).
 * @param filled ось FILL иконки (см. [AnixIcon]).
 * @param tint цвет иконки; по умолчанию белый [OVERLAY_CONTENT_COLOR] — оверлей всегда лежит на кадре
 * видео под тёмным скримом, а не на теме приложения.
 * @param contentDescription озвучка иконки для скринридера; `null` (по умолчанию) — иконка
 * декоративна (её озвучивает объемлющая кнопка/жест), см. [AnixIcon].
 */
@Suppress("LongParameterList") // Примитив «иконка в круге»: 5 обязательных параметров декомпозиции
// (иконка/диаметр/слот/заливка/modifier) + 3 опциональных с дефолтами (FILL/tint/озвучка) — та же
// граница, что и у M3 `IconButton`/`Icon`, короче без потери явности не станет.
@Composable
internal fun PlayerIconCircle(
    iconName: String,
    diameter: Dp,
    iconSize: Dp,
    background: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = OVERLAY_CONTENT_COLOR,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(diameter).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        AnixIcon(
            name = iconName,
            contentDescription = contentDescription,
            filled = filled,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Круглая кнопка оверлея: тач-таргет не меньше `minTouchTarget` (48dp) и не меньше [diameter], а
 * видимая подложка — вложенный [PlayerIconCircle] фиксированного [diameter] (макет Claude Design
 * рисует именно круг заданного размера, а не растянутый на весь тач-таргет).
 *
 * `clearAndSetSemantics` вместо `contentDescription` на иконке: `IconButton` не сливает описание
 * вложенного узла в свой кликабельный узел (Фаза 11, T9, подтверждено на устройстве) — тот же
 * паттерн, что и у остальных M3-кнопок проекта.
 *
 * @param contentDescription озвучка кнопки для скринридера (обязательна: кнопка без подписи).
 * @see PlayerIconCircle про остальные параметры и гарантии центровки.
 */
@Suppress("LongParameterList") // Кнопка = подпись + onClick + та же декомпозиция круга
// ([PlayerIconCircle]): modifier не нужен (тач-таргет всегда max(minTouchTarget, diameter)).
@Composable
internal fun PlayerCircleIconButton(
    iconName: String,
    contentDescription: String,
    onClick: () -> Unit,
    diameter: Dp,
    iconSize: Dp,
    background: Color,
    filled: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(maxOf(AnixThemeTokens.dimens.minTouchTarget, diameter))
                .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        PlayerIconCircle(
            iconName = iconName,
            diameter = diameter,
            iconSize = iconSize,
            background = background,
            filled = filled,
        )
    }
}
