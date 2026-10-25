package com.gymtrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.ui.theme.Brand

/**
 * Mapa corporal (frente y espalda) dibujado con formas propias: gratis, sin recursos de terceros y con el color de marca.
 * La intensidad de cada zona es proporcional a las series del grupo (la zona con más series = máxima intensidad).
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
            Text("Menos", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t ->
                Spacer(
                    Modifier.size(width = 22.dp, height = 8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (t == 0f) c.outline else lerp(c.outline, c.primary, 0.25f + 0.75f * t)),
                )
            }
            Text("Más", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun BodyView(zones: List<MapZone>, sets: Map<MuscleGroup, Int>, max: Int, modifier: Modifier) {
    val c = Brand.colors
    val base = c.surfaceHigh
    val idle = c.outline
    val accent = c.primary
    Canvas(modifier) {
        val k = size.width / 100f
        for (zone in zones) {
            val m = zone.muscle
            val color = if (m == null) base else {
                val t = (sets[m] ?: 0).toFloat() / max
                if (t <= 0f) idle else lerp(idle, accent, 0.25f + 0.75f * t)
            }
            for (s in zone.shapes) when (s) {
                is MapShape.Oval -> drawOval(color, topLeft = Offset((s.cx - s.rx) * k, (s.cy - s.ry) * k), size = Size(2 * s.rx * k, 2 * s.ry * k))
                is MapShape.Rect -> drawRoundRect(color, topLeft = Offset(s.x * k, s.y * k), size = Size(s.w * k, s.h * k), cornerRadius = CornerRadius(s.r * k, s.r * k))
            }
        }
    }
}
