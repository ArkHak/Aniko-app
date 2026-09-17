package com.aniko.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Liquid Glass (Apple iOS 26 материал) — полупрозрачное преломляющее стекло с real-time backdrop-
 * blur, тонирующей заливкой, мягким вертикальным градиентом, световым бликом по верхней кромке и
 * волосяной обводкой (2026-09-11, feature/liquid-glass-tab-bar). Не буквальная копия официального
 * Anixart — задача была "выглядит отполировано/премиально", референс — сам эффект Apple, не чей-то
 * конкретный экран.
 *
 * ## Архитектура — двухслойный backdrop-blur, весь код общий (`commonMain`)
 * 1. [Modifier.glassBackdropSource] на контейнере контента, который должен просвечивать под
 *    стеклом, записывает этот контент в offscreen [GraphicsLayer] на каждой перерисовке
 *    ([GlassBackdropState]).
 * 2. Это стекло читает тот слой (явный параметр `state` или [LocalGlassBackdrop]), вычисляет своё
 *    положение относительно источника ([LayoutCoordinates.localPositionOf]), рисует
 *    СООТВЕТСТВУЮЩИЙ вырезанный прямоугольник в СВОЙ собственный (маленький, размером с сам бар, не
 *    с весь экран) даунсемпленный [GraphicsLayer], применяет к нему [BlurEffect] и растягивает
 *    результат обратно на полный размер при финальной отрисовке.
 * 3. Единственное платформенное ветвление во всей фиче — [isRealtimeBlurSupported] (Android
 *    API<31 не поддерживает `RenderEffect`) — стекло без реального блюра деградирует в
 *    непрозрачную заливку [LiquidGlassStyle.fallbackAlpha], не в крэш и не в пустое место.
 *
 * ## Почему даунсемплинг ([LiquidGlassStyle.downsampleFactor])
 * Блюр полного разрешения (особенно на iOS retina, ×3 плотность пикселей) заметно дороже, чем
 * блюр маленького слоя с последующим апскейлом — визуальная разница на реальном экране
 * незаметна (тот же трюк, которым живут почти все backdrop-blur реализации, включая нативный iOS).
 * Слой стекла рисуется в разрешении `barSize * downsampleFactor`, блюрится радиусом
 * `blurRadius * downsampleFactor` (радиус блюра должен масштабироваться вместе с холстом, иначе
 * на уменьшенном холсте тот же абсолютный радиус даёт визуально куда более сильный блюр), затем
 * растягивается обратно `scale(1 / downsampleFactor)` при финальной отрисовке.
 *
 * ## Слои отрисовки (снизу вверх, см. [ContentDrawScope.draw])
 * 1. Размытый фон (если [isRealtimeBlurSupported] и источник задан), иначе — п.2 сразу плотнее.
 * 2. Тонирующая заливка (`tintAlpha` поверх блюра / `fallbackAlpha` без него).
 * 3. Мягкий вертикальный градиент (светлее у верхней кромки).
 * 4. Specular-блик по верхней кромке.
 * 5. Волосяная обводка (rim) по контуру формы.
 * 6. `drawContent()` — сам контент стеклянного контейнера (иконки/подписи таб-бара).
 *
 * `Modifier.clip(style.shape)` применяется ПЕРЕД собственной draw-нодой (см. реализацию ниже) —
 * все шаги 1-6 автоматически обрезаны по форме через обычный механизм Compose `clip`, отдельная
 * ручная обрезка (`clipPath`) внутри draw-ноды не нужна.
 */
fun Modifier.liquidGlass(
    style: LiquidGlassStyle,
    state: GlassBackdropState? = null,
): Modifier = this.clip(style.shape) then LiquidGlassElement(style, state)

/**
 * Тонкая обёртка над [Modifier.liquidGlass] — переиспользуемый примитив без дублирования логики
 * отрисовки, см. её KDoc.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    intensity: GlassIntensity = GlassIntensity.Regular,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LiquidGlassDefaults.style(shape = shape, intensity = intensity)
    Box(modifier = modifier.liquidGlass(style), content = content)
}

private data class LiquidGlassElement(
    private val style: LiquidGlassStyle,
    private val explicitState: GlassBackdropState?,
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create(): LiquidGlassNode = LiquidGlassNode(style, explicitState)

    override fun update(node: LiquidGlassNode) {
        node.updateStyle(style)
        node.updateExplicitState(explicitState)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlass"
        properties["style"] = style
        properties["state"] = explicitState
    }
}

private class LiquidGlassNode(
    private var style: LiquidGlassStyle,
    private var explicitState: GlassBackdropState?,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var resolvedState: GlassBackdropState? = null
    private var ownLayer: GraphicsLayer? = null
    private var graphicsContext: GraphicsContext? = null
    private var myCoordinates: LayoutCoordinates? = null

    fun updateStyle(newStyle: LiquidGlassStyle) {
        style = newStyle
        invalidateDraw()
    }

    fun updateExplicitState(newState: GlassBackdropState?) {
        if (newState !== explicitState) {
            explicitState = newState
            reresolveState()
        }
    }

    override fun onAttach() {
        graphicsContext = currentValueOf(LocalGraphicsContext)
        reresolveState()
    }

    override fun onDetach() {
        resolvedState?.unregisterGlass(this)
        resolvedState = null
        ownLayer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        ownLayer = null
        graphicsContext = null
        myCoordinates = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        myCoordinates = coordinates
    }

    /**
     * `state` явным параметром выигрывает у [LocalGlassBackdrop] — тот читается только когда
     * `explicitState == null` (см. KDoc [com.aniko.ui.glass.liquidGlass]).
     */
    private fun reresolveState() {
        resolvedState?.unregisterGlass(this)
        val next = explicitState ?: currentValueOf(LocalGlassBackdrop)
        resolvedState = next
        next?.registerGlass(this)
    }

    override fun ContentDrawScope.draw() {
        val backdrop = resolvedState
        val srcLayer = backdrop?.sourceLayer
        val srcCoords = backdrop?.sourceCoordinates
        val myCoords = myCoordinates

        val canBlur =
            isRealtimeBlurSupported() &&
                srcLayer != null &&
                srcCoords != null &&
                srcCoords.isAttached &&
                myCoords != null &&
                myCoords.isAttached &&
                // Стекло внутри поддерева источника рисуется В МОМЕНТ записи этого слоя — читать
                // его нельзя (рекурсия record→draw, SIGILL по переполнению стека, 2026-09-17);
                // такой проход отрисовывается fallback-заливкой.
                !backdrop.isRecording

        if (canBlur) {
            drawBlurredBackdrop(srcLayer, srcCoords, myCoords)
            drawRect(color = style.tint.copy(alpha = style.tintAlpha))
        } else {
            drawRect(color = style.tint.copy(alpha = style.fallbackAlpha))
        }

        drawTopHighlightGradient()
        drawSpecular(style)
        drawRim(style)
        drawContent()
    }

    /** Слой 1 — см. KDoc [com.aniko.ui.glass.liquidGlass] про архитектуру/даунсемплинг. */
    private fun ContentDrawScope.drawBlurredBackdrop(
        srcLayer: GraphicsLayer?,
        srcCoords: LayoutCoordinates,
        myCoords: LayoutCoordinates,
    ) {
        val ctx = graphicsContext
        if (srcLayer == null || ctx == null) return

        val factor = style.downsampleFactor.coerceIn(MIN_DOWNSAMPLE_FACTOR, 1f)
        val fullSize = size
        val downW = (fullSize.width * factor).roundToInt().coerceAtLeast(1)
        val downH = (fullSize.height * factor).roundToInt().coerceAtLeast(1)
        val downSize = IntSize(downW, downH)

        var glassLayer = ownLayer
        if (glassLayer == null) {
            glassLayer = ctx.createGraphicsLayer()
            ownLayer = glassLayer
        }

        // Позиция ЭТОГО стекла внутри локальных координат источника — вырезаем правильный
        // прямоугольник фона независимо от того, где на экране физически расположен бар.
        val topLeft = srcCoords.localPositionOf(myCoords, Offset.Zero)

        glassLayer.record(size = downSize) {
            scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                translate(left = -topLeft.x, top = -topLeft.y) {
                    drawLayer(srcLayer)
                }
            }
        }
        glassLayer.renderEffect =
            BlurEffect(
                radiusX = style.blurRadius.toPx() * factor,
                radiusY = style.blurRadius.toPx() * factor,
                edgeTreatment = TileMode.Clamp,
            )

        scale(scaleX = 1f / factor, scaleY = 1f / factor, pivot = Offset.Zero) {
            drawLayer(glassLayer)
        }
    }
}

// Слои 3-5 — вынесены из [LiquidGlassNode] top-level функциями (не члены класса): им нужен только
// [LiquidGlassStyle] и сам [DrawScope], без мутируемого состояния ноды — detekt `TooManyFunctions`
// у самого класса иначе считал бы их наравне с lifecycle/resolve-функциями, которым состояние
// действительно нужно (см. KDoc [com.aniko.ui.glass.liquidGlass] про слои отрисовки).

/**
 * Слой 3 — мягкий вертикальный градиент, светлее у верхней кромки к нейтральному внизу. Не
 * зависит от [LiquidGlassStyle] (фиксированный белый блик, не тонированный) — сознательно без
 * параметра стиля, в отличие от соседних [drawSpecular]/[drawRim].
 */
private fun DrawScope.drawTopHighlightGradient() {
    val brush =
        Brush.verticalGradient(
            colors = listOf(GRADIENT_TOP_COLOR, Color.Transparent),
            startY = 0f,
            endY = size.height * GRADIENT_HEIGHT_FRACTION,
        )
    drawRect(brush = brush)
}

/** Слой 4 — тонкая световая полоса по самой верхней кромке (`specularHeight`). */
private fun DrawScope.drawSpecular(style: LiquidGlassStyle) {
    val height = style.specularHeight.toPx()
    if (height <= 0f) return
    drawRect(color = style.specular, size = Size(width = size.width, height = height))
}

/** Слой 5 — волосяная обводка по контуру [LiquidGlassStyle.shape]. */
private fun DrawScope.drawRim(style: LiquidGlassStyle) {
    val width = style.rimWidth.toPx()
    if (width <= 0f) return
    val outline = style.shape.createOutline(size, layoutDirection, this)
    drawOutline(outline = outline, color = style.rim, style = Stroke(width = width))
}

/** Нижняя граница даунсемплинга — защита от вырожденного нулевого/отрицательного слоя. */
private const val MIN_DOWNSAMPLE_FACTOR = 0.05f

private const val GRADIENT_HEIGHT_FRACTION = 0.6f
private val GRADIENT_TOP_COLOR = Color.White.copy(alpha = 0.06f)
