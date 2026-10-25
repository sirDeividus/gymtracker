package com.gymtrack.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.gymtrack.domain.Equipment
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.MovementType
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.RecommendationSort
import com.gymtrack.domain.TrackingType
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.ChipSelector
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.ExerciseAnimation
import com.gymtrack.ui.components.ExerciseThumb
import com.gymtrack.ui.components.hasExerciseMedia
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.gymSwitchColors
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.theme.Brand

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand.colors.primary,
    unfocusedBorderColor = Brand.colors.outline,
    focusedContainerColor = Brand.colors.surface,
    unfocusedContainerColor = Brand.colors.surface,
    cursorColor = Brand.colors.primary,
)

/** Lista con búsqueda instantánea, filtros por grupo muscular y equipamiento, y orden. */
@Composable
fun ExerciseBrowser(
    state: LibraryState,
    vm: LibraryViewModel,
    onClick: (Exercise) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val c = Brand.colors
    val f = state.filters
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = bottomPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    singleLine = true,
                    placeholder = { Text("Buscar ejercicio") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (f.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                )
                ChipSelector(MuscleGroup.values().toList(), f.muscles, { it.label }, vm::toggleMuscle)
                ChipSelector(Equipment.values().toList(), f.equipment, { it.label }, vm::toggleEquipment)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.items.size} de ${state.total} ejercicios",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    var open by remember { mutableStateOf(false) }
                    TextButton(onClick = { open = true }, modifier = Modifier.heightIn48()) {
                        Text("Orden: ${f.sort.label}", color = c.primary)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        RecommendationSort.values().forEach { s ->
                            DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setSort(s); open = false })
                        }
                    }
                }
            }
        }
        if (state.items.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = "Sin resultados",
                    message = "Prueba con otra búsqueda o quita algún filtro. También puedes crear tu propio ejercicio.",
                    actionLabel = "Crear ejercicio",
                    onAction = onCreate,
                )
            }
        }
        items(state.items, key = { it.id }) { ex ->
            ExerciseRow(ex, uses = state.usage[ex.id] ?: 0, onClick = { onClick(ex) }, modifier = Modifier.padding(horizontal = ScreenPadding))
        }
    }
}

private fun Modifier.heightIn48(): Modifier = this.then(Modifier.padding(0.dp))

@Composable
fun ExerciseRow(ex: Exercise, uses: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    GymCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasExerciseMedia(ex.id)) {
            ExerciseThumb(ex.id, size = 60.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
        Text(ex.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        val extra = buildList {
            add(ex.primaryMuscle.label)
            add(ex.equipment.label)
            add(ex.tracking.label)
            if (ex.custom) add("Personalizado")
        }
        Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        if (uses > 0) {
            Text("Usado $uses ${if (uses == 1) "vez" else "veces"}", style = MaterialTheme.typography.bodySmall, color = c.primary)
        }
        }
      }
    }
}

/** Selector de ejercicios en hoja inferior. Las sugerencias son editables: nada es obligatorio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    vm: LibraryViewModel,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Brand.colors.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                Text("Elegir ejercicio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }, modifier = Modifier.padding(0.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Crear", color = Brand.colors.primary)
                }
            }
            Text(
                "Pulsa un ejercicio para añadirlo. Las primeras opciones se ordenan según tu uso real.",
                style = MaterialTheme.typography.bodySmall, color = Brand.colors.textSecondary,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
            )
            ExerciseBrowser(state, vm, onClick = onPick, onCreate = { creating = true }, modifier = Modifier.weight(1f), bottomPadding = 32.dp)
        }
    }
    if (creating) {
        ExerciseEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { f, done ->
                vm.saveCustom(null, f.name, f.primary, f.secondary, f.equipment, f.movement, f.tracking, f.unilateral, f.description, f.cues) { err ->
                    if (err == null) creating = false
                    done(err)
                }
            },
        )
    }
}

class ExerciseForm(
    val name: String, val primary: MuscleGroup, val secondary: List<MuscleGroup>, val equipment: Equipment,
    val movement: MovementType, val tracking: TrackingType, val unilateral: Boolean, val description: String, val cues: String,
)

@Composable
fun ExerciseEditorDialog(
    initial: Exercise?,
    onDismiss: () -> Unit,
    onSave: (ExerciseForm, (String?) -> Unit) -> Unit,
) {
    val c = Brand.colors
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var primary by rememberSaveable { mutableStateOf(initial?.primaryMuscle ?: MuscleGroup.CHEST) }
    var secondary by rememberSaveable { mutableStateOf(initial?.secondaryMuscles.orEmpty().toSet().toList()) }
    var equipment by rememberSaveable { mutableStateOf(initial?.equipment ?: Equipment.BARBELL) }
    var movement by rememberSaveable { mutableStateOf(initial?.movement ?: MovementType.ISOLATION) }
    var tracking by rememberSaveable { mutableStateOf(initial?.tracking ?: TrackingType.WEIGHT_REPS) }
    var unilateral by rememberSaveable { mutableStateOf(initial?.unilateral ?: false) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var cues by rememberSaveable { mutableStateOf(initial?.cues.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (initial == null) "Nuevo ejercicio" else "Editar ejercicio",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Nombre") }, singleLine = true, isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), colors = fieldColors(),
                    )
                    Label("Grupo muscular principal")
                    ChipSelector(MuscleGroup.values().toList(), setOf(primary), { it.label }, { primary = it })
                    Label("Músculos secundarios (opcional)")
                    ChipSelector(
                        MuscleGroup.values().filter { it != primary }, secondary.toSet(), { it.label },
                        { m -> secondary = if (m in secondary) secondary - m else secondary + m },
                    )
                    Label("Equipamiento")
                    ChipSelector(Equipment.values().toList(), setOf(equipment), { it.label }, { equipment = it })
                    Label("Método de registro")
                    ChipSelector(TrackingType.values().toList(), setOf(tracking), { it.label }, { tracking = it })
                    Text(
                        when (tracking) {
                            TrackingType.WEIGHT_REPS -> "Registra peso y repeticiones en cada serie."
                            TrackingType.BODYWEIGHT -> "Registra repeticiones; el peso extra o la asistencia son opcionales."
                            TrackingType.DURATION -> "Registra el tiempo de cada serie (planchas, isométricos)."
                            TrackingType.DISTANCE -> "Registra distancia y/o tiempo."
                        },
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                    Label("Tipo de movimiento")
                    ChipSelector(MovementType.values().toList(), setOf(movement), { it.label }, { movement = it })
                    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Unilateral", style = MaterialTheme.typography.titleMedium)
                            Text("Registrar cada lado por separado", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        Switch(checked = unilateral, onCheckedChange = { unilateral = it }, colors = gymSwitchColors())
                    }
                    OutlinedTextField(
                        value = description, onValueChange = { description = it }, label = { Text("Descripción breve") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), colors = fieldColors(), minLines = 2,
                    )
                    OutlinedTextField(
                        value = cues, onValueChange = { cues = it }, label = { Text("Indicaciones técnicas (opcional)") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), colors = fieldColors(), minLines = 2,
                    )
                }
                BigButton(
                    "Guardar ejercicio",
                    onClick = {
                        onSave(ExerciseForm(name, primary, secondary, equipment, movement, tracking, unilateral, description, cues)) { err -> error = err }
                    },
                    modifier = Modifier.padding(ScreenPadding).navigationBarsPaddingCompat(),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = Brand.colors.textMuted,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
}

private fun Modifier.navigationBarsPaddingCompat(): Modifier = this.then(Modifier.padding(bottom = 8.dp))

@Composable
fun ExerciseDetailDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEvolution: (() -> Unit)? = null,
) {
    val c = Brand.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(exercise.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseAnimation(exercise.id)
                Fact("Músculo principal", exercise.primaryMuscle.label)
                if (exercise.secondaryMuscles.isNotEmpty()) Fact("Secundarios", exercise.secondaryMuscles.joinToString { it.label })
                Fact("Equipamiento", exercise.equipment.label)
                Fact("Movimiento", exercise.movement.label)
                Fact("Registro", exercise.tracking.label + if (exercise.unilateral) " · por lado" else "")
                if (exercise.description.isNotBlank()) {
                    Text(exercise.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (exercise.cues.isNotBlank()) {
                    Fact("Técnica", exercise.cues)
                }
                if (onEvolution != null) SecondaryButton("Ver evolución", onEvolution, Modifier.fillMaxWidth())
                if (onEdit != null) SecondaryButton("Editar", onEdit, Modifier.fillMaxWidth())
                if (onDelete != null) SecondaryButton("Eliminar", onDelete, Modifier.fillMaxWidth(), destructive = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.padding(0.dp)) { Text("Cerrar", color = c.primary) } },
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Brand.colors.textMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onEvolution: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Exercise?>(null) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Exercise?>(null) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        com.gymtrack.ui.components.ScreenHeader(
            "Ejercicios", onBack = onBack, subtitle = "${state.total} en la biblioteca",
            actions = {
                IconButton(onClick = { creating = true }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.Add, contentDescription = "Crear ejercicio", tint = Brand.colors.primary)
                }
            },
        )
        ExerciseBrowser(state, vm, onClick = { selected = it }, onCreate = { creating = true }, modifier = Modifier.weight(1f))
    }

    selected?.let { ex ->
        val used = (state.usage[ex.id] ?: 0) > 0
        ExerciseDetailDialog(
            exercise = ex,
            onDismiss = { selected = null },
            onEvolution = if (used) ({ selected = null; onEvolution(ex.id) }) else null,
            onEdit = if (ex.custom) ({ editing = ex; selected = null }) else null,
            onDelete = if (ex.custom) ({ confirmDelete = ex; selected = null }) else null,
        )
    }
    if (creating || editing != null) {
        val initial = editing
        ExerciseEditorDialog(
            initial = initial,
            onDismiss = { creating = false; editing = null },
            onSave = { f, done ->
                vm.saveCustom(initial?.id, f.name, f.primary, f.secondary, f.equipment, f.movement, f.tracking, f.unilateral, f.description, f.cues) { err ->
                    if (err == null) { creating = false; editing = null }
                    done(err)
                }
            },
        )
    }
    confirmDelete?.let { ex ->
        com.gymtrack.ui.components.ConfirmDialog(
            title = "Eliminar ejercicio",
            message = "«${ex.name}» dejará de aparecer en la biblioteca. Si está en rutinas o en tu historial, esos datos se conservan.",
            confirmText = "Eliminar", destructive = true,
            onConfirm = { vm.deleteCustom(ex.id); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}
