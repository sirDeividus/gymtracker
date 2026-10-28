package com.gymtrack.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import com.gymtrack.data.db.enumOr
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.SetType
import com.gymtrack.ui.components.MiniMuscleMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.domain.Formatting
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.ConfirmDialog
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.GradientCapsuleButton
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.library.ExercisePickerSheet
import com.gymtrack.ui.library.LibraryViewModel
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.theme.ClockStyle
import com.gymtrack.ui.theme.TimerStyle

@Composable
fun WorkoutScreen(
    vm: WorkoutViewModel,
    libraryVm: LibraryViewModel,
    contentPadding: PaddingValues,
    onFinished: (String) -> Unit,
    onLeave: () -> Unit,
    onOpenSettings: () -> Unit = {},
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
    val haptic = LocalHapticFeedback.current
    val hapticsOn = state.settings.vibration
    val actions = remember(vm, hapticsOn) {
        ExerciseActions(
            setWeight = vm::setWeight, setReps = vm::setReps, setDuration = vm::setDuration, setDistance = vm::setDistance,
            toggleComplete = { set ->
                // Pulso leve solo al completar (no al deshacer), y solo si el usuario tiene la vibración activada.
                if (!set.completed && hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.toggleComplete(set)
            }, repeatPrevious = vm::repeatPrevious, setRpe = vm::setRpe,
            addSet = vm::addSet, removeSet = vm::removeSet, setSkipped = vm::setSkipped, removeExercise = vm::removeExercise,
            setNotes = vm::setNotes, setDiscomfort = vm::setDiscomfort, applySuggestion = vm::applySuggestion,
            dismissSuggestion = vm::dismissSuggestion, setBodyweightMode = vm::setBodyweightMode,
            setMinutes = vm::setMinutes, startInterval = vm::openIntervalSetup,
            setType = vm::setType, copyPrevious = vm::copyPrevious, addWarmups = vm::addWarmups, setRest = vm::setRest,
        )
    }
    val progress by animateFloatAsState(workout.progress, label = "progress")

    // Bloqueo durante el descanso: si guardas el móvil en el bolsillo, los toques accidentales no tocan nada.
    // Se desbloquea manteniendo pulsado el candado; al acabar el descanso se libera solo.
    val restActive = state.rest != null
    var unlockedFor by remember { mutableStateOf<Long?>(null) }
    val restKey = state.rest?.endsAtMs ?: state.rest?.totalSec?.toLong()
    val locked = restActive && unlockedFor != restKey
    LaunchedEffect(restActive) { if (!restActive) unlockedFor = null }
    androidx.activity.compose.BackHandler(enabled = locked) { /* ignorado mientras está bloqueado */ }

    val volumeKg = workout.exercises.sumOf { ex ->
        ex.sets.filter { it.completed && it.setType != SetType.WARMUP.name }
            .sumOf { (it.weightKg ?: 0.0).coerceAtLeast(0.0) * (it.reps ?: 0) }
    }
    val muscleSets = remember(workout.exercises.map { it.entity.id to it.sets.size }) {
        workout.exercises.filter { !it.entity.skipped }
            .groupBy { enumOr(it.entity.primaryMuscle, MuscleGroup.FULL_BODY) }
            .mapValues { (_, l) -> l.sumOf { it.sets.size } }
    }

    val intervalSetup by vm.intervalSetup.collectAsStateWithLifecycle()
    val intervalRun by vm.interval.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
            // ---- Cabecera: minimizar · cronómetro · despertador (descanso) · Terminar
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLeave, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimizar (el entrenamiento se conserva)", modifier = Modifier.size(30.dp))
                }
                Text(
                    Formatting.duration(state.elapsedSec.toInt()),
                    style = MaterialTheme.typography.titleLarge, color = c.primary, maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Tiempo transcurrido ${Formatting.duration(state.elapsedSec.toInt())}" },
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Default.Alarm, contentDescription = "Descanso", modifier = Modifier.size(28.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Iniciar descanso (${state.settings.defaultRestSec} s)") }, onClick = { menu = false; vm.startRest(state.settings.defaultRestSec) })
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(c.primaryGradient)
                        .clickable { finishDialog = true }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Terminar", style = MaterialTheme.typography.titleMedium, color = c.onGradient) }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = c.primary, trackColor = c.surfaceHigh, gapSize = 0.dp, drawStopIndicator = {},
            )

            // ---- Estadísticas rápidas + mapa corporal
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatCell("Duración", Formatting.duration(state.elapsedSec.toInt()), Modifier.weight(1f), accent = true)
                StatCell("Volumen", Formatting.weight(volumeKg, unit), Modifier.weight(1f))
                StatCell("Series", "${workout.completedSets}", Modifier.weight(0.7f))
                MiniMuscleMap(muscleSets, Modifier.width(64.dp))
            }
            androidx.compose.material3.HorizontalDivider(color = c.outline)
            if (state.settings.musicControls) MusicBar(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp))

            // ---- Ejercicios
            // Al terminar un ejercicio, la lista se desplaza sola al siguiente (y al abrir, al que toca).
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(currentId) {
                val idx = workout.exercises.indexOfFirst { it.entity.id == currentId }
                if (idx >= 0) listState.animateScrollToItem(idx)
            }
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (workout.exercises.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Default.FitnessCenter, "Entrenamiento libre",
                            "Añade el primer ejercicio para empezar a registrar series.",
                        )
                    }
                }
                items(workout.exercises, key = { it.entity.id }) { ex ->
                    ExerciseCard(
                        item = ex, expanded = true, onToggleExpanded = {},
                        unit = unit, prs = state.prs, actions = actions,
                    )
                }
                item(key = "footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientCapsuleButton("AGREGAR EJERCICIO", { picker = true }, Modifier.fillMaxWidth().heightIn(min = 54.dp), icon = Icons.Default.Add)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FooterButton("Configuración", c.text, Modifier.weight(1f), onOpenSettings)
                            FooterButton("Descartar Entreno", c.error, Modifier.weight(1f)) { discardDialog = true }
                        }
                    }
                }
            }

            // ---- Descanso
            state.rest?.let { rest ->
                RestPanel(
                    remainingSec = state.restRemainingSec, totalSec = rest.totalSec, paused = rest.paused,
                    locked = locked,
                    onAdjust = vm::adjustRest,
                    onPauseResume = { if (rest.paused) vm.resumeRest() else vm.pauseRest() },
                    onRestart = vm::restartRest, onSkip = vm::skipRest,
                    onUnlock = { unlockedFor = restKey },
                )
            }
        }
        // Capa de bloqueo: se traga todos los toques de lo que hay debajo (salvo el panel de descanso, que queda encima).
        if (locked) {
            Box(
                Modifier.fillMaxSize().padding(bottom = 110.dp + contentPadding.calculateBottomPadding())
                    .background(c.background.copy(alpha = 0.45f))
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
            )
        }
        IntervalDialog(
            setup = intervalSetup, run = intervalRun,
            onPlayPause = vm::playPauseInterval, onEdit = vm::editInterval,
            onSaveConfig = vm::applyIntervalConfig, onCancelEdit = vm::cancelIntervalSetup,
            onToggleSound = vm::toggleIntervalSound, onStop = vm::stopInterval,
            contentPadding = contentPadding,
        )
        if (picker) ExercisePickerSheet(libraryVm, onPick = { vm.addExercise(it); picker = false }, onDismiss = { picker = false }, contentPadding = contentPadding)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp + contentPadding.calculateBottomPadding()))
    }



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
    locked: Boolean,
    onAdjust: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onRestart: () -> Unit,
    onSkip: () -> Unit,
    onUnlock: () -> Unit,
) {
    val c = Brand.colors
    GymCard(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        highlighted = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (paused) "DESCANSO EN PAUSA" else "DESCANSO", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                AnimatedContent(
                    targetState = remainingSec,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                    label = "restSeconds",
                    modifier = Modifier.semantics { contentDescription = "Descanso, quedan ${Formatting.duration(remainingSec)}" },
                ) { sec ->
                    Text(
                        Formatting.duration(sec), style = MaterialTheme.typography.headlineLarge.merge(TimerStyle),
                        color = if (paused) c.warning else c.primary,
                    )
                }
            }
            if (locked) {
                // Bloqueado: solo un gesto deliberado (mantener pulsado) libera los controles.
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { onUnlock() }) }
                        .heightIn(min = MinTouch).padding(horizontal = 14.dp)
                        .semantics { contentDescription = "Pantalla bloqueada durante el descanso. Mantén pulsado para desbloquear" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mantén para desbloquear", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(onClick = { onAdjust(-15) }, modifier = Modifier.heightIn(min = MinTouch)) { Text("−15", color = c.text) }
                TextButton(onClick = { onAdjust(15) }, modifier = Modifier.heightIn(min = MinTouch)) { Text("+15", color = c.text) }
                IconButton(onClick = onPauseResume, modifier = Modifier.size(MinTouch)) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "Reanudar descanso" else "Pausar descanso")
                }
                IconButton(onClick = onRestart, modifier = Modifier.size(MinTouch)) { Icon(Icons.Default.Refresh, contentDescription = "Reiniciar descanso") }
                IconButton(onClick = onSkip, modifier = Modifier.size(MinTouch)) { Icon(Icons.Default.SkipNext, contentDescription = "Saltar descanso") }
            }
        }
        // La barra se desliza de forma continua en vez de saltar cada segundo.
        val restProgress by animateFloatAsState(
            targetValue = if (totalSec <= 0) 0f else (remainingSec.toFloat() / totalSec).coerceIn(0f, 1f),
            animationSpec = tween(900, easing = LinearEasing), label = "restProgress",
        )
        LinearProgressIndicator(
            progress = { restProgress },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = c.primary, trackColor = c.surfaceHigh,
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = Brand.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = if (accent) c.primary else c.text, maxLines = 1)
    }
}

@Composable
private fun FooterButton(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(Brand.colors.surfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1) }
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
