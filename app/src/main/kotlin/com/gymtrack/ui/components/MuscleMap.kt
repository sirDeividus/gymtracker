package com.gymtrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.ui.theme.Brand
import kotlin.math.max

/**
 * Mapa corporal (frente y espalda) con formas propias: gratis, sin recursos de terceros y con los colores de marca.
 * Silueta gris oscuro; cada músculo entrenado se pinta con el acento, con un halo suave y un leve brillo superior.
 * La intensidad es proporcional a las series (la zona con más series = máxima intensidad).
 */
@Composable
fun MuscleMap(setsByMuscle: Map<MuscleGroup, Int>, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val max = (setsByMuscle.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val summary = setsByMuscle.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(4)
        .joinToString { "${it.key.label}: ${it.value} series" }.ifEmpty { "sin series recientes" }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = 300.dp).fillMaxWidth().semantics { contentDescription = "Mapa de músculos entrenados. $summary" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BodyView(MuscleMapShapes.front, setsByMuscle, max, Modifier.weight(1f).aspectRatio(0.5f))
            BodyView(MuscleMapShapes.back, setsByMuscle, max, Modifier.weight(1f).aspectRatio(0.5f))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MENOS", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t ->
                Spacer(
                    Modifier.size(width = 22.dp, height = 8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (t == 0f) c.silhouette else trainedColor(c.silhouette, c.primary, t)),
                )
            }
            Text("MÁS", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
}

/** Entrenado = rojo desde una intensidad mínima visible hasta el acento pleno. */
private fun trainedColor(base: Color, accent: Color, t: Float): Color = lerp(base, accent, 0.45f + 0.55f * t)

@Composable
private fun BodyView(zones: List<MapZone>, sets: Map<MuscleGroup, Int>, max: Int, modifier: Modifier) {
    val c = Brand.colors
    Canvas(modifier) {
        val k = size.width / 100f
        fun intensity(m: MuscleGroup): Float = (sets[m] ?: 0).toFloat() / max

        // 1) Silueta y músculos sin entrenar: un único gris oscuro (diseño minimalista).
        for (z in zones) z.shapes.forEach { drawMapShape(it, c.silhouette, k) }

        // 2) Halo suave alrededor de lo entrenado (dos capas, sin desenfoque nativo).
        for (z in zones) {
            val m = z.muscle ?: continue
            val t = intensity(m)
            if (t <= 0f) continue
            for ((grow, alpha) in listOf(3.5f to 0.06f, 1.8f to 0.11f)) {
                z.shapes.forEach { drawMapShape(it, c.primary.copy(alpha = alpha + 0.12f * t), k, grow) }
            }
        }

        // 3) Músculo entrenado: degradado radial interno (centro intenso, borde suave), no un bloque plano.
        for (z in zones) {
            val m = z.muscle ?: continue
            val t = intensity(m)
            if (t > 0f) z.shapes.forEach { drawMapShapeRadial(it, c.primary, t, k) }
        }
    }
}

/** Relleno con degradado radial interno: opacidad alta en el centro y suave hacia el borde. */
private fun DrawScope.drawMapShapeRadial(s: MapShape, accent: Color, t: Float, k: Float) {
    val (center, radius) = when (s) {
        is MapShape.Oval -> Offset(s.cx * k, s.cy * k) to max(s.rx, s.ry) * k
        is MapShape.Rect -> Offset((s.x + s.w / 2) * k, (s.y + s.h / 2) * k) to max(s.w, s.h) / 2f * k
    }
    val brush = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.60f + 0.40f * t), accent.copy(alpha = 0.28f + 0.30f * t)),
        center = center, radius = radius * 1.05f,
    )
    when (s) {
        is MapShape.Oval -> drawOval(brush, topLeft = Offset((s.cx - s.rx) * k, (s.cy - s.ry) * k), size = Size(2 * s.rx * k, 2 * s.ry * k))
        is MapShape.Rect -> drawRoundRect(
            brush, topLeft = Offset(s.x * k, s.y * k), size = Size(s.w * k, s.h * k), cornerRadius = CornerRadius(s.r * k, s.r * k),
        )
    }
}

private fun DrawScope.drawMapShape(s: MapShape, color: Color, k: Float, grow: Float = 0f) {
    when (s) {
        is MapShape.Oval -> drawOval(
            color,
            topLeft = Offset((s.cx - s.rx - grow) * k, (s.cy - s.ry - grow) * k),
            size = Size(2 * (s.rx + grow) * k, 2 * (s.ry + grow) * k),
        )
        is MapShape.Rect -> drawRoundRect(
            color,
            topLeft = Offset((s.x - grow) * k, (s.y - grow) * k),
            size = Size((s.w + 2 * grow) * k, (s.h + 2 * grow) * k),
            cornerRadius = CornerRadius((s.r + grow) * k, (s.r + grow) * k),
        )
    }
}
