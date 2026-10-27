package com.gymtrack.ui.routines

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.gymtrack.ui.components.gymCheckboxColors
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

    Box(Modifier.fillMaxSize()) {
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
                        Text("DÍAS DE ENTRENAMIENTO", style = MaterialTheme.typography.labelMedium, color = Brand.colors.textMuted)
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
                        Text("EJERCICIOS", style = MaterialTheme.typography.labelMedium, color = Brand.colors.textMuted, modifier = Modifier.padding(top = 6.dp))
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
        ExercisePickerSheet(libraryVm, onPick = { vm.addExercise(it); picker = false }, onDismiss = { picker = false }, contentPadding = contentPadding)
    }
    editing?.let { id ->
        state.items.firstOrNull { it.row.id == id }?.let { item ->
            RoutineExerciseDialog(item, state.settings.unit, onChange = vm::update, onDismiss = { editing = null }, contentPadding = contentPadding)
        }
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
                    ExerciseThumb(ex.id, size = 60.dp)
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
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = Brand.colors
    val ex = item.exercise
    val tracking = ex?.tracking ?: TrackingType.WEIGHT_REPS
    var row by remember(item.row.id) { mutableStateOf(item.row) }
    fun set(new: RoutineExerciseEntity) { row = new; onChange(new) }
    var weightText by remember(item.row.id) { mutableStateOf(item.row.startWeightKg?.let { Formatting.weight(it, unit, false) }.orEmpty()) }
    var minText by remember(item.row.id) { mutableStateOf(item.row.repMin.toString()) }
    var maxText by remember(item.row.id) { mutableStateOf(item.row.repMax.toString()) }
    var durText by remember(item.row.id) { mutableStateOf((item.row.targetDurationSec ?: 30).toString()) }
    var showNote by remember(item.row.id) { mutableStateOf(item.row.notes.isNotBlank()) }
    var restMenu by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    val trackingLabel = when (tracking) {
        TrackingType.WEIGHT_REPS -> "Reps y carga"
        TrackingType.BODYWEIGHT -> "Reps (peso corporal)"
        TrackingType.DURATION -> "Duración"
        TrackingType.DISTANCE -> "Distancia y tiempo"
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)
    run {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = c.background) {
            Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding().padding(horizontal = ScreenPadding)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                    Text("Editar ejercicio", style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.gymtrack.ui.components.ExerciseImageFill(item.row.exerciseId, ex?.name.orEmpty(), Modifier.size(92.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex?.name ?: "Ejercicio", style = MaterialTheme.typography.titleLarge)
                            val muscles = ex?.let { (listOf(it.primaryMuscle) + it.secondaryMuscles).distinct().joinToString(", ") { m -> m.label } }.orEmpty()
                            Text(muscles, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                        if (hasExerciseMedia(item.row.exerciseId)) {
                            IconButton(onClick = { showMedia = true }) { Icon(Icons.Default.Info, contentDescription = "Ver demostración") }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditChip(trackingLabel, Modifier.weight(1.3f), onClick = null)
                        Box(Modifier.weight(1f)) {
                            EditChip(if (row.restSec > 0) Formatting.duration(row.restSec) else "Sin descanso", Modifier.fillMaxWidth(), icon = Icons.Default.Timer) { restMenu = true }
                            DropdownMenu(expanded = restMenu, onDismissRequest = { restMenu = false }) {
                                listOf(0, 30, 45, 60, 90, 120, 180, 240, 300).forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(if (s == 0) "Sin descanso" else Formatting.duration(s)) },
                                        onClick = { restMenu = false; set(row.copy(restSec = s)) },
                                    )
                                }
                            }
                        }
                        EditChip("Nota", Modifier.weight(0.9f), icon = Icons.Default.Add) { showNote = !showNote }
                    }
                    if (showNote) {
                        OutlinedTextField(
                            value = row.notes, onValueChange = { set(row.copy(notes = it.take(500))) },
                            label = { Text("Notas personales") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = editColors(), minLines = 2,
                        )
                    }

                    // ---- Series: línea de tiempo con una tarjeta por serie (todas comparten el mismo objetivo).
                    if (tracking == TrackingType.DURATION) {
                        // Ejercicio por tiempo (Tabata, plancha…): solo tres números, sin una tarjeta por serie.
                        var roundsText by remember(item.row.id) { mutableStateOf(item.row.sets.toString()) }
                        var restRoundText by remember(item.row.id) { mutableStateOf(item.row.restSec.toString()) }
                        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) {
                                    LabeledInput("Trabajo (s)", durText, decimal = false) {
                                        durText = it
                                        it.toIntOrNull()?.takeIf { v -> v in 1..3600 }?.let { v -> set(row.copy(targetDurationSec = v)) }
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    LabeledInput("Descanso (s)", restRoundText, decimal = false) {
                                        restRoundText = it
                                        it.toIntOrNull()?.takeIf { v -> v in 0..1800 }?.let { v -> set(row.copy(restSec = v)) }
                                    }
                                }
                            }
                            LabeledInput("Rondas (series)", roundsText, decimal = false) {
                                roundsText = it
                                it.toIntOrNull()?.takeIf { v -> v in 1..20 }?.let { v -> set(row.copy(sets = v)) }
                            }
                            Text(
                                "En el entrenamiento se abre un temporizador a pantalla completa; allí también puedes ajustar ciclos, descanso entre ciclos y cuenta atrás.",
                                style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                    } else Column(Modifier.padding(top = 20.dp)) {
                        for (i in 1..row.sets) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                                    Text("$i", style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.width(10.dp))
                                Row(
                                    Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .border(1.dp, c.outline, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when (tracking) {
                                        TrackingType.WEIGHT_REPS, TrackingType.BODYWEIGHT -> {
                                            SetField(minText, "mín", Modifier.width(52.dp)) {
                                                minText = it
                                                it.toIntOrNull()?.takeIf { v -> v in 1..100 }?.let { v -> set(row.copy(repMin = v, repMax = maxOf(row.repMax, v))); if (v > (maxText.toIntOrNull() ?: 0)) maxText = v.toString() }
                                            }
                                            Text("–", color = c.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
                                            SetField(maxText, "máx", Modifier.width(52.dp)) {
                                                maxText = it
                                                it.toIntOrNull()?.takeIf { v -> v in 1..100 }?.let { v -> set(row.copy(repMax = v, repMin = minOf(row.repMin, v))); if (v < (minText.toIntOrNull() ?: 0)) minText = v.toString() }
                                            }
                                            Text(" reps", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                            if (tracking == TrackingType.WEIGHT_REPS) {
                                                Text("  ·  ", color = c.textMuted)
                                                SetField(weightText, "—", Modifier.width(64.dp), decimal = true) {
                                                    weightText = it
                                                    val parsed = Formatting.parseDecimal(it)
                                                    set(row.copy(startWeightKg = if (it.isBlank()) null else parsed?.let { v -> unit.toKg(v) }))
                                                }
                                                Text(" ${unit.symbol}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                            }
                                        }
                                        TrackingType.DURATION -> {
                                            SetField(durText, "30", Modifier.width(72.dp)) {
                                                durText = it
                                                it.toIntOrNull()?.takeIf { v -> v in 1..3600 }?.let { v -> set(row.copy(targetDurationSec = v)) }
                                            }
                                            Text(" segundos", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                        }
                                        TrackingType.DISTANCE -> Text("Registras km y minutos al entrenar", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { if (row.sets < 20) set(row.copy(sets = row.sets + 1)) }, modifier = Modifier.heightIn(min = MinTouch)) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = c.primary)
                                Text("  Añadir serie", color = c.primary)
                            }
                            if (row.sets > 1) {
                                TextButton(onClick = { set(row.copy(sets = row.sets - 1)) }, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Icon(Icons.Default.Remove, contentDescription = null, tint = c.textSecondary)
                                    Text("  Quitar serie", color = c.textSecondary)
                                }
                            }
                        }
                        Text(
                            "Todas las series comparten el mismo objetivo; durante el entreno puedes cambiar cada una.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }
                com.gymtrack.ui.components.GradientCapsuleButton(
                    "GUARDAR", onDismiss, Modifier.fillMaxWidth().padding(vertical = 12.dp), icon = Icons.Default.Check,
                )
            }
        }
    }
    if (showMedia) com.gymtrack.ui.components.ExerciseMediaDialog(item.row.exerciseId, ex?.name.orEmpty()) { showMedia = false }
}

@Composable
private fun EditChip(text: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: (() -> Unit)?) {
    val c = Brand.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Row(
        modifier.heightIn(min = 44.dp).clip(shape).background(c.surfaceHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun SetField(value: String, placeholder: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    NumberInput(
        value = value, placeholder = placeholder, onValueChange = onChange, decimal = decimal, modifier = modifier,
        minimal = true, description = placeholder,
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
                                colors = gymCheckboxColors(),
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
