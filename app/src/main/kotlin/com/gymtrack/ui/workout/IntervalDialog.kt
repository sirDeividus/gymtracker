package com.gymtrack.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    onBegin: (Int, Int) -> Unit,
    onCancelSetup: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    if (run != null) {
        IntervalRunScreen(run, onPause, onResume, onStop)
    } else if (setup != null) {
        IntervalSetupDialog(setup, onBegin, onCancelSetup)
    }
}

@Composable
private fun IntervalSetupDialog(setup: IntervalSetup, onBegin: (Int, Int) -> Unit, onCancel: () -> Unit) {
    val c = Brand.colors
    var work by remember(setup) { mutableStateOf(setup.workSec) }
    var rest by remember(setup) { mutableStateOf(setup.restSec) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.surfaceHigh,
        title = { Text("Modo intervalos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(setup.exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cuenta atrás automática con aviso en cada cambio. Cada ronda de trabajo completa una serie pendiente.",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Tabata 20/10" to (20 to 10), "30/30" to (30 to 30), "40/20" to (40 to 20)).forEach { (label, p) ->
                        FilterChip(
                            selected = work == p.first && rest == p.second,
                            onClick = { work = p.first; rest = p.second },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = c.surface, labelColor = c.text,
                                selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
                            ),
                        )
                    }
                }
                StepRow("Trabajo (s)", work, { work = it }, 5..300)
                StepRow("Descanso (s)", rest, { rest = it }, 0..120)
                Text("Rondas: ${setup.rounds} (una por serie pendiente; añade series si quieres más).", style = MaterialTheme.typography.bodySmall)
                val total = 5 + setup.rounds * work + (setup.rounds - 1) * rest
                Text("Duración total: ${Formatting.duration(total)} (incluye 5 s de preparación)", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onBegin(work, rest) }, modifier = Modifier.height(MinTouch)) { Text("Comenzar", color = c.primary) } },
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
private fun IntervalRunScreen(run: IntervalUi, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    val c = Brand.colors
    val snap = run.snapshot
    val color = when (snap.phase) {
        IntervalPhase.PREP -> c.warning
        IntervalPhase.WORK, IntervalPhase.DONE -> c.primary
        IntervalPhase.REST -> c.textSecondary
    }
    val label = when (snap.phase) {
        IntervalPhase.PREP -> "PREPÁRATE"
        IntervalPhase.WORK -> "¡TRABAJA!"
        IntervalPhase.REST -> "DESCANSA"
        IntervalPhase.DONE -> "¡LISTO!"
    }
    val seconds = ceil(snap.phaseRemainingMs / 1000.0).toInt()
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Column(
                Modifier.fillMaxSize().padding(ScreenPadding),
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
                    if (snap.round > 0) Text("Ronda ${snap.round} de ${run.plan.rounds}", style = MaterialTheme.typography.titleLarge)
                    Text("Quedan ${Formatting.duration(Math.ceil(snap.totalRemainingMs / 1000.0).toInt())}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                } else {
                    Text("${run.plan.rounds} rondas completadas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
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
