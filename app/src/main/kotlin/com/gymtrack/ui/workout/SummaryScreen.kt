package com.gymtrack.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.repo.SessionSummary
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.RecordType
import com.gymtrack.domain.WeightUnit
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.components.StatTile
import com.gymtrack.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SummaryState(val loading: Boolean = true, val summary: SessionSummary? = null, val settings: UserSettings = UserSettings())

class SummaryViewModel(c: AppContainer, sessionId: String) : ViewModel() {
    private val _state = MutableStateFlow(SummaryState())
    val state: StateFlow<SummaryState> = _state

    init {
        viewModelScope.launch {
            val settings = c.settings.current()
            _state.value = SummaryState(false, c.workout.summary(sessionId), settings)
        }
    }
}

@Composable
fun SummaryScreen(vm: SummaryViewModel, contentPadding: PaddingValues, onDone: () -> Unit, onDetail: (String) -> Unit) {
    val c = Brand.colors
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.summary
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (s == null) {
            if (!st.loading) EmptyState(Icons.Default.CheckCircle, "Resumen no disponible", "No se encontró la sesión.", actionLabel = "Volver", onAction = onDone)
            return@Column
        }
        val unit = st.settings.unit
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.primary, modifier = Modifier.size(36.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Entrenamiento guardado", style = MaterialTheme.typography.headlineMedium)
                        Text("${s.session.routineName} · ${Dates.dayLong(s.session.startedAt)}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Duración", Formatting.duration(s.durationSec.toInt()), Modifier.weight(1f))
                    StatTile("Ejercicios", "${s.exerciseCount}", Modifier.weight(1f))
                    StatTile("Series", "${s.totals.completedSets}", Modifier.weight(1f))
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Volumen de entrenamiento")
                    if (s.totals.volumeKg > 0) {
                        Text(Formatting.weight(s.totals.volumeKg, unit), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Suma de peso × repeticiones de ejercicios con carga externa. Es una métrica orientativa, no una medida de calidad.",
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    } else {
                        Text("Sin ejercicios con carga externa en esta sesión.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    if (s.totals.bodyweightReps > 0) Text("${s.totals.bodyweightReps} repeticiones de peso corporal", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    if (s.totals.timedSeconds > 0) Text("${Formatting.duration(s.totals.timedSeconds)} de trabajo cronometrado", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Comparación con la sesión anterior")
                    val p = s.previous
                    if (p == null) {
                        Text(
                            if (s.session.routineId == null) "Las sesiones libres no se comparan automáticamente."
                            else "Es la primera vez que registras esta rutina: aún no hay con qué comparar.",
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        )
                    } else {
                        Text("Anterior: ${Dates.dayYear(p.previousDate)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        if (p.previousVolumeKg > 0 && s.totals.volumeKg > 0) {
                            val diff = s.totals.volumeKg - p.previousVolumeKg
                            val pct = diff / p.previousVolumeKg * 100
                            Text("Volumen ${signed(diff, unit)} (${if (pct >= 0) "+" else "−"}${Formatting.number(abs(pct), 1)} %)", style = MaterialTheme.typography.titleMedium, color = if (diff >= 0) c.primary else c.warning)
                        }
                        val sd = s.totals.completedSets - p.previousSets
                        Text("Series ${if (sd >= 0) "+" else "−"}${abs(sd)} (${p.previousSets} → ${s.totals.completedSets})", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionTitle("Mejores marcas alcanzadas") }
            if (s.records.isEmpty()) {
                item {
                    Text(
                        "Sin récords nuevos esta vez. Los récords se marcan cuando superas una marca anterior del mismo ejercicio.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                    )
                }
            } else {
                items(s.records) { r ->
                    GymCard(Modifier.fillMaxWidth(), highlighted = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(r.exerciseName, style = MaterialTheme.typography.titleMedium)
                                Text("${r.record.type.label}: ${recordValue(r.record.type, r.record.value, unit)}" +
                                    (r.record.previous?.let { " (antes ${recordValue(r.record.type, it, unit)})" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium, color = c.primary)
                            }
                        }
                    }
                }
            }
            if (s.session.notes.isNotBlank()) {
                item {
                    GymCard(Modifier.fillMaxWidth()) {
                        SectionTitle("Notas")
                        Text(s.session.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Hecho", onDone)
            SecondaryButton("Ver detalle de la sesión", { onDetail(s.session.id) }, Modifier.fillMaxWidth())
        }
    }
}

private fun signed(v: Double, unit: WeightUnit): String = (if (v >= 0) "+" else "−") + Formatting.weight(abs(v), unit)

fun recordValue(type: RecordType, v: Double, unit: WeightUnit): String = when (type) {
    RecordType.WEIGHT, RecordType.ESTIMATED_1RM -> Formatting.weight(v, unit)
    RecordType.REPS -> "${Formatting.number(v, 0)} reps"
    RecordType.DURATION -> Formatting.duration(v.toInt())
    RecordType.DISTANCE -> Formatting.distance(v)
}

