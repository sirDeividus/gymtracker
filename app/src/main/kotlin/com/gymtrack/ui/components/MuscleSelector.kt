package com.gymtrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.ui.theme.Brand

/** Etiqueta con línea guía: el texto va en el margen (izquierda o derecha) y la línea llega al músculo. */
private class Callout(val muscle: MuscleGroup, val x: Float, val y: Float, val left: Boolean)

private val frontCallouts = listOf(
    Callout(MuscleGroup.SHOULDERS, 27f, 39f, true),
    Callout(MuscleGroup.BICEPS, 22f, 58f, true),
    Callout(MuscleGroup.QUADS, 40f, 125f, true),
    Callout(MuscleGroup.CALVES, 40f, 170f, true),
    Callout(MuscleGroup.CHEST, 58f, 49f, false),
    Callout(MuscleGroup.CORE, 50f, 70f, false),
    Callout(MuscleGroup.FOREARMS, 79f, 90f, false),
)
private val backCallouts = listOf(
    Callout(MuscleGroup.SHOULDERS, 27f, 39f, true),
    Callout(MuscleGroup.TRICEPS, 22f, 58f, true),
    Callout(MuscleGroup.HAMSTRINGS, 40f, 130f, true),
    Callout(MuscleGroup.CALVES, 40f, 170f, true),
    Callout(MuscleGroup.BACK, 60f, 52f, false),
    Callout(MuscleGroup.FOREARMS, 79f, 78f, false),
    Callout(MuscleGroup.GLUTES, 59f, 102f, false),
)

/**
 * Silueta grande (frente o espalda) donde se elige un grupo muscular tocando el cuerpo o su etiqueta.
 * Los músculos se pintan con las mismas formas del mapa de músculos; el elegido, con el acento.
 */
@Composable
fun MuscleSelector(back: Boolean, onSelect: (MuscleGroup) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val zones = if (back) MuscleMapShapes.back else MuscleMapShapes.front
    val callouts = if (back) backCallouts else frontCallouts
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val total = maxWidth
        val bodyW = total * 0.5f
        val bodyH = bodyW * 2f
        val k = with(LocalDensity.current) { bodyW.toPx() } / 100f
        val bodyX = (total - bodyW) / 2
        Box(Modifier.fillMaxWidth().height(bodyH + 8.dp)) {
            Canvas(
                Modifier.offset(x = bodyX).width(bodyW).height(bodyH)
                    .pointerInput(back) {
                        detectTapGestures { p ->
                            val ux = p.x / k
                            val uy = p.y / k
                            val hit = zones.reversed().firstOrNull { z ->
                                z.muscle != null && z.shapes.any { s -> s.contains(ux, uy, 3f) }
                            }
                            hit?.muscle?.let(onSelect)
                        }
                    }
                    .semantics { contentDescription = "Silueta ${if (back) "de espalda" else "frontal"}: toca un músculo" },
            ) {
                for (z in zones) z.shapes.forEach { drawMapShape(it, c.silhouette, size.width / 100f) }
                // Los músculos se distinguen del resto con un gris algo más claro.
                for (z in zones) if (z.muscle != null) z.shapes.forEach { drawMapShape(it, c.textMuted.copy(alpha = 0.22f), size.width / 100f) }
                for (co in callouts) {
                    val x = co.x * size.width / 100f
                    val y = co.y * size.width / 100f
                    val edge = if (co.left) -bodyX.toPx() else size.width + bodyX.toPx()
                    drawLine(c.textMuted, Offset(edge, y), Offset(x, y), strokeWidth = 1.2f)
                    drawRect(c.textMuted, Offset(x - 5f, y - 5f), Size(10f, 10f), style = Stroke(1.6f))
                }
            }
            for (co in callouts) {
                val yDp = with(LocalDensity.current) { (co.y * k).toDp() }
                Text(
                    co.muscle.label,
                    style = MaterialTheme.typography.bodyMedium, color = c.text,
                    textAlign = if (co.left) TextAlign.Start else TextAlign.End,
                    modifier = Modifier
                        .align(if (co.left) Alignment.TopStart else Alignment.TopEnd)
                        .offset(y = yDp - 24.dp)
                        .width(bodyX + 6.dp)
                        .clickable { onSelect(co.muscle) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun MapShape.contains(x: Float, y: Float, pad: Float): Boolean = when (this) {
    is MapShape.Oval -> {
        val dx = (x - cx) / (rx + pad)
        val dy = (y - cy) / (ry + pad)
        dx * dx + dy * dy <= 1f
    }
    is MapShape.Rect -> x >= this.x - pad && x <= this.x + w + pad && y >= this.y - pad && y <= this.y + h + pad
}
