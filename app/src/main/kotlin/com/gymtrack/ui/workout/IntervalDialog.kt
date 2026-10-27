package com.gymtrack.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import com.gymtrack.domain.IntervalPlan
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.IntervalPhase
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.theme.TimerStyle
import kotlin.math.ceil

/** Configuración (antes de empezar) y pantalla de cuenta atrás del modo intervalos. */
@Composable
fun IntervalDialog(
    setup: IntervalSetup?,
    run: IntervalUi?,
    onBegin: (IntervalSetup) -> Unit,
    onCancelSetup: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    if (run != null) {
        IntervalRunScreen(run, onPause, onResume, onStop, contentPadding)
    } else if (setup != null) {
        IntervalSetupDialog(setup, onBegin, onCancelSetup)
    }
}

@Composable
private fun IntervalSetupDialog(setup: IntervalSetup, onBegin: (IntervalSetup) -> Unit, onCancel: () -> Unit) {
    val c = Brand.colors
    var work by remember(setup) { mutableStateOf(setup.workSec) }
    var rest by remember(setup) { mutableStateOf(setup.restSec) }
    var rounds by remember(setup) { mutableStateOf(setup.rounds) }
    var cycles by remember(setup) { mutableStateOf(setup.cycles) }
    var cycleRest by remember(setup) { mutableStateOf(setup.cycleRestSec) }
    var prep by remember(setup) { mutableStateOf(setup.prepSec) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.surfaceHigh,
        title = { Text("Modo intervalos") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(setup.exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cuenta atrás con aviso en cada cambio. Cada ronda de trabajo completa una serie (si faltan series, se añaden).",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Tabata 20/10×8" to Triple(20, 10, 8), "30/30" to Triple(30, 30, rounds), "40/20" to Triple(40, 20, rounds)).forEach { (label, p) ->
                        FilterChip(
                            selected = work == p.first && rest == p.second,
                            onClick = { work = p.first; rest = p.second; rounds = p.third },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = c.surface, labelColor = c.text,
                                selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
                            ),
                        )
                    }
                }
                StepRow("Trabajo (s)", work, { work = it }, 5..300)
                StepRow("Descanso entre rondas (s)", rest, { rest = it }, 0..120)
                StepRow("Rondas por ciclo", rounds, { rounds = it }, 1..IntervalPlan.MAX_ROUNDS, step = 1)
                StepRow("Ciclos", cycles, { cycles = it }, 1..IntervalPlan.MAX_CYCLES, step = 1)
                if (cycles > 1) StepRow("Descanso entre ciclos (s)", cycleRest, { cycleRest = it }, 0..300)
                StepRow("Cuenta atrás de inicio (s)", prep, { prep = it }, 0..30, step = 1)
                val plan = IntervalPlan(work, rest, rounds, prep, cycles, if (cycles > 1) cycleRest else 0)
                Text(
                    "${plan.totalRounds} rondas de trabajo en total · duración ${Formatting.duration(plan.totalSec)}" +
                        if (prep > 0) " (incluye $prep s de inicio)" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onBegin(setup.copy(workSec = work, restSec = rest, rounds = rounds, cycles = cycles, cycleRestSec = if (cycles > 1) cycleRest else 0, prepSec = prep)) },
                modifier = Modifier.height(MinTouch),
            ) { Text("Comenzar", color = c.primary) }
        },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.height(MinTouch)) { Text("Cancelar", color = c.text) } },
    )
}

@Composable
private fun StepRow(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 5) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.Remove, contentDescription = "Menos $label")
        }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.Add, contentDescription = "Más $label")
        }
    }
}

@Composable
private fun IntervalRunScreen(
    run: IntervalUi, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val c = Brand.colors
    val snap = run.snapshot
    val color = when (snap.phase) {
        IntervalPhase.PREP -> c.warning
        IntervalPhase.WORK, IntervalPhase.DONE -> c.primary
        IntervalPhase.REST -> c.textSecondary
        IntervalPhase.CYCLE_REST -> c.warning
    }
    val label = when (snap.phase) {
        IntervalPhase.PREP -> "PREPÁRATE"
        IntervalPhase.WORK -> "¡TRABAJA!"
        IntervalPhase.REST -> "DESCANSA"
        IntervalPhase.CYCLE_REST -> "DESCANSO ENTRE CICLOS"
        IntervalPhase.DONE -> "¡LISTO!"
    }
    val seconds = ceil(snap.phaseRemainingMs / 1000.0).toInt()
    // Superposición dentro de la pantalla (no una ventana aparte): mismos márgenes que el resto de la app.
    androidx.activity.compose.BackHandler { /* no se sale por accidente: usa Pausar o Terminar */ }
    run {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Column(
                Modifier.fillMaxSize()
                    .padding(contentPadding).consumeWindowInsets(contentPadding)
                    .padding(ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(run.exerciseName, style = MaterialTheme.typography.titleLarge, color = c.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text(label, style = MaterialTheme.typography.headlineLarge, color = color, fontWeight = FontWeight.Bold)
                if (!snap.finished) {
                    AnimatedContent(
                        targetState = seconds,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) },
                        label = "intervalSeconds",
                        modifier = Modifier.semantics { contentDescription = "$label, quedan $seconds segundos" },
                    ) { sec ->
                        Text("$sec", style = TimerStyle, fontSize = 160.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                    LinearProgressIndicator(
                        progress = { if (snap.phaseTotalMs <= 0) 0f else (snap.phaseRemainingMs.toFloat() / snap.phaseTotalMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(0.8f), color = color, trackColor = c.surfaceHigh,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (snap.round > 0) Text(
                        (if (run.plan.cycles > 1) "Ciclo ${snap.cycle} de ${run.plan.cycles} · " else "") + "Ronda ${snap.round} de ${run.plan.rounds}",
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    )
                    Text("Quedan ${Formatting.duration(Math.ceil(snap.totalRemainingMs / 1000.0).toInt())}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                } else {
                    Text("${run.plan.totalRounds} rondas completadas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                    Text("Tus series ya están registradas en el entrenamiento.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(32.dp))
                if (snap.finished) {
                    BigButton("Cerrar", onStop)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(if (run.paused) "Reanudar" else "Pausar", if (run.paused) onResume else onPause, Modifier.weight(1f).height(56.dp))
                        SecondaryButton("Terminar", onStop, Modifier.weight(1f).height(56.dp), destructive = true)
                    }
                    Text("«Terminar» conserva las rondas ya completadas.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
