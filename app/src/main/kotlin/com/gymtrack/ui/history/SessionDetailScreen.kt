package com.gymtrack.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.repo.loggedSets
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.Side
import com.gymtrack.domain.SessionStatus
import com.gymtrack.domain.Stats
import com.gymtrack.domain.TrackingType
import com.gymtrack.ui.components.ConfirmDialog
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.ExerciseThumb
import com.gymtrack.ui.components.hasExerciseMedia
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.components.StatTile
import com.gymtrack.ui.theme.Brand

/** Resultados exactos de una sesión pasada. Solo lectura: el historial no se reescribe. */
@Composable
fun SessionDetailScreen(
    vm: SessionDetailViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val c = Brand.colors
    val w by vm.workout.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unit = settings.unit
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        val workout = w
        if (workout == null) {
            ScreenHeader("Sesión", onBack = onBack)
            EmptyState(Icons.Default.History, "Sesión no encontrada", "Puede que se haya eliminado.", actionLabel = "Volver", onAction = onBack)
            return@Column
        }
        val s = workout.session
        val status = enumOr(s.status, SessionStatus.FINISHED)
        val totals = Stats.totals(workout.loggedSets())
        ScreenHeader(
            s.routineName, onBack = onBack,
            subtitle = "${Dates.dayLong(s.startedAt)} · ${Dates.time(s.startedAt)}" + when (status) {
                SessionStatus.IN_PROGRESS -> " · En curso"
                SessionStatus.DISCARDED -> " · Descartada"
                SessionStatus.FINISHED -> ""
            },
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val dur = ((s.endedAt ?: s.startedAt) - s.startedAt) / 1000
                    StatTile("Duración", Dates.shortDuration(dur), Modifier.weight(1f))
                    StatTile("Series", "${totals.completedSets}", Modifier.weight(1f))
                    StatTile("Volumen", if (totals.volumeKg > 0) Formatting.weight(totals.volumeKg, unit) else "—", Modifier.weight(1f))
                }
            }
            if (s.notes.isNotBlank()) {
                item { GymCard(Modifier.fillMaxWidth()) { SectionTitle("Notas"); Text(s.notes, style = MaterialTheme.typography.bodyMedium) } }
            }
            items(workout.exercises, key = { it.entity.id }) { ex ->
                val e = ex.entity
                val tracking = enumOr(e.tracking, TrackingType.WEIGHT_REPS)
                val omitted = e.skipped || ex.completedSets == 0
                GymCard(Modifier.fillMaxWidth(), onClick = { onOpenExercise(e.exerciseId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasExerciseMedia(e.exerciseId)) {
                            ExerciseThumb(e.exerciseId, size = 56.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(e.exerciseName, style = MaterialTheme.typography.titleMedium)
                    }
                    if (omitted) {
                        Text(if (e.skipped) "Omitido" else "Sin series completadas", style = MaterialTheme.typography.bodySmall, color = c.warning)
                    } else {
                        ex.sets.filter { it.completed }.forEach { set ->
                            val side = enumOr(set.side, Side.BOTH)
                            val label = "Serie ${set.setNumber}" + if (side != Side.BOTH) " ${side.short}" else ""
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                                Text(
                                    when (tracking) {
                                        TrackingType.WEIGHT_REPS -> "${Formatting.weight(set.weightKg ?: 0.0, unit)} × ${set.reps ?: 0}"
                                        TrackingType.BODYWEIGHT -> if ((set.weightKg ?: 0.0) != 0.0) "${Formatting.bodyweightLoad(set.weightKg, unit)} × ${set.reps ?: 0}" else "${set.reps ?: 0} reps"
                                        TrackingType.DURATION -> Formatting.duration(set.durationSec ?: 0)
                                        TrackingType.DISTANCE -> listOfNotNull(set.distanceM?.let { Formatting.distance(it) }, set.durationSec?.let { Formatting.duration(it) }).joinToString(" · ")
                                    } + (set.rpe?.let { "  · RPE ${Formatting.number(it)}" } ?: ""),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                    if (e.notes.isNotBlank()) Text("Nota: ${e.notes}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                    if (e.discomfort) Text("Marcado: dolor o técnica deteriorada", style = MaterialTheme.typography.bodySmall, color = c.warning)
                    Text("Ver evolución del ejercicio ›", style = MaterialTheme.typography.bodySmall, color = c.primary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (status != SessionStatus.IN_PROGRESS) {
                item { SecondaryButton("Eliminar sesión del historial", { confirmDelete = true }, Modifier.fillMaxWidth(), destructive = true) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar sesión",
            message = "Se borrará esta sesión y sus series del historial y de las estadísticas. No se puede deshacer.",
            confirmText = "Eliminar", destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
