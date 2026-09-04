package com.aniko.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Фоновые радиальные "блобы" макета (Claude Design, `Reelwave Prototype.dc.html`) — два
 * фиксированных (не зависящих от dark/light) радиальных градиента, лежащих поверх bg-page.
 * Track A (Foundation).
 *
 * Макет задаёт их как CSS `radial-gradient` с ЭЛЛИПТИЧЕСКИМ размером и позицией в процентах
 * viewport:
 * - блоб A (фиолетовый, верх-лево): `1200×800px at 18% -10%`, база `#3D2C61`, альфа 0.35;
 * - блоб B (кримзон, верх-право): `1000×700px at 100% 0%`, база `#5B161A`, альфа 0.25.
 *
 * Compose [Brush.radialGradient] умеет только КРУГЛЫЙ градиент (один [Brush.radialGradient]
 * `radius`, не отдельные полуоси по X/Y). Приближение: радиус круга = среднее полуосей эллипса
 * (`(a+b)/2`), центр — та же процентная позиция от размера холста. Разница круг/эллипс на мягком
 * fade-to-transparent градиенте (полностью прозрачном на границе) визуально несущественна —
 * сознательное упрощение, не баг.
 */
private val BlobABase = Color(0xFF3D2C61)
private const val BLOB_A_ALPHA = 0.35f
private const val BLOB_A_CENTER_FRACTION_X = 0.18f
private const val BLOB_A_CENTER_FRACTION_Y = -0.10f

// (1200/2 + 800/2) / 2 = 500dp — среднее полуосей эллипса блоба A (1200×800px в макете).
private val BlobARadius = 500.dp

private val BlobBBase = Color(0xFF5B161A)
private const val BLOB_B_ALPHA = 0.25f
private const val BLOB_B_CENTER_FRACTION_X = 1.0f
private const val BLOB_B_CENTER_FRACTION_Y = 0.0f

// (1000/2 + 700/2) / 2 = 425dp — среднее полуосей эллипса блоба B (1000×700px в макете).
private val BlobBRadius = 425.dp

/**
 * Красит фон под контентом, к которому применён этот modifier, в текущий `background` темы
 * (bg-page) и рисует поверх два фиксированных радиальных блоба макета — см. KDoc файла. Единая
 * точка применения — корневой фон приложения (см. [AppTheme]), должен быть виден под ВСЕМ
 * контентом на всех экранах (Foundation-трек, не только Home).
 */
@Composable
fun Modifier.anixAppBackground(): Modifier {
    val pageColor = MaterialTheme.colorScheme.background
    return this.drawWithCache {
        val blobABrush =
            Brush.radialGradient(
                colors = listOf(BlobABase.copy(alpha = BLOB_A_ALPHA), Color.Transparent),
                center = Offset(size.width * BLOB_A_CENTER_FRACTION_X, size.height * BLOB_A_CENTER_FRACTION_Y),
                radius = BlobARadius.toPx(),
            )
        val blobBBrush =
            Brush.radialGradient(
                colors = listOf(BlobBBase.copy(alpha = BLOB_B_ALPHA), Color.Transparent),
                center = Offset(size.width * BLOB_B_CENTER_FRACTION_X, size.height * BLOB_B_CENTER_FRACTION_Y),
                radius = BlobBRadius.toPx(),
            )
        onDrawBehind {
            drawRect(color = pageColor)
            drawRect(brush = blobABrush)
            drawRect(brush = blobBBrush)
        }
    }
}
