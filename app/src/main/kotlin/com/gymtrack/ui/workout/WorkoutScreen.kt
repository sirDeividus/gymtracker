package com.gymtrack.ui.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.domain.Formatting
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.ConfirmDialog
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.library.ExercisePickerSheet
import com.gymtrack.ui.library.LibraryViewModel
import com.gymtrack.ui.theme.Brand

@Composable
fun WorkoutScreen(
    vm: WorkoutViewModel,
    libraryVm: LibraryViewModel,
    contentPadding: PaddingValues,
    onFinished: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val c = Brand.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var picker by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var expandedOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    // Entre series suele haber el móvil en la mano o apoyado: no queremos que se apague la pantalla.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val workout = state.workout
    // Si la sesión ya no está en curso (finalizada o descartada), salimos de la pantalla.
    LaunchedEffect(state.loading, workout == null) { if (!state.loading && workout == null) onLeave() }

    if (workout == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            EmptyState(Icons.Default.FitnessCenter, "Sin entrenamiento en curso", "Inicia una rutina desde Inicio o Rutinas.", actionLabel = "Volver", onAction = onLeave)
        }
        return
    }

    val unit = state.settings.unit
    val currentId = workout.exercises.firstOrNull { !it.finished }?.entity?.id
    val actions = remember(vm) {
        ExerciseActions(
            setWeight = vm::setWeight, setReps = vm::setReps, setDuration = vm::setDuration, setDistance = vm::setDistance,
            toggleComplete = vm::toggleComplete, repeatPrevious = vm::repeatPrevious, setRpe = vm::setRpe,
            addSet = vm::addSet, removeSet = vm::removeSet, setSkipped = vm::setSkipped, removeExercise = vm::removeExercise,
            setNotes = vm::setNotes, setDiscomfort = vm::setDiscomfort, applySuggestion = vm::applySuggestion,
            dismissSuggestion = vm::dismissSuggestion, setBodyweightMode = vm::setBodyweightMode,
            setMinutes = vm::setMinutes, startInterval = vm::openIntervalSetup,
        )
    }
    val progress by animateFloatAsState(workout.progress, label = "progress")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
            // ---- Cabecera
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLeave, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir sin finalizar (el entrenamiento se conserva)")
                }
                Column(Modifier.weight(1f)) {
                    Text(workout.session.routineName, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        "${workout.finishedExercises}/${workout.exercises.size} ejercicios · ${workout.completedSets}/${workout.totalSets} series",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Text(
                    Formatting.duration(state.elapsedSec.toInt()),
                    style = MaterialTheme.typography.titleLarge, color = c.primary,
                    modifier = Modifier.semantics { contentDescription = "Tiempo transcurrido ${Formatting.duration(state.elapsedSec.toInt())}" },
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Iniciar descanso (${state.settings.defaultRestSec} s)") }, onClick = { menu = false; vm.startRest(state.settings.defaultRestSec) })
                        DropdownMenuItem(text = { Text("Descartar entrenamiento", color = c.error) }, onClick = { menu = false; discardDialog = true })
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
                color = c.primary, trackColor = c.surfaceHigh,
            )

            // ---- Ejercicios
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (workout.exercises.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Default.FitnessCenter, "Entrenamiento libre",
                            "Añade el primer ejercicio para empezar a registrar series.",
                            actionLabel = "Añadir ejercicio", onAction = { picker = true },
                        )
                    }
                }
                items(workout.exercises, key = { it.entity.id }) { ex ->
                    val open = expandedOverrides[ex.entity.id] ?: (ex.entity.id == currentId)
                    ExerciseCard(
                        item = ex, expanded = open,
                        onToggleExpanded = { expandedOverrides = expandedOverrides + (ex.entity.id to !open) },
                        unit = unit, prs = state.prs, actions = actions,
                    )
                }
            }

            // ---- Descanso + acciones
            state.rest?.let { rest ->
                RestPanel(
                    remainingSec = state.restRemainingSec, totalSec = rest.totalSec, paused = rest.paused,
                    onAdjust = vm::adjustRest,
                    onPauseResume = { if (rest.paused) vm.resumeRest() else vm.pauseRest() },
                    onRestart = vm::restartRest, onSkip = vm::skipRest,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Ejercicio", { picker = true }, Modifier.weight(1f).heightIn(min = 56.dp), icon = Icons.Default.Add)
                BigButton("Finalizar", { finishDialog = true }, Modifier.weight(1.6f))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp + contentPadding.calculateBottomPadding()))
    }

    val intervalSetup by vm.intervalSetup.collectAsStateWithLifecycle()
    val intervalRun by vm.interval.collectAsStateWithLifecycle()
    IntervalDialog(
        setup = intervalSetup, run = intervalRun,
        onBegin = vm::beginInterval, onCancelSetup = vm::cancelIntervalSetup,
        onPause = vm::pauseInterval, onResume = vm::resumeInterval, onStop = vm::stopInterval,
    )

    if (picker) ExercisePickerSheet(libraryVm, onPick = { vm.addExercise(it); picker = false }, onDismiss = { picker = false })

    if (finishDialog) {
        FinishDialog(
            completedSets = workout.completedSets, totalSets = workout.totalSets,
            elapsedSec = state.elapsedSec, initialNotes = workout.session.notes,
            onConfirm = { notes -> finishDialog = false; vm.finish(notes, onFinished) },
            onDiscard = { finishDialog = false; discardDialog = true },
            onDismiss = { finishDialog = false },
        )
    }
    if (discardDialog) {
        ConfirmDialog(
            title = "Descartar entrenamiento",
            message = if (workout.completedSets > 0) {
                "Se descartarán ${workout.completedSets} series registradas y no aparecerán en tu historial. Esta acción no se puede deshacer."
            } else "Este entrenamiento aún no tiene series completadas. Se descartará.",
            confirmText = "Descartar", dismissText = "Seguir entrenando", destructive = true,
            onConfirm = { discardDialog = false; vm.discard(onLeave) },
            onDismiss = { discardDialog = false },
        )
    }
}

@Composable
private fun RestPanel(
    remainingSec: Int,
    totalSec: Int,
    paused: Boolean,
    onAdjust: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onRestart: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = Brand.colors
    GymCard(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        highlighted = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (paused) "DESCANSO EN PAUSA" else "DESCANSO", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text(
                    Formatting.duration(remainingSec), style = MaterialTheme.typography.headlineLarge,
                    color = if (paused) c.warning else c.primary,
                    modifier = Modifier.semantics { contentDescription = "Descanso, quedan ${Formatting.duration(remainingSec)}" },
                )
            }
            TextButton(onClick = { onAdjust(-15) }, modifier = Modifier.heightIn(min = MinTouch)) { Text("−15", color = c.text) }
            TextButton(onClick = { onAdjust(15) }, modifier = Modifier.heightIn(min = MinTouch)) { Text("+15", color = c.text) }
            IconButton(onClick = onPauseResume, modifier = Modifier.size(MinTouch)) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "Reanudar descanso" else "Pausar descanso")
            }
            IconButton(onClick = onRestart, modifier = Modifier.size(MinTouch)) { Icon(Icons.Default.Refresh, contentDescription = "Reiniciar descanso") }
            IconButton(onClick = onSkip, modifier = Modifier.size(MinTouch)) { Icon(Icons.Default.SkipNext, contentDescription = "Saltar descanso") }
        }
        LinearProgressIndicator(
            progress = { if (totalSec <= 0) 0f else (remainingSec.toFloat() / totalSec).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = c.primary, trackColor = c.surfaceHigh,
        )
    }
}

@Composable
private fun FinishDialog(
    completedSets: Int,
    totalSets: Int,
    elapsedSec: Long,
    initialNotes: String,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Brand.colors
    var notes by remember { mutableStateOf(initialNotes) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(if (completedSets == 0) "Aún no hay series completadas" else "Finalizar entrenamiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (completedSets == 0) {
                    Text("Para guardar la sesión, completa al menos una serie. También puedes descartarla.", color = c.textSecondary)
                } else {
                    Text("Duración ${Formatting.duration(elapsedSec.toInt())} · $completedSets de $totalSets series completadas.")
                    if (completedSets < totalSets) {
                        Text("Las ${totalSets - completedSets} series pendientes no se registrarán.", color = c.warning, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text("Notas generales (opcional)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.primary, unfocusedBorderColor = c.outline,
                            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, cursorColor = c.primary,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            if (completedSets > 0) {
                TextButton(onClick = { onConfirm(notes) }, modifier = Modifier.heightIn(min = MinTouch)) { Text("Finalizar y guardar", color = c.primary) }
            } else {
                TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = MinTouch)) { Text("Descartar", color = c.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text("Seguir entrenando", color = c.text) } },
    )
}
