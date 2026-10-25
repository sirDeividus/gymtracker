package com.gymtrack.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.TrackingType
import com.gymtrack.ui.components.BarChart
import com.gymtrack.ui.components.ChipSelector
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MuscleMap
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.theme.Brand

@Composable
fun ProgressScreen(vm: ProgressViewModel, contentPadding: PaddingValues, onOpenExercise: (String) -> Unit) {
    val c = Brand.colors
    val st by vm.state.collectAsStateWithLifecycle()
    val unit = st.settings.unit
    var metricChoice by remember { mutableStateOf<Metric?>(null) }
    val metrics = metricsFor(st.selectedTracking)
    val metric = metricChoice?.takeIf { it in metrics } ?: metrics.first()
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHeader("Progreso")
        if (!st.hasData && !st.loading) {
            EmptyState(
                Icons.Default.ShowChart, "Aún no hay datos que mostrar",
                "Finaliza tu primer entrenamiento con series completadas y aquí verás la evolución de tus cargas, tu frecuencia semanal, las series por músculo y tus récords.",
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ChipSelector(Period.values().toList(), setOf(st.period), { it.label }, vm::setPeriod) }

            // ---- Evolución por ejercicio
            item {
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                    SectionTitle("Evolución por ejercicio")
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh)
                                .clickable { menu = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(st.exercises.firstOrNull { it.first == st.selectedId }?.second ?: "Elegir ejercicio", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Default.ExpandMore, contentDescription = "Elegir ejercicio")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            st.exercises.forEach { (id, name) ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { vm.select(id); menu = false })
                            }
                        }
                    }
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipSelector(metrics, setOf(metric), { it.label }, { metricChoice = it })
                        EvolutionChart(inPeriod(st.selectedPerformances, st.period, System.currentTimeMillis()), metric, unit)
                    }
                    st.selectedId?.let { id ->
                        SecondaryButton("Ver detalle del ejercicio", { onOpenExercise(id) }, Modifier.padding(top = 8.dp))
                    }
                }
            }

            // ---- Frecuencia semanal
            item {
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                    SectionTitle("Frecuencia semanal")
                    val avg = st.weeklySessions.map { it.second }.average()
                    Text("Media de ${Formatting.number(avg)} sesiones por semana (últimas 8)", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    BarChart(
                        values = st.weeklySessions.map { it.second.toDouble() },
                        labels = st.weeklySessions.map { "${it.first.dayOfMonth}/${it.first.monthValue}" },
                        modifier = Modifier.padding(top = 10.dp),
                        description = "Sesiones por semana: " + st.weeklySessions.joinToString { "${it.first.dayOfMonth}/${it.first.monthValue}: ${it.second}" },
                    )
                    Text("Cada barra es una semana (empieza el lunes indicado).", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ---- Series por grupo muscular
            item {
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                    SectionTitle("Series por grupo muscular · ${st.period.label}")
                    if (st.setsPerMuscle.isEmpty()) {
                        Text("Sin series en este periodo.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    } else {
                        val max = st.setsPerMuscle.maxOf { it.second }.coerceAtLeast(1)
                        MuscleMap(st.setsPerMuscle.toMap(), Modifier.padding(vertical = 10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            st.setsPerMuscle.forEach { (m, n) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp), maxLines = 1)
                                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.surfaceHigh)) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / max).clip(RoundedCornerShape(6.dp)).background(c.primary))
                                    }
                                    Text("$n", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp).padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Volumen
            item {
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                    SectionTitle("Volumen semanal")
                    Text(
                        "Σ peso × repeticiones solo de ejercicios con carga externa (${unit.symbol}). Es una métrica orientativa: no es una medida de calidad ni se mezcla con ejercicios de peso corporal o tiempo.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                    BarChart(
                        values = st.weeklyVolumeKg.map { unit.fromKg(it.second) },
                        labels = st.weeklyVolumeKg.map { "${it.first.dayOfMonth}/${it.first.monthValue}" },
                        modifier = Modifier.padding(top = 10.dp),
                        description = "Volumen semanal en ${unit.symbol}",
                        formatValue = { if (it >= 1000) Formatting.number(it / 1000, 1) + "k" else Formatting.number(it, 0) },
                    )
                }
            }

            // ---- Récords
            item { SectionTitle("Mejores marcas personales", Modifier.padding(horizontal = ScreenPadding)) }
            items(st.records, key = { it.exerciseId }) { r ->
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), onClick = { onOpenExercise(r.exerciseId) }, contentPadding = PaddingValues(14.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    val parts = buildList {
                        when (r.tracking) {
                            TrackingType.WEIGHT_REPS -> {
                                r.maxWeightKg?.let { add("Peso máx. ${Formatting.weight(it, unit)}") }
                                r.e1rmKg?.let { add("1RM est. ${Formatting.weight(it, unit)}") }
                                r.maxReps?.let { add("$it reps máx.") }
                            }
                            TrackingType.BODYWEIGHT -> {
                                r.maxReps?.let { add("$it reps máx.") }
                                r.maxWeightKg?.takeIf { it > 0 }?.let { add("Lastre máx. +${Formatting.weight(it, unit)}") }
                            }
                            else -> r.maxDurationSec?.let { add("Mayor duración ${Formatting.duration(it)}") }
                        }
                    }
                    Text(parts.joinToString(" · ").ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, color = c.primary)
                }
            }
        }
    }
}
