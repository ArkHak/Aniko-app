@file:Suppress("MatchingDeclarationName")
// Файл назван по роли целиком (источник backdrop-blur), а не только по единственному публичному
// top-level классу (GlassBackdropState) — рядом с ним (по архитектурному решению фичи Liquid Glass,
// 2026-09-11) намеренно живут его composable-конструктор, CompositionLocal и модификатор-источник
// с приватной Modifier.Node-реализацией: одна цельная единица API, тот же приём, что уже в
// TitleCard.kt/ListStatusChip.kt.

package com.aniko.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * Источник контента, просвечивающего сквозь стекло (2026-09-11, feature/liquid-glass-tab-bar) —
 * половина двухслойного backdrop-blur, описанного в KDoc [com.aniko.ui.glass.LiquidGlass]: ЭТОТ
 * слой (обычно — контентная область экрана под плавающим таб-баром) записывает себя в offscreen
 * [GraphicsLayer] на каждой перерисовке ([Modifier.glassBackdropSource]), а
 * [com.aniko.ui.glass.LiquidGlass] читает этот слой через [LocalGlassBackdrop]/явный параметр
 * `state`, вырезает нужный прямоугольник и размывает его.
 *
 * **Почему не `mutableStateOf`.** Наивная реализация держала бы [GraphicsLayer] в
 * `mutableStateOf`, записываемом во время фазы `draw` источника — Compose это либо роняет
 * (`IllegalStateException`: запись snapshot-состояния в фазе draw запрещена), либо создаёт цикл
 * инвалидации (запись состояния => инвалидация => новый draw => новая запись...). Вместо этого
 * источник ([GlassBackdropSourceNode]) держит обычное (не-snapshot) изменяемое поле
 * [GlassBackdropState.sourceLayer] и explicit-список подписанных стеклянных `Modifier.Node`
 * ([GlassBackdropState.registerGlass]) — после своей записи в слой источник напрямую зовёт
 * [DrawModifierNode.invalidateDraw] у каждого подписчика (не через snapshot-инвалидацию, а через
 * прямой вызов `requireCoordinator(Nodes.Any).invalidateLayer()` внутри самого Compose UI) —
 * безопасно вызывать из фазы draw, это ровно тот механизм, которым сам Compose инвалидирует
 * слои по своим внутренним причинам.
 *
 * Инвалидация с задержкой в один кадр (источник обновился => стекло перерисуется на СЛЕДУЮЩЕМ
 * кадре) — сознательный компромисс, тот же, на который идёт нативный iOS `UIVisualEffectView`
 * поверх скролла: не наблюдаемый на глаз при обычной частоте кадров.
 */
@Stable
class GlassBackdropState internal constructor() {
    internal var sourceLayer: GraphicsLayer? = null
        private set

    internal var sourceCoordinates: LayoutCoordinates? = null
        private set

    /**
     * `true`, пока источник записывает своё поддерево в [sourceLayer] ([GraphicsLayer.record]).
     * Стекло ВНУТРИ поддерева источника, нарисованное во время этой записи, не имеет права
     * читать слой ([LiquidGlass] обязан проверять этот флаг): `drawLayer` слоя, который в этот
     * момент записывается, уходит в бесконечную рекурсию record→draw→record и роняет нативный
     * рендерер по переполнению стека (живой SIGILL в `runSkikoComposeUiTest`-харнессе desktopTest,
     * 2026-09-17 — плитки `HomeQuickActions` с `liquidGlass()` внутри `AdaptiveScaffold`-источника).
     */
    internal var isRecording: Boolean = false
        private set

    private val glassNodes = mutableListOf<DrawModifierNode>()

    internal fun attachSource(layer: GraphicsLayer) {
        sourceLayer = layer
    }

    internal fun detachSource() {
        sourceLayer = null
        sourceCoordinates = null
    }

    internal fun updateSourceCoordinates(coordinates: LayoutCoordinates) {
        sourceCoordinates = coordinates
    }

    internal fun registerGlass(node: DrawModifierNode) {
        glassNodes += node
    }

    internal fun unregisterGlass(node: DrawModifierNode) {
        glassNodes -= node
    }

    /** Вызывается источником сразу после [GraphicsLayer.record] — см. KDoc класса. */
    internal fun notifySourceUpdated() {
        glassNodes.forEach { it.invalidateDraw() }
    }

    /** Оборачивает [GraphicsLayer.record] источника — см. KDoc [isRecording]. */
    internal inline fun <T> withRecording(block: () -> T): T {
        isRecording = true
        try {
            return block()
        } finally {
            isRecording = false
        }
    }
}

@Composable
fun rememberGlassBackdropState(): GlassBackdropState = remember { GlassBackdropState() }

/**
 * Источник для [com.aniko.ui.glass.LiquidGlass], когда стекло не получает `state` явным
 * параметром. Дефолт `null` — стекло без источника (см. [LiquidGlassStyle.fallbackAlpha]) обязано
 * деградировать в непрозрачную заливку, а не крэшиться (например, `LiquidGlassSurface` в превью/
 * тесте без обёртки [Modifier.glassBackdropSource] где-то выше по дереву).
 */
val LocalGlassBackdrop: ProvidableCompositionLocal<GlassBackdropState?> = staticCompositionLocalOf { null }

/**
 * Отмечает [state] как источник фонового контента: каждый draw-проход этого поддерева
 * дополнительно записывается в offscreen-слой, который читают подписанные [LiquidGlass]-стёкла.
 * Вешается на контейнер контента, который должен просвечивать под стеклом (например,
 * `AnixNavGraph` в Compact-ветке `AdaptiveScaffold`) — не на сам стеклянный элемент.
 */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier = this then GlassBackdropSourceElement(state)

private data class GlassBackdropSourceElement(
    private val state: GlassBackdropState,
) : ModifierNodeElement<GlassBackdropSourceNode>() {
    override fun create(): GlassBackdropSourceNode = GlassBackdropSourceNode(state)

    override fun update(node: GlassBackdropSourceNode) {
        node.state = state
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassBackdropSource"
        properties["state"] = state
    }
}

private class GlassBackdropSourceNode(
    var state: GlassBackdropState,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var layer: GraphicsLayer? = null
    private var graphicsContext: GraphicsContext? = null

    override fun onAttach() {
        val ctx = currentValueOf(LocalGraphicsContext)
        graphicsContext = ctx
        val newLayer = ctx.createGraphicsLayer()
        layer = newLayer
        state.attachSource(newLayer)
    }

    override fun onDetach() {
        layer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        layer = null
        graphicsContext = null
        state.detachSource()
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        state.updateSourceCoordinates(coordinates)
    }

    override fun ContentDrawScope.draw() {
        val currentLayer = layer
        if (currentLayer != null) {
            // Тот же приём, что у Compose-снапшот-утилит: `record { this@draw.drawContent() }` —
            // явная квалификация `this@draw` (не безымянный `this` блока, который был бы обычным
            // `DrawScope` без `drawContent()`) обязательна, см. KDoc класса про двухслойный blur.
            state.withRecording { currentLayer.record { this@draw.drawContent() } }
            state.notifySourceUpdated()
        }
        drawContent()
    }
}
