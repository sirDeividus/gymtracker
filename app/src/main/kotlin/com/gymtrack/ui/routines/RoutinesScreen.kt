package com.gymtrack.ui.routines

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.gymtrack.domain.RoutineDays
import com.gymtrack.domain.RoutineTemplate
import com.gymtrack.domain.RoutineTemplates
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.ConfirmDialog
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.PlayCapsuleButton
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.theme.Brand

val DayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
val DayNames = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

fun daysText(mask: Int): String =
    java.time.DayOfWeek.values().filter { RoutineDays.contains(mask, it) }.joinToString(" · ") { DayNames[it.value - 1] }

@Composable
fun RoutinesScreen(
    vm: RoutinesViewModel,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onOpenWorkout: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<RoutineListItem?>(null) }
    var templatesOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHeader(
            "Rutinas",
            subtitle = if (state.items.isEmpty()) null else "${state.items.size} ${if (state.items.size == 1) "rutina" else "rutinas"}",
            actions = {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva rutina", tint = Brand.colors.primary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rutina en blanco") }, onClick = { menuOpen = false; vm.create(onOpen) })
                    DropdownMenuItem(text = { Text("Desde una plantilla…") }, onClick = { menuOpen = false; templatesOpen = true })
                }
            },
        )
        if (state.items.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Default.ViewList,
                title = "Aún no tienes rutinas",
                message = "Crea tu primera rutina en blanco o parte de una plantilla que podrás editar libremente.",
            )
            Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Crear rutina", onClick = { vm.create(onOpen) }, icon = Icons.Default.Add)
                SecondaryButton("Usar una plantilla", { templatesOpen = true }, Modifier.fillMaxWidth())
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { it.routine.id }) { item ->
                    RoutineCard(
                        item,
                        onOpen = { onOpen(item.routine.id) },
                        onStart = { vm.start(item.routine.id, onOpenWorkout) },
                        onDuplicate = { vm.duplicate(item.routine.id) },
                        onDelete = { confirmDelete = item },
                        startLabel = if (state.hasActiveSession) "CONTINUAR" else "ENTRENAR",
                        startEnabled = item.exerciseCount > 0 || state.hasActiveSession,
                    )
                }
            }
        }
    }

    if (templatesOpen) TemplatePickerDialog(
        onDismiss = { templatesOpen = false },
        onPick = { t -> templatesOpen = false; vm.createFromTemplate(t, onOpen) },
    )
    confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Eliminar rutina",
            message = "Se eliminará «${item.routine.name.ifBlank { "Sin nombre" }}». Tu historial de entrenamientos con esta rutina se conserva intacto.",
            confirmText = "Eliminar", destructive = true,
            onConfirm = { vm.delete(item.routine.id); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun RoutineCard(
    item: RoutineListItem,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    startLabel: String,
    startEnabled: Boolean,
) {
    val c = Brand.colors
    var menu by remember { mutableStateOf(false) }
    GymCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.routine.name.ifBlank { "Sin nombre" }.uppercase(), style = MaterialTheme.typography.titleLarge)
                if (item.routine.description.isNotBlank()) {
                    Text(item.routine.description, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                Text(
                    "${item.exerciseCount} ejercicios · ${item.setCount} series",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
                )
                if (item.muscles.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.muscles.take(5).forEach { MuscleChip(it.label) }
                    }
                }
                if (item.routine.daysMask != 0) {
                    Text(daysText(item.routine.daysMask), style = MaterialTheme.typography.bodySmall, color = c.primary, modifier = Modifier.padding(top = 2.dp))
                }
            }
            IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones de ${item.routine.name}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Eliminar", color = c.error) }, onClick = { menu = false; onDelete() })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            PlayCapsuleButton(
                onClick = onStart, enabled = startEnabled,
                contentDescription = (if (startLabel == "CONTINUAR") "Continuar entrenamiento" else "Entrenar con ${item.routine.name.ifBlank { "esta rutina" }}"),
            )
        }
    }
}

/** Chip de texto minimalista: borde fino, sin relleno. */
@Composable
private fun MuscleChip(text: String) {
    val c = Brand.colors
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1,
        modifier = Modifier.border(1.dp, c.outline, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun TemplatePickerDialog(onDismiss: () -> Unit, onPick: (RoutineTemplate) -> Unit) {
    val c = Brand.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Plantillas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Son un punto de partida: podrás cambiar, quitar o añadir lo que quieras.", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                RoutineTemplates.all.forEach { t ->
                    GymCard(Modifier.fillMaxWidth(), onClick = { onPick(t) }, contentPadding = PaddingValues(12.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text("${t.description} · ${t.items.size} ejercicios", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text) }
        },
    )
}
