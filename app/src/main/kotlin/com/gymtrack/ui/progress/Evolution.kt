package com.gymtrack.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.ExerciseBests
import com.gymtrack.domain.ExerciseEvolution
import com.gymtrack.domain.ExercisePerformance
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.TrackingType
import com.gymtrack.domain.WeightUnit
import com.gymtrack.ui.components.ChipSelector
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.ExerciseAnimation
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.LineChart
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.components.StatTile
import com.gymtrack.ui.theme.Brand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class Metric(val label: String) {
    LOAD("Carga máxima"),
    E1RM("1RM estimado"),
    REPS("Repeticiones"),
    VOLUME("Volumen"),
    DURATION("Duración"),
}

enum class Period(val label: String, val days: Long?) {
    M1("1 mes", 30), M3("3 meses", 90), M6("6 meses", 180), Y1("1 año", 365), ALL("Todo", null)
}

fun metricsFor(tracking: TrackingType): List<Metric> = when (tracking) {
    TrackingType.WEIGHT_REPS -> listOf(Metric.LOAD, Metric.E1RM, Metric.REPS, Metric.VOLUME)
    TrackingType.BODYWEIGHT -> listOf(Metric.REPS, Metric.LOAD)
    TrackingType.DURATION -> listOf(Metric.DURATION)
    TrackingType.DISTANCE -> listOf(Metric.DURATION)
}

fun inPeriod(performances: List<ExercisePerformance>, period: Period, nowMs: Long): List<ExercisePerformance> {
    val days = period.days ?: return performances
    val from = nowMs - days * 86_400_000L
    return performances.filter { it.performedAt >= from }
}

/** Gráfico de evolución de un ejercicio: solo datos reales de sesiones finalizadas. */
@Composable
fun EvolutionChart(
    performances: List<ExercisePerformance>,
    metric: Metric,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    val points = ExerciseEvolution.points(performances)
    val series = points.mapNotNull { p ->
        val v: Double? = when (metric) {
            Metric.LOAD -> p.topWeightKg?.takeIf { it != 0.0 }?.let { unit.fromKg(it) }
            Metric.E1RM -> p.bestE1rmKg?.let { unit.fromKg(it) }
            Metric.REPS -> p.bestReps?.toDouble()
            Metric.VOLUME -> p.volumeKg.takeIf { it > 0 }?.let { unit.fromKg(it) }
            Metric.DURATION -> p.bestDurationSec?.toDouble()
        }
        v?.let { p.performedAt to it }
    }
    val unitLabel = when (metric) {
        Metric.LOAD, Metric.E1RM, Metric.VOLUME -> unit.symbol
        Metric.REPS -> "reps"
        Metric.DURATION -> "s"
    }
    if (series.isEmpty()) {
        EmptyState(Icons.Default.ShowChart, "Sin datos para esta métrica", "Registra este ejercicio en este periodo para ver su evolución.", modifier)
        return
    }
    Column(modifier) {
        LineChart(
            values = series.map { it.second },
            description = "${metric.label}: de ${Formatting.number(series.first().second)} a ${Formatting.number(series.last().second)} $unitLabel en ${series.size} sesiones",
            formatValue = { Formatting.number(it) + " " + unitLabel },
            startLabel = Dates.dayYear(series.first().first),
            endLabel = Dates.dayYear(series.last().first),
        )
        if (series.size == 1) {
            Text("Solo hay una sesión en este periodo. Registra otra para ver la tendencia.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (metric == Metric.VOLUME) {
            Text("Volumen = suma de peso × repeticiones. Métrica orientativa.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

fun setsText(p: ExercisePerformance, unit: WeightUnit): String = p.completedSets.joinToString("  ·  ") { s ->
    when (p.tracking) {
        TrackingType.WEIGHT_REPS -> "${Formatting.weight(s.weightKg ?: 0.0, unit, false)}×${s.reps ?: 0}"
        TrackingType.BODYWEIGHT -> if ((s.weightKg ?: 0.0) != 0.0) "${Formatting.bodyweightLoad(s.weightKg, unit)}×${s.reps ?: 0}" else "${s.reps ?: 0}"
        TrackingType.DURATION -> Formatting.duration(s.durationSec ?: 0)
        TrackingType.DISTANCE -> listOfNotNull(s.distanceM?.let { Formatting.distance(it) }, s.durationSec?.let { Formatting.duration(it) }).joinToString(" ")
    }
}

data class ExerciseEvolutionState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val performances: List<ExercisePerformance> = emptyList(),
    val settings: UserSettings = UserSettings(),
)

class ExerciseEvolutionViewModel(c: AppContainer, exerciseId: String) : ViewModel() {
    val state: StateFlow<ExerciseEvolutionState> = combine(
        c.history.performances(exerciseId), c.exercises.allById, c.settings.settings,
    ) { perfs, byId, settings ->
        ExerciseEvolutionState(false, byId[exerciseId], perfs, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseEvolutionState())
}

@Composable
fun ExerciseEvolutionScreen(vm: ExerciseEvolutionViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val c = Brand.colors
    val st by vm.state.collectAsStateWithLifecycle()
    val unit = st.settings.unit
    val tracking = st.exercise?.tracking ?: st.performances.firstOrNull()?.tracking ?: TrackingType.WEIGHT_REPS
    var metric by remember(tracking) { mutableStateOf(metricsFor(tracking).first()) }
    var period by remember { mutableStateOf(Period.ALL) }
    val now = remember { System.currentTimeMillis() }
    val filtered = inPeriod(st.performances, period, now)
    val bests = ExerciseBests.from(st.performances, tracking)

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHeader(st.exercise?.name ?: "Ejercicio", onBack = onBack, subtitle = "${st.performances.size} sesiones registradas")
        if (st.performances.isEmpty() && !st.loading) {
            EmptyState(Icons.Default.ShowChart, "Aún sin registros", "Completa este ejercicio en un entrenamiento y aquí verás su evolución.")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            st.exercise?.let { ex -> item { ExerciseAnimation(ex.id, size = 160.dp) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipSelector(metricsFor(tracking), setOf(metric), { it.label }, { metric = it })
                    ChipSelector(Period.values().toList(), setOf(period), { it.label }, { period = it })
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                    EvolutionChart(filtered, metric, unit)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (tracking) {
                        TrackingType.WEIGHT_REPS -> {
                            StatTile("Mejor peso", bests.maxWeight()?.let { Formatting.weight(it, unit) } ?: "—", Modifier.weight(1f))
                            StatTile("1RM estimado", bests.maxE1rm()?.let { Formatting.weight(it, unit) } ?: "—", Modifier.weight(1f))
                        }
                        TrackingType.BODYWEIGHT -> StatTile("Más repeticiones", bests.maxReps()?.toString() ?: "—", Modifier.weight(1f))
                        else -> StatTile("Mayor duración", bests.maxDuration()?.let { Formatting.duration(it) } ?: "—", Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("Sesiones", Modifier.padding(horizontal = ScreenPadding)) }
            items(st.performances.sortedByDescending { it.performedAt }, key = { it.sessionId + it.exerciseId + it.performedAt }) { p ->
                GymCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), contentPadding = PaddingValues(14.dp)) {
                    Text(Dates.day(p.performedAt) + " · " + Dates.dayYear(p.performedAt), style = MaterialTheme.typography.titleMedium)
                    Text(setsText(p, unit), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        }
    }
}
