package com.gymtrack.ui.routines

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.data.db.RoutineExerciseEntity
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.RoutineDays
import com.gymtrack.domain.TrackingType
import com.gymtrack.domain.WeightUnit
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.ConfirmDialog
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.ExerciseThumb
import com.gymtrack.ui.components.hasExerciseMedia
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.NumberInput
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.library.ExercisePickerSheet
import com.gymtrack.ui.library.LibraryViewModel
import com.gymtrack.ui.theme.Brand
import kotlinx.coroutines.launch

fun exerciseSummary(row: RoutineExerciseEntity, tracking: TrackingType): String = when (tracking) {
    TrackingType.DURATION -> "${row.sets} × ${Formatting.duration(row.targetDurationSec ?: 30)}"
    TrackingType.DISTANCE -> "${row.sets} series"
    else -> "${row.sets} × ${if (row.repMin == row.repMax) "${row.repMin}" else "${row.repMin}–${row.repMax}"}"
} + if (row.restSec > 0) " · ${Formatting.duration(row.restSec)} descanso" else " · sin descanso"

@Composable
fun RoutineEditScreen(
    vm: RoutineEditViewModel,
    libraryVm: LibraryViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenWorkout: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<RoutineExerciseItem?>(null) }
    var copyOpen by remember { mutableStateOf(false) }

    // Estado de arrastrar y soltar.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var order by remember { mutableStateOf<List<String>?>(null) }
    val displayed = remember(state.items, order) {
        val o = order
        if (o == null) state.items else o.mapNotNull { id -> state.items.firstOrNull { it.row.id == id } }
    }
    LaunchedEffect(state.items) {
        // Cuando la base de datos ya refleja el nuevo orden, volvemos a mostrar la lista real.
        if (draggingId == null && order == state.items.map { it.row.id }) order = null
    }

    fun onDragMove() {
        val id = draggingId ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val cur = infos.firstOrNull { it.key == id } ?: return
        val center = cur.offset + cur.size / 2f + dragDelta
        val current = order ?: return
        val target = infos.firstOrNull { it.key != id && (it.key as? String)?.let { k -> k in current } == true && center > it.offset && center < it.offset + it.size } ?: return
        val list = current.toMutableList()
        val from = list.indexOf(id)
        val to = list.indexOf(target.key as String)
        if (from < 0 || to < 0) return
        list.add(to, list.removeAt(from))
        order = list
        dragDelta += (cur.offset - target.offset)
    }

    Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
        ScreenHeader(
            "Editar rutina", onBack = onBack,
            subtitle = "${state.items.size} ejercicios · ${state.totalSets} series · se guarda automáticamente",
        )
        val draft = state.draft
        if (state.missing) {
            EmptyState(Icons.Default.FitnessCenter, "Rutina no encontrada", "Es posible que se haya eliminado.", actionLabel = "Volver", onAction = onBack)
        } else if (draft == null) {
            Spacer(Modifier.height(8.dp))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "details") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft.name, onValueChange = vm::setName, label = { Text("Nombre de la rutina") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = editColors(),
                        )
                        OutlinedTextField(
                            value = draft.description, onValueChange = vm::setDescription, label = { Text("Descripción (opcional)") },
                            modifier = Modifier.fillMaxWidth(), colors = editColors(), minLines = 1, maxLines = 3,
                        )
                        Text("DÍAS DE ENTRENAMIENTO", style = MaterialTheme.typography.labelMedium, color = Brand.colors.textSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            java.time.DayOfWeek.values().forEach { day ->
                                val sel = RoutineDays.contains(draft.daysMask, day)
                                FilterChip(
                                    selected = sel, onClick = { vm.toggleDay(day) },
                                    label = { Text(DayLabels[day.value - 1]) },
                                    modifier = Modifier.weight(1f).semantics { contentDescription = DayNames[day.value - 1] + if (sel) ", seleccionado" else "" },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Brand.colors.surface, labelColor = Brand.colors.text,
                                        selectedContainerColor = Brand.colors.primary, selectedLabelColor = Brand.colors.onPrimary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = sel, borderColor = Brand.colors.outline, selectedBorderColor = Brand.colors.primary,
                                    ),
                                )
                            }
                        }
                        Text("EJERCICIOS", style = MaterialTheme.typography.labelMedium, color = Brand.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                        if (state.items.isNotEmpty()) {
                            Text(
                                "Mantén pulsado el asa ⠿ para reordenar arrastrando, o usa el menú ⋮.",
                                style = MaterialTheme.typography.bodySmall, color = Brand.colors.textSecondary,
                            )
                        }
                    }
                }
                if (state.items.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            Icons.Default.FitnessCenter, "Sin ejercicios",
                            "Añade ejercicios de la biblioteca. Puedes buscarlos o filtrarlos por grupo muscular.",
                        )
                    }
                }
                items(displayed, key = { it.row.id }) { item ->
                    val isDragging = draggingId == item.row.id
                    ExerciseRow(
                        item = item,
                        unit = state.settings.unit,
                        modifier = if (isDragging) {
                            Modifier.zIndex(1f).graphicsLayer { translationY = dragDelta; scaleX = 1.02f; scaleY = 1.02f }
                        } else Modifier.animateItem(),
                        index = displayed.indexOf(item),
                        count = displayed.size,
                        onEdit = { editing = item.row.id },
                        onRemove = { removing = item },
                        onMove = { vm.move(item.row.id, it) },
                        dragModifier = Modifier.pointerInput(item.row.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    order = state.items.map { it.row.id }
                                    draggingId = item.row.id
                                    dragDelta = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDelta += amount.y
                                    onDragMove()
                                },
                                onDragEnd = {
                                    order?.let { vm.reorder(it) }
                                    draggingId = null
                                    dragDelta = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragDelta = 0f
                                    order = null
                                },
                            )
                        },
                    )
                }
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        SecondaryButton("Añadir ejercicio", { picker = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Add)
                        if (state.others.any { it.exerciseCount > 0 }) {
                            SecondaryButton("Copiar de otra rutina", { copyOpen = true }, Modifier.fillMaxWidth(), icon = Icons.Default.ContentCopy)
                        }
                    }
                }
            }
            BigButton(
                text = if (state.hasActiveSession) "CONTINUAR ENTRENAMIENTO" else "ENTRENAR",
                onClick = { vm.start(onOpenWorkout) },
                enabled = state.items.isNotEmpty() || state.hasActiveSession,
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.padding(ScreenPadding),
            )
        }
    }

    if (picker) {
        ExercisePickerSheet(libraryVm, onPick = { vm.addExercise(it); picker = false }, onDismiss = { picker = false })
    }
    editing?.let { id ->
        state.items.firstOrNull { it.row.id == id }?.let { item ->
            RoutineExerciseDialog(item, state.settings.unit, onChange = vm::update, onDismiss = { editing = null })
        }
    }
    removing?.let { item ->
        ConfirmDialog(
            title = "Quitar ejercicio",
            message = "«${item.exercise?.name ?: "Ejercicio"}» se quitará de esta rutina. Tus entrenamientos anteriores no cambian.",
            confirmText = "Quitar", destructive = true,
            onConfirm = { vm.remove(item.row.id); removing = null },
            onDismiss = { removing = null },
        )
    }
    if (copyOpen) CopyFromRoutineDialog(vm, state.others, onDismiss = { copyOpen = false })
}

@Composable
private fun editColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand.colors.primary, unfocusedBorderColor = Brand.colors.outline,
    focusedContainerColor = Brand.colors.surface, unfocusedContainerColor = Brand.colors.surface, cursorColor = Brand.colors.primary,
)

@Composable
private fun ExerciseRow(
    item: RoutineExerciseItem,
    unit: WeightUnit,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    var menu by remember { mutableStateOf(false) }
    val name = item.exercise?.name ?: "Ejercicio no disponible"
    GymCard(modifier.fillMaxWidth(), onClick = onEdit, contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box48(dragModifier.semantics { contentDescription = "Reordenar $name" }) {
                Icon(Icons.Default.DragHandle, contentDescription = null, tint = c.textSecondary)
            }
            item.exercise?.let { ex ->
                if (hasExerciseMedia(ex.id)) {
                    ExerciseThumb(ex.id, size = 48.dp)
                    Spacer(Modifier.width(10.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                val tracking = item.exercise?.tracking ?: TrackingType.WEIGHT_REPS
                val start = item.row.startWeightKg?.takeIf { tracking == TrackingType.WEIGHT_REPS }
                Text(
                    exerciseSummary(item.row, tracking) + (start?.let { " · inicio ${Formatting.weight(it, unit)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                if (item.row.notes.isNotBlank()) {
                    Text(item.row.notes, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                }
            }
            IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones de $name")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() })
                if (index > 0) DropdownMenuItem(text = { Text("Subir") }, onClick = { menu = false; onMove(-1) })
                if (index < count - 1) DropdownMenuItem(text = { Text("Bajar") }, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text("Quitar", color = c.error) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun Box48(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier.size(MinTouch), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.Remove, contentDescription = "Menos $label")
        }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(44.dp), color = c.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.Add, contentDescription = "Más $label")
        }
    }
}

@Composable
private fun RoutineExerciseDialog(
    item: RoutineExerciseItem,
    unit: WeightUnit,
    onChange: (RoutineExerciseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Brand.colors
    val tracking = item.exercise?.tracking ?: TrackingType.WEIGHT_REPS
    var row by remember(item.row.id) { mutableStateOf(item.row) }
    fun set(new: RoutineExerciseEntity) { row = new; onChange(new) }
    var weightText by remember(item.row.id) { mutableStateOf(item.row.startWeightKg?.let { Formatting.weight(it, unit, false) }.orEmpty()) }
    var restText by remember(item.row.id) { mutableStateOf(item.row.restSec.toString()) }
    var durText by remember(item.row.id) { mutableStateOf((item.row.targetDurationSec ?: 30).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(item.exercise?.name ?: "Ejercicio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Series", row.sets, { set(row.copy(sets = it)) }, 1..20)
                if (tracking == TrackingType.WEIGHT_REPS || tracking == TrackingType.BODYWEIGHT) {
                    Stepper("Reps mínimas", row.repMin, { set(row.copy(repMin = it, repMax = maxOf(row.repMax, it))) }, 1..100)
                    Stepper("Reps máximas", row.repMax, { set(row.copy(repMax = it, repMin = minOf(row.repMin, it))) }, 1..100)
                }
                if (tracking == TrackingType.DURATION) {
                    LabeledInput("Duración objetivo (segundos)", durText, decimal = false) {
                        durText = it
                        it.toIntOrNull()?.takeIf { s -> s in 1..3600 }?.let { s -> set(row.copy(targetDurationSec = s)) }
                    }
                }
                if (tracking == TrackingType.WEIGHT_REPS) {
                    LabeledInput("Peso inicial opcional (${unit.symbol})", weightText, decimal = true) {
                        weightText = it
                        val parsed = Formatting.parseDecimal(it)
                        set(row.copy(startWeightKg = if (it.isBlank()) null else parsed?.let { v -> unit.toKg(v) }))
                    }
                }
                LabeledInput("Descanso entre series (segundos)", restText, decimal = false) {
                    restText = it
                    it.toIntOrNull()?.takeIf { s -> s in 0..1800 }?.let { s -> set(row.copy(restSec = s)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(45, 60, 90, 120, 180).forEach { s ->
                        FilterChip(
                            selected = row.restSec == s,
                            onClick = { restText = s.toString(); set(row.copy(restSec = s)) },
                            label = { Text("${s}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = c.surface, labelColor = c.text,
                                selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = row.notes, onValueChange = { set(row.copy(notes = it.take(500))) },
                    label = { Text("Notas personales") }, modifier = Modifier.fillMaxWidth(), colors = editColors(), minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.padding(0.dp)) { Text("Hecho", color = c.primary) } },
    )
}

@Composable
private fun LabeledInput(label: String, value: String, decimal: Boolean, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.colors.textSecondary)
        NumberInput(value = value, placeholder = "—", onValueChange = onChange, decimal = decimal, modifier = Modifier.fillMaxWidth(), description = label)
    }
}

@Composable
private fun CopyFromRoutineDialog(vm: RoutineEditViewModel, others: List<RoutineListItem>, onDismiss: () -> Unit) {
    val c = Brand.colors
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<RoutineListItem?>(null) }
    var rows by remember { mutableStateOf<List<RoutineExerciseItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(source?.routine?.name?.ifBlank { "Rutina" } ?: "Copiar de otra rutina") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (source == null) {
                    others.filter { it.exerciseCount > 0 }.forEach { r ->
                        GymCard(Modifier.fillMaxWidth(), onClick = {
                            source = r
                            scope.launch {
                                rows = vm.exercisesOf(r.routine.id)
                                selected = rows.map { it.row.id }.toSet()
                            }
                        }, contentPadding = PaddingValues(12.dp)) {
                            Text(r.routine.name.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleMedium)
                            Text("${r.exerciseCount} ejercicios", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                } else {
                    rows.forEach { it2 ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = it2.row.id in selected,
                                onCheckedChange = { on -> selected = if (on) selected + it2.row.id else selected - it2.row.id },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(it2.exercise?.name ?: "Ejercicio", style = MaterialTheme.typography.bodyLarge)
                                Text(exerciseSummary(it2.row, it2.exercise?.tracking ?: TrackingType.WEIGHT_REPS), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (source != null) {
                TextButton(
                    onClick = { vm.copyFrom(source!!.routine.id, selected); onDismiss() },
                    enabled = selected.isNotEmpty(),
                ) { Text("Copiar ${selected.size}", color = c.primary) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text) } },
    )
}
