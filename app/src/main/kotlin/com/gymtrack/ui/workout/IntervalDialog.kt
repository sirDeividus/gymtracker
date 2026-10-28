package com.gymtrack.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymtrack.domain.IntervalPhase
import com.gymtrack.domain.IntervalPlan
import com.gymtrack.domain.IntervalTimer
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.NumberInput
import com.gymtrack.ui.theme.Brand
import kotlin.math.ceil

// Paleta del temporizador, coherente con la app: fondo oscuro, rojo de marca para el trabajo y tonos fríos/neutros
// para el resto. El color de la fase tiñe suavemente el fondo y los números.
private val RestBlue = Color(0xFF38BDF8)
private val DoneGreen = Color(0xFF34C759)

@Composable
private fun phaseColor(p: IntervalPhase): Color {
    val c = Brand.colors
    return when (p) {
        IntervalPhase.PREP -> c.text
        IntervalPhase.WORK -> c.primary
        IntervalPhase.REST -> RestBlue
        IntervalPhase.CYCLE_REST -> c.warning
        IntervalPhase.DONE -> DoneGreen
    }
}

private fun phaseTitle(p: IntervalPhase) = when (p) {
    IntervalPhase.PREP -> "PREPÁRATE"
    IntervalPhase.WORK -> "ENTRENAR"
    IntervalPhase.REST -> "DESCANSA"
    IntervalPhase.CYCLE_REST -> "DESCANSO DE CICLO"
    IntervalPhase.DONE -> "¡LISTO!"
}

private fun nextTitle(p: IntervalPhase) = when (p) {
    IntervalPhase.PREP -> "PREPÁRATE"
    IntervalPhase.WORK -> "ENTRENAR"
    IntervalPhase.REST -> "DESCANSAR"
    IntervalPhase.CYCLE_REST -> "DESCANSO DE CICLO"
    IntervalPhase.DONE -> "FIN"
}

private fun mmss(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

/** Temporizador de intervalos a pantalla completa (superposición) y su edición. */
@Composable
fun IntervalDialog(
    setup: IntervalSetup?,
    run: IntervalUi?,
    onPlayPause: () -> Unit,
    onEdit: () -> Unit,
    onSaveConfig: (IntervalSetup) -> Unit,
    onCancelEdit: () -> Unit,
    onToggleSound: () -> Unit,
    onStop: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (run != null) IntervalRunScreen(run, onPlayPause, onEdit, onToggleSound, onStop, contentPadding)
    if (setup != null) IntervalEditDialog(setup, onSaveConfig, onCancelEdit)
}

@Composable
private fun IntervalRunScreen(
    run: IntervalUi,
    onPlayPause: () -> Unit,
    onEdit: () -> Unit,
    onToggleSound: () -> Unit,
    onStop: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = Brand.colors
    val snap = run.snapshot
    val plan = run.plan
    var showList by remember { mutableStateOf(false) }
    val color = phaseColor(snap.phase)
    val nextColor = phaseColor(run.nextPhase)
    val seconds = if (snap.finished) 0 else ceil(snap.phaseRemainingMs / 1000.0).toInt()
    val totalLeft = ceil(snap.totalRemainingMs / 1000.0).toInt()
    // Rondas y ciclos que quedan, contando la actual.
    val roundsLeft = when (snap.phase) {
        IntervalPhase.PREP, IntervalPhase.CYCLE_REST -> plan.rounds
        IntervalPhase.WORK -> plan.rounds - snap.round + 1
        IntervalPhase.REST -> plan.rounds - snap.round
        IntervalPhase.DONE -> 0
    }
    val cyclesLeft = when (snap.phase) {
        IntervalPhase.PREP -> plan.cycles
        IntervalPhase.WORK, IntervalPhase.REST -> plan.cycles - snap.cycle + 1
        IntervalPhase.CYCLE_REST -> plan.cycles - snap.cycle
        IntervalPhase.DONE -> 0
    }
    val progress = if (snap.phaseTotalMs <= 0) 0f else (snap.phaseRemainingMs.toFloat() / snap.phaseTotalMs).coerceIn(0f, 1f)
    BackHandler { /* no se sale por accidente: usa la X */ }
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), c.background), endY = 1400f))
                .padding(contentPadding).consumeWindowInsets(contentPadding).padding(horizontal = 20.dp),
        ) {
            // ---- Barra superior: cerrar · ejercicio · tiempo total
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape).clickable(onClick = onStop)
                        .semantics { contentDescription = "Cerrar el temporizador" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, contentDescription = null, tint = c.text, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(run.exerciseName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("Quedan ${mmss(totalLeft)}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            // ---- Acciones secundarias como pastillas discretas
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Editar", Icons.Default.Tune, onEdit)
                Pill(if (run.muted) "Sin sonido" else "Sonido", if (run.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, onToggleSound)
                Pill("Intervalos", null) { showList = true }
            }
            Spacer(Modifier.weight(1f))
            // ---- Fase + número gigante + barra fina
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text(
                        "  " + phaseTitle(snap.phase), color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp,
                    )
                }
                Text(
                    mmss(seconds), color = c.text, fontSize = 112.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, maxLines = 1,
                    modifier = Modifier.padding(vertical = 4.dp)
                        .semantics { contentDescription = "${phaseTitle(snap.phase)}, quedan $seconds segundos" },
                )
                Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape).background(c.surfaceHigh)) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(color))
                }
                if (snap.round > 0 && !snap.finished) {
                    Text(
                        (if (plan.cycles > 1) "Ciclo ${snap.cycle}/${plan.cycles}  ·  " else "") + "Ronda ${snap.round}/${plan.rounds}",
                        color = c.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // ---- Siguiente
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("SIGUIENTE  ", color = c.textMuted, fontSize = 12.sp, letterSpacing = 2.sp)
                Box(Modifier.size(7.dp).clip(CircleShape).background(nextColor))
                Text("  " + nextTitle(run.nextPhase), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            // ---- Rondas · empezar/pausar · ciclos
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Counter("RONDAS", roundsLeft, Modifier.weight(1f))
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(c.primaryGradient).clickable(onClick = onPlayPause)
                        .semantics { contentDescription = if (run.paused) (if (run.started) "Reanudar" else "Empezar") else "Pausar" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (snap.finished) Icons.Default.Check else if (run.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null, tint = c.onGradient, modifier = Modifier.size(44.dp),
                    )
                }
                Counter("CICLOS", cyclesLeft, Modifier.weight(1f))
            }
        }
    }
    if (showList) IntervalListDialog(plan, snap.phase, snap.cycle, snap.round) { showList = false }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier.height(36.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = c.text, fontSize = 13.sp)
    }
}

@Composable
private fun Counter(label: String, value: Int, modifier: Modifier) {
    val c = Brand.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = c.text, fontSize = 36.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
        Text(label, color = c.textMuted, fontSize = 11.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun IntervalListDialog(plan: IntervalPlan, phase: IntervalPhase, cycle: Int, round: Int, onDismiss: () -> Unit) {
    val c = Brand.colors
    val steps = remember(plan) { IntervalTimer.schedule(plan) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Intervalos") },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                itemsIndexed(steps) { i, st ->
                    val current = st.phase == phase && st.cycle == cycle && st.round == round
                    val name = when (st.phase) {
                        IntervalPhase.PREP -> "Preparación"
                        IntervalPhase.WORK -> (if (plan.cycles > 1) "C${st.cycle} · " else "") + "Entrenar ${st.round}"
                        IntervalPhase.REST -> "Descansar"
                        IntervalPhase.CYCLE_REST -> "Descanso de ciclo"
                        IntervalPhase.DONE -> "Fin"
                    }
                    Row(
                        Modifier.fillMaxWidth().background(if (current) phaseColor(st.phase).copy(alpha = 0.25f) else Color.Transparent).padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(14.dp).background(phaseColor(st.phase)))
                        Text("  $name", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(mmss(st.seconds), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.primary) } },
    )
}

/** Edición simple: escribes los números directamente (sin tocar «+» muchas veces). */
@Composable
private fun IntervalEditDialog(setup: IntervalSetup, onSave: (IntervalSetup) -> Unit, onCancel: () -> Unit) {
    val c = Brand.colors
    var work by remember(setup) { mutableStateOf(setup.workSec.toString()) }
    var rest by remember(setup) { mutableStateOf(setup.restSec.toString()) }
    var rounds by remember(setup) { mutableStateOf(setup.rounds.toString()) }
    var cycles by remember(setup) { mutableStateOf(setup.cycles.toString()) }
    var cycleRest by remember(setup) { mutableStateOf(setup.cycleRestSec.toString()) }
    var prep by remember(setup) { mutableStateOf(setup.prepSec.toString()) }
    fun preset(w: Int, r: Int, n: Int) { work = "$w"; rest = "$r"; rounds = "$n" }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.surfaceHigh,
        title = { Text("Editar temporizador") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Tabata" to Triple(20, 10, 8), "30/30" to Triple(30, 30, 10), "40/20" to Triple(40, 20, 10)).forEach { (label, p) ->
                        FilterChip(
                            selected = work == "${p.first}" && rest == "${p.second}" && rounds == "${p.third}",
                            onClick = { preset(p.first, p.second, p.third) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = c.surface, labelColor = c.text,
                                selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Trabajo (s)", work, { work = it }, Modifier.weight(1f))
                    Field("Descanso (s)", rest, { rest = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Rondas", rounds, { rounds = it }, Modifier.weight(1f))
                    Field("Ciclos", cycles, { cycles = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Descanso entre ciclos (s)", cycleRest, { cycleRest = it }, Modifier.weight(1f))
                    Field("Cuenta atrás inicial (s)", prep, { prep = it }, Modifier.weight(1f))
                }
                Text(
                    "Cada ronda de trabajo completa una serie del ejercicio; si faltan series, se añaden.",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    fun n(s: String, def: Int) = s.toIntOrNull() ?: def
                    onSave(
                        setup.copy(
                            workSec = n(work, setup.workSec), restSec = n(rest, setup.restSec), rounds = n(rounds, setup.rounds),
                            cycles = n(cycles, setup.cycles), cycleRestSec = n(cycleRest, setup.cycleRestSec), prepSec = n(prep, setup.prepSec),
                        ),
                    )
                },
                modifier = Modifier.height(MinTouch),
            ) { Text("Guardar", color = c.primary) }
        },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.height(MinTouch)) { Text("Cancelar", color = c.text) } },
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.colors.textSecondary, maxLines = 2)
        NumberInput(value, "0", onChange, Modifier.fillMaxWidth(), decimal = false, description = label)
    }
}
