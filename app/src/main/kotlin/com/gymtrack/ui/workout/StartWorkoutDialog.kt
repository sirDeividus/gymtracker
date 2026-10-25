package com.gymtrack.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.routines.RoutineListItem
import com.gymtrack.ui.theme.Brand

/** Elegir qué entrenar: la recomendada primero, el resto de rutinas y la sesión libre. */
@Composable
fun StartWorkoutDialog(
    routines: List<RoutineListItem>,
    recommendedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Brand.colors
    val usable = routines.filter { it.exerciseCount > 0 }
    val ordered = usable.sortedByDescending { it.routine.id == recommendedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("¿Qué entrenas hoy?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ordered.isEmpty()) {
                    Text("Aún no tienes rutinas con ejercicios. Crea una en la pestaña Rutinas o empieza una sesión libre y añade ejercicios sobre la marcha.", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                ordered.forEach { r ->
                    GymCard(Modifier.fillMaxWidth(), onClick = { onPick(r.routine.id) }, highlighted = r.routine.id == recommendedId, contentPadding = PaddingValues(14.dp)) {
                        if (r.routine.id == recommendedId) Text("RECOMENDADA", style = MaterialTheme.typography.labelSmall, color = c.primary)
                        Text(r.routine.name.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleMedium)
                        Text("${r.exerciseCount} ejercicios · ${r.setCount} series", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                GymCard(Modifier.fillMaxWidth(), onClick = { onPick(null) }, contentPadding = PaddingValues(14.dp)) {
                    Text("Entrenamiento libre", style = MaterialTheme.typography.titleMedium)
                    Text("Sin rutina: añade los ejercicios al momento", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text) } },
    )
}
