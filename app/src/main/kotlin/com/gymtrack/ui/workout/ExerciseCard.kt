package com.gymtrack.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymtrack.data.db.SetRecordEntity
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.repo.ActiveExercise
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.PersonalRecord
import com.gymtrack.domain.RecordType
import com.gymtrack.domain.Side
import com.gymtrack.domain.SuggestionKind
import com.gymtrack.domain.TrackingType
import com.gymtrack.domain.WeightUnit
import com.gymtrack.ui.components.ExerciseMediaDialog
import com.gymtrack.ui.components.ExerciseThumb
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.hasExerciseMedia
import com.gymtrack.ui.components.pressScale
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.NumberInput
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.theme.ExerciseTitleStyle
import kotlin.math.abs

/** Acciones que la tarjeta delega en el ViewModel. */
class ExerciseActions(
    val setWeight: (String, String, Int) -> Unit,
    val setReps: (String, String) -> Unit,
    val setDuration: (String, String) -> Unit,
    val setDistance: (String, String) -> Unit,
    val toggleComplete: (SetRecordEntity) -> Unit,
    val repeatPrevious: (String) -> Unit,
    val setRpe: (String, Double?) -> Unit,
    val addSet: (String) -> Unit,
    val removeSet: (String) -> Unit,
    val setSkipped: (String, Boolean) -> Unit,
    val removeExercise: (String) -> Unit,
    val setNotes: (String, String) -> Unit,
    val setDiscomfort: (String, Boolean) -> Unit,
    val applySuggestion: (String) -> Unit,
    val dismissSuggestion: (String) -> Unit,
    val setBodyweightMode: (List<SetRecordEntity>, Int) -> Unit,
    val setMinutes: (String, String) -> Unit,
    val startInterval: (String) -> Unit,
)

private fun repsRange(min: Int, max: Int) = if (min == max) "$min" else "$min–$max"

/** Texto de la columna ANTERIOR: solo datos reales de la última sesión del mismo ejercicio. */
fun previousText(s: SetRecordEntity, tracking: TrackingType, unit: WeightUnit): String? = when (tracking) {
    TrackingType.WEIGHT_REPS ->
        if (s.prevWeightKg != null && s.prevReps != null) "${Formatting.weight(s.prevWeightKg, unit)} × ${s.prevReps}" else null
    TrackingType.BODYWEIGHT -> when {
        s.prevReps == null -> null
        s.prevWeightKg != null && abs(s.prevWeightKg) > 1e-9 -> "${Formatting.bodyweightLoad(s.prevWeightKg, unit)} × ${s.prevReps}"
        else -> "${s.prevReps} reps"
    }
    TrackingType.DURATION -> s.prevDurationSec?.let { Formatting.duration(it) }
    TrackingType.DISTANCE -> listOfNotNull(
        s.prevDistanceM?.let { Formatting.distance(it) },
        s.prevDurationSec?.let { Formatting.duration(it) },
    ).joinToString(" · ").ifEmpty { null }
}

private fun recordText(list: List<PersonalRecord>, unit: WeightUnit): String {
    val top = list.first()
    return when (top.type) {
        RecordType.WEIGHT -> "Nuevo peso máximo: ${Formatting.weight(top.value, unit)}"
        RecordType.REPS -> "Récord de repeticiones: ${Formatting.number(top.value, 0)}"
        RecordType.ESTIMATED_1RM -> "Nuevo 1RM estimado: ${Formatting.weight(top.value, unit)}"
        RecordType.DURATION -> "Mayor duración: ${Formatting.duration(top.value.toInt())}"
        RecordType.DISTANCE -> "Mayor distancia: ${Formatting.distance(top.value)}"
    }
}

@Composable
fun ExerciseCard(
    item: ActiveExercise,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    unit: WeightUnit,
    prs: Map<String, List<PersonalRecord>>,
    actions: ExerciseActions,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    val e = item.entity
    val tracking = enumOr(e.tracking, TrackingType.WEIGHT_REPS)
    var menu by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var showNotes by remember(e.id) { mutableStateOf(e.notes.isNotBlank()) }
    val done = item.finished && !e.skipped
    val hasHistory = item.sets.any { it.prevWeightKg != null || it.prevReps != null || it.prevDurationSec != null || it.prevDistanceM != null }

    // Peso corporal: por defecto sin peso externo; el usuario puede elegir lastre o asistencia.
    val derivedMode = when {
        item.sets.any { (it.weightKg ?: 0.0) < 0 } -> -1
        item.sets.any { (it.weightKg ?: 0.0) > 0 } -> 1
        else -> 0
    }
    var bwMode by remember(e.id) { mutableStateOf(derivedMode) }

    MediaHost(showMedia, e.exerciseId, e.exerciseName) { showMedia = false }
    GymCard(
        modifier.fillMaxWidth(),
        highlighted = expanded && !e.skipped,
        contentPadding = PaddingValues(0.dp),
    ) {
        // ---- Cabecera
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(if (done) c.primary else c.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Default.Check, contentDescription = "Completado", tint = c.onPrimary, modifier = Modifier.size(16.dp))
                else Text("${item.completedSets}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
            Spacer(Modifier.width(12.dp))
            if (hasExerciseMedia(e.exerciseId)) {
                ExerciseThumb(e.exerciseId, Modifier.clickable { showMedia = true }.semantics { contentDescription = "Ver demostración de ${e.exerciseName}" }, size = 64.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    e.exerciseName.uppercase(), style = ExerciseTitleStyle,
                    color = if (e.skipped) c.textSecondary else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val target = when (tracking) {
                    TrackingType.DURATION -> "${e.targetSets} × ${Formatting.duration(e.targetDurationSec ?: 30)}"
                    TrackingType.DISTANCE -> "${e.targetSets} series"
                    else -> "${e.targetSets} × ${repsRange(e.repMin, e.repMax)} reps"
                }
                val loadKg = item.sets.mapNotNull { it.weightKg }.maxOrNull()
                val weightLabel = when {
                    tracking == TrackingType.WEIGHT_REPS && loadKg != null && loadKg > 0 -> " · ${Formatting.weight(loadKg, unit)}"
                    tracking == TrackingType.BODYWEIGHT && loadKg != null && loadKg != 0.0 -> " · ${Formatting.bodyweightLoad(loadKg, unit)}"
                    else -> ""
                }
                Text(
                    if (e.skipped) "Omitido" else target + weightLabel,
                    style = MaterialTheme.typography.bodyMedium, color = if (e.skipped) c.warning else c.textSecondary,
                )
                if (!e.skipped) {
                    Text(
                        "${item.completedSets}/${item.sets.size} series",
                        style = MaterialTheme.typography.labelMedium, color = if (item.completedSets > 0) c.primary else c.textSecondary,
                    )
                }
            }
            IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${e.exerciseName}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Añadir serie") }, onClick = { menu = false; actions.addSet(e.id) })
                if (tracking == TrackingType.DURATION) {
                    DropdownMenuItem(text = { Text("Modo intervalos (Tabata)") }, onClick = { menu = false; actions.startInterval(e.id) })
                }
                DropdownMenuItem(text = { Text(if (showNotes) "Ocultar notas" else "Notas del ejercicio") }, onClick = { menu = false; showNotes = !showNotes })
                DropdownMenuItem(
                    text = { Text(if (e.discomfort) "Quitar aviso de dolor/técnica" else "Marcar dolor o técnica deteriorada") },
                    onClick = { menu = false; actions.setDiscomfort(e.id, !e.discomfort) },
                )
                DropdownMenuItem(
                    text = { Text(if (e.skipped) "Retomar ejercicio" else "Saltar ejercicio") },
                    onClick = { menu = false; actions.setSkipped(e.id, !e.skipped) },
                )
                DropdownMenuItem(text = { Text("Quitar de la sesión", color = c.error) }, onClick = { menu = false; actions.removeExercise(e.id) })
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Contraer" else "Expandir",
                tint = c.textSecondary, modifier = Modifier.padding(end = 12.dp),
            )
        }

        AnimatedVisibility(visible = expanded && !e.skipped) {
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // ---- Sugerencia de progresión (editable, nunca automática)
                val kind = e.suggestionKind?.let { enumOr(it, SuggestionKind.HOLD) }
                if (!hasHistory && kind == SuggestionKind.FIRST_TIME) {
                    InfoChip("Primera vez · introduce una carga inicial", c.primary)
                } else if (kind != null && !e.suggestionDismissed && e.suggestionReason != null && kind != SuggestionKind.FIRST_TIME) {
                    SuggestionBanner(
                        reason = e.suggestionReason,
                        actionable = kind == SuggestionKind.INCREASE || kind == SuggestionKind.MORE_TIME,
                        onApply = { actions.applySuggestion(e.id) },
                        onDismiss = { actions.dismissSuggestion(e.id) },
                    )
                }
                if (e.discomfort) InfoChip("Marcado: dolor o técnica deteriorada. No se sugerirá subir carga.", c.warning)

                if (tracking == TrackingType.BODYWEIGHT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(0 to "Peso corporal", 1 to "+ Lastre", -1 to "− Asistencia").forEach { (m, label) ->
                            FilterChip(
                                selected = bwMode == m,
                                onClick = {
                                    bwMode = m
                                    actions.setBodyweightMode(item.sets, m)
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = c.surface, labelColor = c.text,
                                    selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
                                ),
                            )
                        }
                    }
                }

                SetTableHeader(tracking, bwMode, unit)
                item.sets.forEachIndexed { index, s ->
                    SetRow(
                        set = s, index = index, tracking = tracking, unit = unit, bwMode = bwMode,
                        repMin = e.repMin, repMax = e.repMax, targetDuration = e.targetDurationSec,
                        unilateral = e.unilateral, records = prs[s.id], actions = actions,
                    )
                }
                TextButton(onClick = { actions.addSet(e.id) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = c.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir serie", color = c.primary)
                }
                if (tracking == TrackingType.DURATION && item.sets.any { !it.completed }) {
                    SecondaryButton("Modo intervalos (Tabata)", { actions.startInterval(e.id) }, Modifier.fillMaxWidth(), icon = Icons.Default.Timer)
                }
                if (showNotes) {
                    var notes by remember(e.id) { mutableStateOf(e.notes) }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it; actions.setNotes(e.id, it) },
                        label = { Text("Notas del ejercicio") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.primary, unfocusedBorderColor = c.outline,
                            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, cursorColor = c.primary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaHost(show: Boolean, exerciseId: String, name: String, onDismiss: () -> Unit) {
    if (show) ExerciseMediaDialog(exerciseId, name, onDismiss)
}

@Composable
private fun InfoChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = color,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)).padding(10.dp),
    )
}

@Composable
private fun SuggestionBanner(reason: String, actionable: Boolean, onApply: () -> Unit, onDismiss: () -> Unit) {
    val c = Brand.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.08f))
            .border(1.dp, c.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("SUGERENCIA", style = MaterialTheme.typography.labelSmall, color = c.primary)
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (actionable) {
                TextButton(onClick = onApply, modifier = Modifier.heightIn(min = MinTouch)) { Text("Aplicar a series pendientes", color = c.primary) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(if (actionable) "Ignorar" else "Entendido", color = c.textSecondary) }
        }
    }
}

// Anchos relativos de columnas: SERIE | ANTERIOR | campos | ✓
private const val W_SET = 0.7f
private const val W_PREV = 1.35f
private const val W_FIELD = 1.0f
private val CheckWidth = 52.dp

@Composable
private fun SetTableHeader(tracking: TrackingType, bwMode: Int, unit: WeightUnit) {
    val c = Brand.colors
    val labels = fieldLabels(tracking, bwMode, unit)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("SERIE", W_SET)
        HeaderCell("ANTERIOR", W_PREV)
        labels.forEach { HeaderCell(it, W_FIELD) }
        Box(Modifier.width(CheckWidth), contentAlignment = Alignment.Center) {
            Text("✓", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
}

private fun fieldLabels(tracking: TrackingType, bwMode: Int, unit: WeightUnit): List<String> = when (tracking) {
    TrackingType.WEIGHT_REPS -> listOf("PESO (${unit.symbol})", "REPS")
    TrackingType.BODYWEIGHT -> if (bwMode == 0) listOf("REPS") else listOf(if (bwMode > 0) "+${unit.symbol}" else "−${unit.symbol}", "REPS")
    TrackingType.DURATION -> listOf("SEGUNDOS")
    TrackingType.DISTANCE -> listOf("KM", "MIN")
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = Brand.colors.textMuted,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp), textAlign = TextAlign.Center, maxLines = 1,
    )
}

@Composable
private fun SetRow(
    set: SetRecordEntity,
    index: Int,
    tracking: TrackingType,
    unit: WeightUnit,
    bwMode: Int,
    repMin: Int,
    repMax: Int,
    targetDuration: Int?,
    unilateral: Boolean,
    records: List<PersonalRecord>?,
    actions: ExerciseActions,
) {
    val c = Brand.colors
    var menu by remember { mutableStateOf(false) }
    var rpeDialog by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (set.completed) c.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent, label = "setBg")
    val side = enumOr(set.side, Side.BOTH)
    val label = "${set.setNumber}" + if (unilateral && side != Side.BOTH) " ${side.short}" else ""
    val weightText = set.weightKg?.let { Formatting.weight(abs(it), unit, false) }.orEmpty()
    val prevWeightText = set.prevWeightKg?.takeIf { it != 0.0 }?.let { Formatting.weight(abs(it), unit, false) } ?: "—"

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(W_SET).heightIn(min = MinTouch).clickable { menu = true }.semantics { contentDescription = "Serie $label, más opciones" }, contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = c.text)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index > 0) DropdownMenuItem(text = { Text("Repetir serie anterior") }, onClick = { menu = false; actions.repeatPrevious(set.id) })
                    DropdownMenuItem(
                        text = { Text(if (set.rpe != null) "Esfuerzo (RPE ${Formatting.number(set.rpe)})" else "Esfuerzo (RPE)") },
                        onClick = { menu = false; rpeDialog = true },
                    )
                    DropdownMenuItem(text = { Text("Eliminar serie", color = c.error) }, onClick = { menu = false; actions.removeSet(set.id) })
                }
            }
            Text(
                previousText(set, tracking, unit) ?: "—",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.weight(W_PREV).padding(horizontal = 2.dp), maxLines = 2,
            )
            when (tracking) {
                TrackingType.WEIGHT_REPS -> {
                    FieldBox(W_FIELD) {
                        NumberInput(weightText, prevWeightText, { actions.setWeight(set.id, it, 1) }, decimal = true, highlight = false, description = "Peso serie $label")
                    }
                    FieldBox(W_FIELD) {
                        NumberInput(set.reps?.toString().orEmpty(), repsRange(repMin, repMax), { actions.setReps(set.id, it) }, decimal = false, description = "Repeticiones serie $label")
                    }
                }
                TrackingType.BODYWEIGHT -> {
                    if (bwMode != 0) FieldBox(W_FIELD) {
                        NumberInput(weightText, "0", { actions.setWeight(set.id, it, if (bwMode < 0) -1 else 1) }, decimal = true, description = "Peso adicional serie $label")
                    }
                    FieldBox(W_FIELD) {
                        NumberInput(set.reps?.toString().orEmpty(), repsRange(repMin, repMax), { actions.setReps(set.id, it) }, decimal = false, description = "Repeticiones serie $label")
                    }
                }
                TrackingType.DURATION -> FieldBox(W_FIELD) {
                    NumberInput(set.durationSec?.toString().orEmpty(), (targetDuration ?: 30).toString(), { actions.setDuration(set.id, it) }, decimal = false, description = "Segundos serie $label")
                }
                TrackingType.DISTANCE -> {
                    FieldBox(W_FIELD) {
                        NumberInput(set.distanceM?.let { Formatting.number(it / 1000.0, 3) }.orEmpty(), "0", { actions.setDistance(set.id, it) }, decimal = true, description = "Kilómetros serie $label")
                    }
                    FieldBox(W_FIELD) {
                        NumberInput(set.durationSec?.let { Formatting.number(it / 60.0, 2) }.orEmpty(), "0", { actions.setMinutes(set.id, it) }, decimal = true, description = "Minutos serie $label")
                    }
                }
            }
            CompleteButton(set.completed, label) { actions.toggleComplete(set) }
        }
        val dist = set.distanceM
        val dur = set.durationSec
        if (tracking == TrackingType.DISTANCE && dist != null && dur != null && dist > 0 && dur > 0) {
            Text(
                "Ritmo ${Formatting.duration((dur / (dist / 1000.0)).toInt())} /km",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
        if (!records.isNullOrEmpty() && set.completed) {
            Row(Modifier.padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = c.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(recordText(records, unit), style = MaterialTheme.typography.bodySmall, color = c.primary)
            }
        }
    }
    if (rpeDialog) RpeDialog(current = set.rpe, onDismiss = { rpeDialog = false }, onPick = { actions.setRpe(set.id, it); rpeDialog = false })
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FieldBox(weight: Float, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight).padding(horizontal = 2.dp)) { content() }
}

@Composable
private fun CompleteButton(completed: Boolean, label: String, onClick: () -> Unit) {
    val c = Brand.colors
    val fill by animateColorAsState(if (completed) c.primary else androidx.compose.ui.graphics.Color.Transparent, label = "checkFill")
    val stroke by animateColorAsState(if (completed) c.primary else c.outline, label = "checkStroke")
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Al completar: un pulso breve (sube y vuelve con un muelle) con animateFloatAsState.
    // No se dispara al abrir una serie que ya estaba completada.
    var pulse by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(completed) }
    androidx.compose.runtime.LaunchedEffect(completed) {
        if (completed && !seen) {
            pulse = true
            kotlinx.coroutines.delay(110)
            pulse = false
        }
        seen = completed
    }
    val scale by animateFloatAsState(
        targetValue = if (pulse) 1.18f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "checkScale",
    )
    Box(Modifier.width(CheckWidth), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(MinTouch).graphicsLayer { scaleX = scale; scaleY = scale }.pressScale(source, 0.9f)
                .clip(CircleShape).background(fill)
                .border(1.5.dp, stroke, CircleShape)
                .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
                .semantics { contentDescription = if (completed) "Serie $label completada. Toca para deshacer" else "Completar serie $label" },
            contentAlignment = Alignment.Center,
        ) {
            // Inactivo: solo contorno; el check queda apenas insinuado para que el control siga siendo descubrible.
            Icon(
                Icons.Default.Check, contentDescription = null,
                tint = if (completed) c.onGradient else c.textMuted.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun RpeDialog(current: Double?, onDismiss: () -> Unit, onPick: (Double?) -> Unit) {
    val c = Brand.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Esfuerzo percibido (RPE)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("10 = no podrías hacer ni una repetición más. 8 = te quedaban unas 2 en reserva.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { v ->
                        FilterChip(
                            selected = current == v, onClick = { onPick(v) }, label = { Text(Formatting.number(v)) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = c.surface, labelColor = c.text,
                                selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("Sin dato", color = c.textSecondary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.text) } },
    )
}
