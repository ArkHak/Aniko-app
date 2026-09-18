@file:Suppress("TooManyFunctions", "MagicNumber")
// Весь файл — точный векторный порт дизайнерского HTML5 Canvas-мокапа (см. KDoc
// [AnimeImagePlaceholder]): каждая координата/цвет/коэффициент кривой ниже — не произвольное
// число, а конкретная точка чертежа референса, транскрибированная 1:1 (осознанное решение после
// двух отклонённых попыток — «переосмысленная с нуля» версия и статичный PNG — обе не устроили
// владельца продукта; см. историю коммитов на этой ветке). Компонент разложен на много маленьких
// приватных build*/draw*-функций по одной детали иллюстрации (фон, торий, лицо, лепестки,
// спиннер) вместо одной гигантской функции — отсюда общее число функций в файле.

package com.aniko.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Аниме-заглушка под картинки: чиби-персонаж на фоне тории и кружащихся лепестков сакуры, со
 * спиннером внизу — процедурная Compose `Canvas`-иллюстрация, отрисованная кодом (не растровый
 * ассет).
 *
 * Это **третья** итерация компонента на этой ветке. Первая («переосмысленная с нуля» силуэтная
 * версия) и вторая (статичный PNG) были отклонены владельцем продукта — первая выглядела
 * недостаточно похожей на референс, вторая не масштабировалась чисто и не была анимирована. Эта
 * версия — **точный порт** дизайнерского HTML5 Canvas-мокапа (координаты/кривые/градиенты/
 * формулы анимации транскрибированы как есть, без переосмысления пропорций), поэтому все
 * приватные `build*`-функции ниже содержат десятки "магических" числовых литералов — это
 * сознательное решение (см. `@file:Suppress` выше), а не небрежность.
 *
 * ### Техника: два слоя с разным масштабированием
 * Персонаж, тории, лепестки и спиннер рисуются в фиксированной системе координат [SCENE_SIZE]×
 * [SCENE_SIZE] — тех же величинах, что и в референсе (`S = canvas.width = canvas.height = 1254`).
 * В [drawAnimeScene] к этой системе применяется равномерный масштаб (`scale(actual/1254, actual/
 * 1254)`, где `actual = min(ширина, высота) контейнера`) — тот же принцип, что и `viewBox` у SVG,
 * без искажения пропорций лица/тория на любом размере контейнера (маленький постер, hero-баннер).
 *
 * Фон-плашка (тёмный градиент + скруглённый прямоугольник + радиальное свечение,
 * [BackgroundLayer]/[drawBackgroundLayer]), наоборот, считается напрямую в РЕАЛЬНЫХ пикселях
 * `size.width`/`size.height` контейнера, а не через этот масштаб-трансформ — иначе на неквадратном
 * контейнере (постер 2:3, широкий hero-баннер) равномерный масштаб от `min(width, height)` красит
 * только центральный квадрат сцены, а по краям остаются незакрашенные letterbox-полосы (баг,
 * найденный владельцем продукта на реальных карточках/баннерах). Скруглённый угол плашки остаётся
 * пропорциональным `min(width, height)` (тот же принцип "viewBox", что и у персонажа), но сам
 * прямоугольник и его градиенты растянуты на полный `size` контейнера — см. KDoc
 * [BackgroundLayer] о пересчёте по размеру.
 *
 * ### Анимация
 * Пока [loading] == true, единственный линейно растущий параметр `t` (секунды, как `phase = ms /
 * 1000` в референсе на `requestAnimationFrame`) двигает 8 точек спиннера ([drawSpinner]) и 6
 * лепестков сакуры ([drawPetal]) по тем же формулам, что и в референсе — см. их KDoc. Всё
 * остальное (фон, торий, лицо) визуально статично и не зависит от `t`.
 *
 * Когда [loading] == false (состояние ошибки), [rememberInfiniteTransition]/`animateFloat` вообще
 * не создаются — ветка `else` ниже рисует один статичный кадр (`t = 0`) без подписки на кадровый
 * таймер, поэтому неанимированная заглушка не создаёт лишней нагрузки в сетке.
 *
 * ### Производительность
 * Все `Path`/`Brush`, которые не двигаются кадр к кадру (фон, торий, силуэт/глаза/рот лица,
 * форма и градиент каждого лепестка, база спиннера) собраны один раз в [AnimeSceneAssets] через
 * `remember` в [AnimeImagePlaceholder] — независимо от размера контейнера, так как вся геометрия
 * живёт в неизменной виртуальной системе координат 1254×1254 (см. выше), а не в пикселях экрана.
 * На каждый кадр пересчитываются только числа реально анимированных величин (позиции/повороты
 * лепестков и спиннера) внутри лямбды `Canvas { }` — без recomposition/relayout: `Canvas` — это
 * `Spacer`-подобный layout-узел, перерисовка кадра не проходит через измерение/расстановку дерева
 * Compose заново.
 *
 * Публичная сигнатура не меняется преднамеренно — используется как `loading`/`error`-слот
 * [AnixAsyncImage] без изменений на его стороне. Компонент чисто декоративный:
 * `contentDescription` не требуется, никакой семантики не добавляет.
 *
 * Базовые цвета (тёмный фон, спиннер, лепестки, лицо) взяты как есть из референса и не привязаны
 * к `MaterialTheme.colorScheme` — заглушка тёмная по дизайну независимо от темы приложения (то же
 * решение, что и в первой итерации), точность копии референса приоритетнее интеграции с темой.
 */
@Composable
fun AnimeImagePlaceholder(
    modifier: Modifier = Modifier,
    loading: Boolean = true,
) {
    val assets = remember { buildAnimeSceneAssets() }
    val backgroundLayer = remember { BackgroundLayer() }
    if (loading) {
        val phase = rememberAnimePlaceholderPhase()
        Canvas(modifier = modifier.fillMaxSize()) {
            drawAnimeScene(assets = assets, backgroundLayer = backgroundLayer, phase = phase)
        }
    } else {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawAnimeScene(assets = assets, backgroundLayer = backgroundLayer, phase = STATIC_FRAME_PHASE)
        }
    }
}

/**
 * Линейно растущий параметр `t` (в секундах) — прямой аналог `phase = ms / 1000`, копящегося в
 * референсе через `requestAnimationFrame`. Технически это `animateFloat` от 0 до
 * [PHASE_CYCLE_SECONDS] с `RepeatMode.Restart` за [PHASE_CYCLE_DURATION_MS] мс, то есть со
 * скоростью ровно 1 единица `t` в секунду — так что за время реального цикла (час) визуального
 * разрыва на перезапуске практически не заметить: сама заглушка на экране столько не живёт.
 *
 * Вызывается только пока `loading == true` (см. [AnimeImagePlaceholder]) — единственная точка
 * подписки на кадровый таймер во всём компоненте.
 */
@Composable
private fun rememberAnimePlaceholderPhase(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "animePlaceholderPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = PHASE_CYCLE_SECONDS,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = PHASE_CYCLE_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "animePlaceholderPhaseValue",
    )
    return phase
}

/**
 * Всё, что не зависит от кадра и от размера контейнера: собранные один раз [Path]/[Brush]
 * статичных деталей иллюстрации персонажа (торий, силуэт/глаза/рот лица) плюс "чертёж" каждого из
 * 6 лепестков сакуры (локальная форма лепестка + его радиальный градиент уже зависят только от
 * радиуса конкретного лепестка, который не анимируется — двигаются только позиция/поворот, см.
 * [drawPetal]). [backgroundPath] — маска-контур виртуальной сцены 1254×1254 (используется только
 * как clip для слоя персонажа); сама заливка фона (градиент+свечение) больше не живёт здесь —
 * она зависит от РЕАЛЬНОГО размера контейнера, а не от неизменной виртуальной сцены, поэтому
 * вынесена в отдельный, зависящий от размера кэш [BackgroundLayer].
 */
private data class AnimeSceneAssets(
    val backgroundPath: Path,
    val toriiPath: Path,
    val faceOutlinePaths: List<Path>,
    val faceOutlineBrush: Brush,
    val eyeBrush: Brush,
    val eyes: List<EyeGeometry>,
    val eyeSparklePath: Path,
    val noseAndMouthPath: Path,
    val petals: List<PetalAsset>,
)

/** Геометрия одного глаза: эллипс `(cx, cy, rx, ry)`, повёрнутый на [rotationDegrees]. */
private data class EyeGeometry(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val rotationDegrees: Float,
)

/**
 * "Чертёж" одного лепестка: [path] и [brush] построены под конкретный радиус лепестка и переживают
 * все кадры без изменений — анимируются только [baseX]/[baseY]/[baseAngle] в [drawPetal] (дрейф
 * позиции и поворот вокруг локального нуля пути).
 */
private data class PetalAsset(
    val baseX: Float,
    val baseY: Float,
    val baseAngle: Float,
    val alpha: Float,
    val path: Path,
    val brush: Brush,
)

private fun buildAnimeSceneAssets(): AnimeSceneAssets =
    AnimeSceneAssets(
        backgroundPath = buildBackgroundPath(),
        toriiPath = buildToriiPath(),
        faceOutlinePaths = buildFaceOutlinePaths(),
        faceOutlineBrush = buildFaceOutlineBrush(),
        eyeBrush = buildEyeBrush(),
        eyes = buildEyeGeometries(),
        eyeSparklePath = buildEyeSparklePath(),
        noseAndMouthPath = buildNoseAndMouthPath(),
        petals = buildPetalAssets(),
    )

/**
 * `roundedRect(0, 0, S, S, 82)` референса — скруглённая маска-контур виртуальной сцены. Больше не
 * закрашивается напрямую (см. [BackgroundLayer]): используется только как clip-маска для слоя
 * персонажа в [drawAnimeScene], поэтому остаётся в фиксированной виртуальной системе координат
 * [SCENE_SIZE]×[SCENE_SIZE], как и весь остальной [AnimeSceneAssets].
 */
private fun buildBackgroundPath(): Path =
    roundedRectPath(x = 0f, y = 0f, w = SCENE_SIZE, h = SCENE_SIZE, r = BACKGROUND_CORNER_RADIUS_VIRTUAL)

/** `drawTorii()` референса: 6 непересекающихся скруглённых прямоугольников одним [Path]. */
private fun buildToriiPath(): Path =
    Path().apply {
        addPath(roundedRectPath(287f, 322f, 680f, 34f, 17f))
        addPath(roundedRectPath(326f, 355f, 602f, 20f, 10f))
        addPath(roundedRectPath(375f, 357f, 52f, 295f, 18f))
        addPath(roundedRectPath(827f, 357f, 52f, 295f, 18f))
        addPath(roundedRectPath(346f, 485f, 110f, 18f, 9f))
        addPath(roundedRectPath(798f, 485f, 110f, 18f, 9f))
    }

/**
 * Три открытых (не замкнутых) `stroke`-пути силуэта лица из `drawFace()` референса: контур
 * волос+чёлки (зигзаг из `lineTo`), линия подбородка/щёк и внутренний зигзаг бровей/носа. Порядок
 * `moveTo`/`bezierCurveTo`/`lineTo` — 1:1 с референсом.
 */
private fun buildFaceOutlinePaths(): List<Path> {
    val hairSilhouette =
        Path().apply {
            moveTo(394f, 650f)
            cubicTo(342f, 535f, 371f, 398f, 470f, 344f)
            lineTo(448f, 287f)
            lineTo(531f, 322f)
            lineTo(572f, 240f)
            lineTo(625f, 316f)
            lineTo(702f, 252f)
            lineTo(721f, 334f)
            lineTo(810f, 305f)
            lineTo(779f, 382f)
            cubicTo(875f, 440f, 894f, 555f, 851f, 650f)
        }
    val jawline =
        Path().apply {
            moveTo(393f, 586f)
            cubicTo(395f, 753f, 470f, 860f, 627f, 873f)
            cubicTo(784f, 860f, 859f, 753f, 861f, 586f)
        }
    val browsAndNose =
        Path().apply {
            moveTo(402f, 510f)
            cubicTo(458f, 445f, 504f, 440f, 548f, 464f)
            lineTo(530f, 548f)
            lineTo(609f, 471f)
            lineTo(638f, 560f)
            lineTo(714f, 461f)
            lineTo(735f, 547f)
            cubicTo(769f, 479f, 811f, 480f, 851f, 531f)
        }
    return listOf(hairSilhouette, jawline, browsAndNose)
}

/** `outline` линейный градиент референса: `(350,350)->(900,870)`, `#d4b5ff/#83e5ff(.48)/#ff9bd4`. */
private fun buildFaceOutlineBrush(): Brush =
    Brush.linearGradient(
        0f to Color(0xFFD4B5FF),
        .48f to Color(0xFF83E5FF),
        1f to Color(0xFFFF9BD4),
        start = Offset(350f, 350f),
        end = Offset(900f, 870f),
    )

/** `eye` линейный градиент референса: `(440,540)->(790,740)`, `#b8f5ff/#9ea0ff(.5)/#ed9be4`. */
private fun buildEyeBrush(): Brush =
    Brush.linearGradient(
        0f to Color(0xFFB8F5FF),
        .5f to Color(0xFF9EA0FF),
        1f to Color(0xFFED9BE4),
        start = Offset(440f, 540f),
        end = Offset(790f, 740f),
    )

/** `ctx.ellipse(x, 650, 73, 108, ±.12, 0, 2π)` референса для `x in [505, 748]`. */
private fun buildEyeGeometries(): List<EyeGeometry> =
    listOf(
        EyeGeometry(cx = 505f, cy = 650f, rx = 73f, ry = 108f, rotationDegrees = -.12f * RADIANS_TO_DEGREES),
        EyeGeometry(cx = 748f, cy = 650f, rx = 73f, ry = 108f, rotationDegrees = .12f * RADIANS_TO_DEGREES),
    )

/**
 * Блик-«звёздочка» глаза: у круглого блика (`ctx.arc`) референса на самом PNG-рисунке есть
 * компаньон — маленький 4-лучевой искристый блик (в JS-коде отдельной функцией не выделен, но
 * ясно виден на `<design-reference-image>`, см. KDoc [AnimeImagePlaceholder]) —
 * без него глаза читаются как плоские залитые овалы, а не характерный "искристый" акцент
 * референса. 8 вершин, чередующих [EYE_SPARKLE_OUTER_RADIUS]/[EYE_SPARKLE_INNER_RADIUS] через
 * 45°, локально относительно `(0, 0)` — позиционируется трансформом в [drawEyes].
 */
private fun buildEyeSparklePath(): Path {
    val path = Path()
    for (i in 0 until 8) {
        val radius = if (i % 2 == 0) EYE_SPARKLE_OUTER_RADIUS else EYE_SPARKLE_INNER_RADIUS
        val angle = -PI.toFloat() / 2f + i * (PI.toFloat() / 4f)
        val x = cos(angle) * radius
        val y = sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Носик-галочка (`moveTo(627,699)->lineTo(615,724)->lineTo(634,724)`) и улыбка-дуга
 * (`arc(627,755,32, .12π, .88π)`) референса — два подпути одного [Path], стро́ятся и обводятся
 * вместе одним и тем же стилем `#ffc4e1`/13px.
 */
private fun buildNoseAndMouthPath(): Path =
    Path().apply {
        moveTo(627f, 699f)
        lineTo(615f, 724f)
        lineTo(634f, 724f)
        arcTo(
            rect = Rect(left = 595f, top = 723f, right = 659f, bottom = 787f),
            startAngleDegrees = .12f * 180f,
            sweepAngleDegrees = (.88f - .12f) * 180f,
            forceMoveTo = true,
        )
    }

/**
 * 6 лепестков сакуры из референса (`[x, y, r, angle, alpha]`) — базовые позиции/радиус/поворот/
 * альфа не меняются, у каждого свои [PetalAsset.path]/[PetalAsset.brush] (зависят только от `r`).
 * Дрейф и вращение считаются каждый кадр в [drawPetal] по индексу `i` этого списка.
 */
private fun buildPetalAssets(): List<PetalAsset> =
    listOf(
        petalBlueprint(x = 241f, y = 277f, r = 24f, angle = .48f, alpha = .84f),
        petalBlueprint(x = 1002f, y = 382f, r = 19f, angle = 2.18f, alpha = .68f),
        petalBlueprint(x = 938f, y = 735f, r = 28f, angle = .62f, alpha = .76f),
        petalBlueprint(x = 254f, y = 796f, r = 20f, angle = 3.2f, alpha = .64f),
        petalBlueprint(x = 332f, y = 204f, r = 15f, angle = 4.0f, alpha = .5f),
        petalBlueprint(x = 1040f, y = 625f, r = 14f, angle = 5.1f, alpha = .45f),
    )

private fun petalBlueprint(
    x: Float,
    y: Float,
    r: Float,
    angle: Float,
    alpha: Float,
): PetalAsset =
    PetalAsset(
        baseX = x,
        baseY = y,
        baseAngle = angle,
        alpha = alpha,
        path = buildPetalPath(r),
        brush = buildPetalBrush(r),
    )

/** `petal()` референса: путь из двух кубических Безье-кривых, локально относительно `(0, 0)`. */
private fun buildPetalPath(r: Float): Path =
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(r * .2f, -r * 1.05f, r * 1.2f, -r * .76f, r * .84f, 0f)
        cubicTo(r * 1.2f, r * .76f, r * .2f, r * 1.05f, 0f, 0f)
        close()
    }

/**
 * `createRadialGradient(-r*.2, -r*.25, 1, 0, 0, r*1.2)` референса — двухкруговой (со смещённым
 * фокусом) градиент, у Compose `radialGradient` только один круг, поэтому фокус аппроксимирован
 * смещённым центром: `center = (-r*.2, -r*.25)`, `radius = r*1.2` — визуально даёт тот же блик у
 * верхнего левого края лепестка.
 */
private fun buildPetalBrush(r: Float): Brush =
    Brush.radialGradient(
        0f to Color(0xFFFFE4F0),
        .56f to Color(0xFFF99DCA),
        1f to Color(0xFFC664B4),
        center = Offset(-r * .2f, -r * .25f),
        radius = r * 1.2f,
    )

/** `roundedRect(x, y, w, h, r)` референса — радиус клампится так же, как в `Math.min(r, w/2, h/2)`. */
private fun roundedRectPath(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    r: Float,
): Path {
    val clampedRadius = minOf(r, w / 2f, h / 2f)
    val roundRect =
        RoundRect(left = x, top = y, right = x + w, bottom = y + h, radiusX = clampedRadius, radiusY = clampedRadius)
    return Path().apply { addRoundRect(roundRect) }
}

/**
 * Кэш фонового слоя (плашка + линейный градиент + радиальное свечение), пересчитываемого В
 * РЕАЛЬНЫХ пикселях контейнера (`size.width`/`size.height` из `DrawScope`) — в отличие от
 * [AnimeSceneAssets], где вся геометрия живёт в неизменной виртуальной сцене [SCENE_SIZE]×
 * [SCENE_SIZE] и не зависит от формы контейнера.
 *
 * Причина раздельного слоя (найдено владельцем продукта на реальных карточках/hero-баннерах,
 * см. KDoc [AnimeImagePlaceholder]): единый масштаб-трансформ персонажа берёт `min(width, height)`
 * — на неквадратном контейнере это красит только центральный квадрат, а по бокам/сверху-снизу
 * остаются пустые незакрашенные letterbox-полосы. Фон вместо этого считается напрямую под полный
 * `size`, поэтому не может оставить пустот независимо от пропорций контейнера.
 *
 * [ensureFor] пересобирает [path]/[backgroundBrush]/[glowBrush] только когда [size] реально
 * меняется (тот же принцип "не аллоцировать то, что не двигается кадр к кадру", что и у
 * [AnimeSceneAssets] — см. KDoc [AnimeImagePlaceholder]); размер контейнера практически никогда не
 * меняется кадр к кадру анимации (только при живом ресайзе окна), так что в подавляющем
 * большинстве кадров это дешёвая проверка на равенство без аллокаций.
 */
private class BackgroundLayer {
    var size: Size = Size.Unspecified
    var path: Path = Path()
    var backgroundBrush: Brush = SolidColor(Color.Transparent)
    var glowBrush: Brush = SolidColor(Color.Transparent)
}

/**
 * Пересчитывает [BackgroundLayer] под новый размер контейнера — не выполняет никакой работы,
 * если размер не менялся с прошлого кадра. Нулевой/отрицательный `size` (например, во время
 * первого прохода измерения с нулевыми constraints — известный класс багов на этой кодовой базе)
 * оставляет [BackgroundLayer.path] пустым, а не падает — [drawBackgroundLayer] тогда просто ничего
 * не рисует, как и раньше вела себя нулевая виртуальная сцена.
 */
private fun BackgroundLayer.ensureFor(newSize: Size) {
    if (size == newSize) return
    size = newSize
    if (newSize.width <= 0f || newSize.height <= 0f) {
        path = Path()
        return
    }
    val minSide = min(newSize.width, newSize.height)
    val cornerRadius = BACKGROUND_CORNER_RADIUS_VIRTUAL / SCENE_SIZE * minSide
    path = roundedRectPath(x = 0f, y = 0f, w = newSize.width, h = newSize.height, r = cornerRadius)
    // `bg` линейный градиент референса: стопы `#171631 / #251846(.5) / #0b1930`, растянутые на
    // полную диагональ РЕАЛЬНОГО контейнера (было `(0,0)->(S,S)` виртуальной сцены).
    backgroundBrush =
        Brush.linearGradient(
            0f to Color(0xFF171631),
            .5f to Color(0xFF251846),
            1f to Color(0xFF0B1930),
            start = Offset.Zero,
            end = Offset(newSize.width, newSize.height),
        )
    // `glow` радиальный градиент референса: центр `(C, 580)`, `r1=600` в виртуальной сцене —
    // здесь центр пересчитан пропорционально РЕАЛЬНОМУ размеру (по X — центр контейнера, по Y —
    // та же относительная доля высоты, что и `580/1254` в виртуальной сцене), а радиус — от
    // `minSide`, как и масштаб персонажа, чтобы свечение осталось тем же соразмерным пятном
    // позади лица независимо от пропорций контейнера (а не растягивалось эллипсом на широком
    // баннере). `r0` (внутренний круг референса) опущен по той же причине, что и раньше — см.
    // историю в KDoc [AnimeSceneAssets] `glow`-версии до этого рефакторинга.
    glowBrush =
        Brush.radialGradient(
            0f to Color(0xFF826AFF).copy(alpha = .33f),
            .48f to Color(0xFF5C47B8).copy(alpha = .11f),
            1f to Color(0xFF11112B).copy(alpha = 0f),
            center = Offset(newSize.width / 2f, newSize.height * (GLOW_CENTER_Y_VIRTUAL / SCENE_SIZE)),
            radius = (GLOW_RADIUS_VIRTUAL / SCENE_SIZE * minSide).coerceAtLeast(1f),
        )
}

private fun DrawScope.drawBackgroundLayer(layer: BackgroundLayer) {
    if (layer.size.width <= 0f || layer.size.height <= 0f) return
    clipPath(layer.path) {
        drawPath(path = layer.path, brush = layer.backgroundBrush)
        drawRect(brush = layer.glowBrush, topLeft = Offset.Zero, size = size)
    }
}

/**
 * `draw(t)` референса целиком, разложенный на два слоя с разным масштабированием (см. KDoc
 * [AnimeImagePlaceholder]):
 * 1. Фон ([drawBackgroundLayer]) — плашка + градиент + radial-glow заливка, В РЕАЛЬНЫХ пикселях
 *    контейнера, полностью покрывает `size` без letterbox-пустот на неквадратных пропорциях.
 * 2. Персонаж (торий → лепестки → лицо → спиннер) — как и раньше, в виртуальной системе координат
 *    [SCENE_SIZE]×[SCENE_SIZE], один раз отмасштабированной под `min(ширина, высота)` контейнера
 *    и отцентрированной — без искажения пропорций лица/тория. Отрисовка обрезана по форме
 *    [AnimeSceneAssets.backgroundPath] — референс полагался на внешний CSS `border-radius` канваса,
 *    чтобы спрятать за его пределами прямоугольную radial-заливку (`ctx.fillRect` без
 *    `ctx.clip()`); здесь этот внешний слой воспроизведён явным `clipPath`.
 */
private fun DrawScope.drawAnimeScene(
    assets: AnimeSceneAssets,
    backgroundLayer: BackgroundLayer,
    phase: Float,
) {
    backgroundLayer.ensureFor(size)
    drawBackgroundLayer(backgroundLayer)

    val scaleFactor = min(size.width, size.height) / SCENE_SIZE
    val renderedSize = SCENE_SIZE * scaleFactor
    val offsetX = (size.width - renderedSize) / 2f
    val offsetY = (size.height - renderedSize) / 2f
    withTransform({
        translate(left = offsetX, top = offsetY)
        scale(scaleX = scaleFactor, scaleY = scaleFactor, pivot = Offset.Zero)
    }) {
        clipPath(assets.backgroundPath) {
            drawTorii(assets)
            assets.petals.forEachIndexed { index, petal -> drawPetal(petal, phase, index) }
            drawFace(assets)
            drawSpinner(phase)
        }
    }
}

private fun DrawScope.drawTorii(assets: AnimeSceneAssets) {
    drawPath(path = assets.toriiPath, color = Color(0xFFFF7197), alpha = .23f)
}

/**
 * `petal(x + sin(t+i)*8, y + cos(t*.7+i)*6, r, angle + t*.18, alpha)` референса — дрейф позиции и
 * поворот вокруг локального нуля пути лепестка (см. [buildPetalPath]); `i` — индекс лепестка в
 * [AnimeSceneAssets.petals], как и в референсе (`petals.forEach((..., i) => ...)`).
 */
private fun DrawScope.drawPetal(
    petal: PetalAsset,
    phase: Float,
    index: Int,
) {
    val driftX = petal.baseX + sin(phase + index) * PETAL_DRIFT_X_AMPLITUDE
    val driftY = petal.baseY + cos(phase * PETAL_DRIFT_Y_TIME_SCALE + index) * PETAL_DRIFT_Y_AMPLITUDE
    val angleDegrees = (petal.baseAngle + phase * PETAL_ROTATION_SPEED) * RADIANS_TO_DEGREES
    withTransform({
        translate(left = driftX, top = driftY)
        rotate(degrees = angleDegrees, pivot = Offset.Zero)
    }) {
        drawPath(path = petal.path, brush = petal.brush, alpha = petal.alpha)
    }
}

/**
 * Статичная часть `drawFace()` референса: силуэт+глаза+нос/рот. `ctx.shadowColor = '#9b7cff'`
 * (референс) — сквозной мягкий ореол вокруг линий — у `DrawScope` нет прямого аналога
 * `shadowBlur`, поэтому он аппроксимирован через [drawStrokeGlow] (несколько всё более широких и
 * прозрачных проходов того же пути под финальным чётким штрихом — стандартный дешёвый приём
 * "свечения" в Canvas без настоящего гауссова блюра).
 */
private fun DrawScope.drawFace(assets: AnimeSceneAssets) {
    drawStrokeGlow(assets.faceOutlinePaths, FACE_STROKE_WIDTH, FACE_GLOW_COLOR, FACE_GLOW_RADIUS_OUTER)
    assets.faceOutlinePaths.forEach { path ->
        drawPath(
            path = path,
            brush = assets.faceOutlineBrush,
            style = Stroke(width = FACE_STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    drawEyes(assets)
    drawStrokeGlow(listOf(assets.noseAndMouthPath), NOSE_MOUTH_STROKE_WIDTH, FACE_GLOW_COLOR, FACE_GLOW_RADIUS_INNER)
    drawPath(
        path = assets.noseAndMouthPath,
        color = Color(0xFFFFC4E1),
        style = Stroke(width = NOSE_MOUTH_STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Глаза референса: залитый+обведённый эллипс (повёрнутый на `EyeGeometry.rotationDegrees`,
 * ровно как параметр вращения `ctx.ellipse()`, а не canvas-трансформ — поэтому блики ниже рисуются
 * уже ВНЕ этого `withTransform`, как и в референсе, где поворот эллипса не затрагивает следующий
 * за ним `ctx.arc()`) плюс два блика — круглый (`cx-20, 615`, из кода) и 4-лучевая звёздочка
 * (`cx+16, 600`, см. KDoc [buildEyeSparklePath]) — оба смещены НЕ зеркально, а одинаковым
 * фиксированным сдвигом для обоих глаз, тем же приёмом, что и `x - 20` в референсе.
 */
private fun DrawScope.drawEyes(assets: AnimeSceneAssets) {
    assets.eyes.forEach { eye ->
        withTransform({ rotate(degrees = eye.rotationDegrees, pivot = Offset(eye.cx, eye.cy)) }) {
            val topLeft = Offset(eye.cx - eye.rx, eye.cy - eye.ry)
            val ovalSize = Size(eye.rx * 2f, eye.ry * 2f)
            drawOval(brush = assets.eyeBrush, topLeft = topLeft, size = ovalSize)
            drawOval(color = Color(0xFFF5D6FF), topLeft = topLeft, size = ovalSize, style = Stroke(width = 13f))
        }
        drawCircle(
            color = Color(0xFFF8FDFF),
            radius = 18f,
            center = Offset(eye.cx - 20f, 615f),
            alpha = .78f,
        )
        withTransform({ translate(left = eye.cx + EYE_SPARKLE_OFFSET_X, top = EYE_SPARKLE_OFFSET_Y) }) {
            drawPath(path = assets.eyeSparklePath, color = Color(0xFFFFFFFF), alpha = .92f)
        }
    }
}

/**
 * `drawSpinner(t)` референса 1:1: 8 точек, `angle = 2π·i/8 + t`,
 * `strength = 0.17 + 0.83·max(0, cos(t·2 − i·0.78))` (используется и как альфа, и косвенно как
 * "яркость"), цвет чередуется между голубым (`i` нечётный) и розовым (`i` чётный), позиция
 * `(C + cos(angle)·r, spinnerCenterY + sin(angle)·r)`.
 */
private fun DrawScope.drawSpinner(phase: Float) {
    for (i in 0 until SPINNER_DOT_COUNT) {
        val angle = 2f * PI.toFloat() * i / SPINNER_DOT_COUNT + phase
        val cosArgument = phase * SPINNER_STRENGTH_TIME_SCALE - i * SPINNER_STRENGTH_INDEX_SCALE
        val strength = SPINNER_STRENGTH_BASE + SPINNER_STRENGTH_AMPLITUDE * max(0f, cos(cosArgument))
        val color = if (i % 2 == 1) Color(0xFF81E8FF) else Color(0xFFFFA3D7)
        drawCircle(
            color = color,
            radius = SPINNER_DOT_RADIUS,
            center = Offset(SCENE_CENTER + cos(angle) * SPINNER_RADIUS, SPINNER_CENTER_Y + sin(angle) * SPINNER_RADIUS),
            alpha = strength,
        )
    }
}

/**
 * Дешёвая аппроксимация `ctx.shadowColor`/`ctx.shadowBlur` референса: [GLOW_PASSES] проходов того
 * же (списка) путей с растущей шириной штриха и убывающей альфой под финальным чётким штрихом —
 * без настоящего гауссова блюра (в `DrawScope` нет кросс-платформенного эквивалента), но с тем же
 * визуальным эффектом мягкого ореола вокруг линии.
 */
private fun DrawScope.drawStrokeGlow(
    paths: List<Path>,
    baseStrokeWidth: Float,
    glowColor: Color,
    glowRadius: Float,
) {
    for (step in GLOW_PASSES downTo 1) {
        val progress = step / GLOW_PASSES.toFloat()
        val stroke =
            Stroke(width = baseStrokeWidth + glowRadius * progress, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val passAlpha = GLOW_MAX_ALPHA * (1f - progress) * (1f - progress)
        paths.forEach { path -> drawPath(path = path, color = glowColor, alpha = passAlpha, style = stroke) }
    }
}

/** Размер виртуального холста — тот же `S = canvas.width = canvas.height` референса. */
private const val SCENE_SIZE = 1254f

/** `C = S / 2` референса. */
private const val SCENE_CENTER = 627f

/**
 * `roundedRect(0, 0, S, S, 82)` референса — скругление угла плашки/фона, в виртуальных единицах
 * сцены. Используется и как радиус clip-маски [buildBackgroundPath] персонажа, и (пересчитанный
 * пропорционально от `min(width, height)`) как радиус реального фонового слоя [BackgroundLayer].
 */
private const val BACKGROUND_CORNER_RADIUS_VIRTUAL = 82f

/** `glow` референса: `createRadialGradient(C, 580, ...)` — координата Y центра в виртуальных единицах. */
private const val GLOW_CENTER_Y_VIRTUAL = 580f

/** `glow` референса: `r1 = 600` — внешний радиус свечения в виртуальных единицах. */
private const val GLOW_RADIUS_VIRTUAL = 600f

/** Кадр, рисуемый один раз в состоянии ошибки (`loading == false`) — без подписки на анимацию. */
private const val STATIC_FRAME_PHASE = 0f

/** Длительность одного цикла `t` — см. KDoc [rememberAnimePlaceholderPhase]. */
private const val PHASE_CYCLE_SECONDS = 3600f
private const val PHASE_CYCLE_DURATION_MS = 3_600_000

private const val FACE_STROKE_WIDTH = 19f
private const val NOSE_MOUTH_STROKE_WIDTH = 13f
private val FACE_GLOW_COLOR = Color(0xFF9B7CFF)

// Радиус/альфа заметно меньше "сырого" shadowBlur референса (44/24) — там это НАСТОЯЩИЙ
// гауссов блюр (энергия быстро спадает у самого края штриха), здесь — стопка всё более широких
// полупрозрачных проходов (см. drawStrokeGlow), которая при радиусе 44 и альфе 0.55 превращалась
// в широкую плотную полосу и полностью забивала собой столбы тория (силуэт волос проходит прямо
// над ними) — визуально совпадать с референсом важнее, чем с буквальным числом 44.
private const val FACE_GLOW_RADIUS_OUTER = 16f
private const val FACE_GLOW_RADIUS_INNER = 9f
private const val GLOW_PASSES = 4
private const val GLOW_MAX_ALPHA = .18f

private const val EYE_SPARKLE_OUTER_RADIUS = 15f
private const val EYE_SPARKLE_INNER_RADIUS = 5f
private const val EYE_SPARKLE_OFFSET_X = 16f
private const val EYE_SPARKLE_OFFSET_Y = 600f

private const val SPINNER_DOT_COUNT = 8
private const val SPINNER_RADIUS = 101f
private const val SPINNER_DOT_RADIUS = 14f
private const val SPINNER_CENTER_Y = 1030f
private const val SPINNER_STRENGTH_BASE = .17f
private const val SPINNER_STRENGTH_AMPLITUDE = .83f
private const val SPINNER_STRENGTH_TIME_SCALE = 2f
private const val SPINNER_STRENGTH_INDEX_SCALE = .78f

private const val PETAL_DRIFT_X_AMPLITUDE = 8f
private const val PETAL_DRIFT_Y_AMPLITUDE = 6f
private const val PETAL_DRIFT_Y_TIME_SCALE = .7f
private const val PETAL_ROTATION_SPEED = .18f

/** `rotate()`/`angle * RAD_TO_DEG`: референс работает в радианах, `DrawTransform.rotate` — в градусах. */
private val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()
