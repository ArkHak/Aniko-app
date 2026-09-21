package com.aniko.app.feature.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens

// Строительные блоки «пилюль» верхней панели Каталога (`CatalogToolbar`) и меню чипов
// (`CatalogFilterMenus`): контейнер с заливкой/рамкой, зона нажатия 48.dp и шеврон. Вынесены из
// `CatalogToolbar.kt`, чтобы файл панели описывал раскладку и фильтры, а не рисование.

/**
 * Контейнер-«пилюля»: строка высотой в зону нажатия (`minTouchTarget`), внутри которой рисуется
 * заливка `surface` + рамка высотой [CHIP_HEIGHT] по центру. Активный чип — тинт `primary` и
 * рамка `primary`; открытое меню — рамка `primary` толще. Зоны нажатия ([PillZone]) лежат внутри.
 */
@Composable
internal fun ChipSurface(
    active: Boolean,
    open: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = MaterialTheme.colorScheme
    val borderColor =
        when {
            open -> colors.primary
            active -> colors.primary.copy(alpha = ACTIVE_BORDER_ALPHA)
            else -> colors.outline
        }
    val borderWidth = if (open) CHIP_OPEN_BORDER_WIDTH else CHIP_BORDER_WIDTH
    val tint = if (active) colors.primary.copy(alpha = TINT_ALPHA) else Color.Transparent
    val surface = colors.surface
    val verticalInset = (dimens.minTouchTarget - CHIP_HEIGHT) / 2
    Row(
        modifier =
            modifier
                .height(dimens.minTouchTarget)
                .drawBehind {
                    val inset = verticalInset.toPx()
                    val stroke = borderWidth.toPx()
                    val pillHeight = size.height - 2 * inset
                    val cornerRadius = CornerRadius(pillHeight / 2f)
                    drawRoundRect(surface, Offset(0f, inset), Size(size.width, pillHeight), cornerRadius)
                    if (tint != Color.Transparent) {
                        drawRoundRect(tint, Offset(0f, inset), Size(size.width, pillHeight), cornerRadius)
                    }
                    // Рамка рисуется внутрь пилюли (сдвиг на полтолщины), а не по краю.
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(stroke / 2f, inset + stroke / 2f),
                        size = Size(size.width - stroke, pillHeight - stroke),
                        cornerRadius = CornerRadius((pillHeight - stroke) / 2f),
                        style = Stroke(stroke),
                    )
                },
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Зона нажатия внутри [ChipSurface]: минимум 48×48.dp (`minTouchTarget`), подсветка наведения/
 * нажатия/фокуса рисуется поверх пилюли по форме [overlayShape] (у сдвоенных зон чипа —
 * скруглена только своя сторона). Ripple не используется: он залил бы всю 48-dp зону, а не
 * видимую 36-dp пилюлю.
 *
 * Семантика задаётся ЯВНО через [semantics] внутри `clearAndSetSemantics` (паттерн проекта:
 * дочерние `Text`/иконки не должны озвучиваться отдельно), включая `onClick` — чтобы действие
 * «нажать» было в дереве семантики независимо от порядка модификаторов.
 */
@Composable
internal fun PillZone(
    onTap: () -> Unit,
    overlayShape: Shape,
    semantics: SemanticsPropertyReceiver.() -> Unit,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val hovered = interactionSource.collectIsHoveredAsState()
    val focused = interactionSource.collectIsFocusedAsState()
    val overlayColor = MaterialTheme.colorScheme.onSurface
    val verticalInsetPx = with(LocalDensity.current) { ((dimens.minTouchTarget - CHIP_HEIGHT) / 2).toPx() }
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = dimens.minTouchTarget, minHeight = dimens.minTouchTarget)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onTap)
                .drawBehind {
                    val alpha =
                        when {
                            pressed.value -> PRESSED_ALPHA
                            hovered.value || focused.value -> HOVER_ALPHA
                            else -> 0f
                        }
                    if (alpha > 0f) {
                        val overlay = Size(size.width, size.height - 2 * verticalInsetPx)
                        translate(top = verticalInsetPx) {
                            drawOutline(
                                overlayShape.createOutline(overlay, layoutDirection, this),
                                overlayColor.copy(alpha = alpha),
                            )
                        }
                    }
                }.clearAndSetSemantics {
                    testTag?.let { this.testTag = it }
                    semantics()
                    onClick {
                        onTap()
                        true
                    }
                },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Шеврон ▾ (рисуется путём: в наборе Material Symbols проекта нет `expand_more`); при открытом меню — ▴. */
@Composable
internal fun ChevronDown(
    tint: Color,
    open: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .size(width = CHEVRON_WIDTH, height = CHEVRON_HEIGHT)
                .rotate(if (open) CHEVRON_ROTATION_OPEN else 0f),
    ) {
        val stroke = CHEVRON_STROKE.toPx()
        val half = stroke / 2f
        val path =
            Path().apply {
                moveTo(half, half)
                lineTo(size.width / 2f, size.height - half)
                lineTo(size.width - half, half)
            }
        drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---- Метрики (размеры зон нажатия — из `AnixDimens.minTouchTarget`) ------------------------------

/** Видимая высота «пилюли»; зона нажатия вокруг неё — `minTouchTarget` (48.dp). */
internal val CHIP_HEIGHT: Dp = 36.dp
internal val CHIP_BORDER_WIDTH = 1.dp
private val CHIP_OPEN_BORDER_WIDTH = 1.5.dp
internal val CHIP_HORIZONTAL_PADDING = 14.dp
internal val CHIP_CONTENT_GAP = 6.dp
private val CHEVRON_WIDTH = 12.dp
private val CHEVRON_HEIGHT = 7.dp
private val CHEVRON_STROKE = 1.75.dp
private const val CHEVRON_ROTATION_OPEN = 180f

/** Тинт `primary` под активным чипом/выбранным сегментом. */
internal const val TINT_ALPHA = 0.16f
internal const val ACTIVE_BORDER_ALPHA = 0.6f
internal const val ICON_ALPHA = 0.7f
private const val PRESSED_ALPHA = 0.14f
private const val HOVER_ALPHA = 0.06f
