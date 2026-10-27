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
import androidx.compose.material.icons.filled.NoteAdd
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
import com.gymtrack.domain.Equipment
import com.gymtrack.domain.PersonalRecord
import com.gymtrack.domain.PlateCalculator
import com.gymtrack.domain.RecordType
import com.gymtrack.domain.SetType
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
    val setType: (String, SetType) -> Unit,
    val copyPrevious: (String) -> Unit,
    val addWarmups: (String) -> Unit,
    val setRest: (String, Int) -> Unit,
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
    var restMenu by remember { mutableStateOf(false) }
    // Los ejercicios por tiempo (Tabata, plancha…) se muestran como un solo botón; las series se pueden desplegar.
    val compact = tracking == TrackingType.DURATION
    var showTable by remember(e.id) { mutableStateOf(!compact) }
    // Bloque plano (sin tarjeta): miniatura circular, nombre en el acento, chips de descanso/nota y la tabla.
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            com.gymtrack.ui.components.ExerciseImageFill(
                e.exerciseId, e.exerciseName,
                Modifier.size(52.dp).clickable { showMedia = true }.semantics { contentDescription = "Ver demostración de ${e.exerciseName}" },
                shape = CircleShape, animated = true,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.exerciseName, style = MaterialTheme.typography.titleLarge,
                    color = if (e.skipped) c.textSecondary else if (done) c.textSecondary else c.primary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (e.skipped) Text("Omitido", style = MaterialTheme.typography.bodySmall, color = c.warning)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${e.exerciseName}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Añadir serie") }, onClick = { menu = false; actions.addSet(e.id) })
                    if (tracking == TrackingType.DURATION) {
                        DropdownMenuItem(text = { Text("Modo intervalos (Tabata)") }, onClick = { menu = false; actions.startInterval(e.id) })
                    }
                    if (tracking == TrackingType.WEIGHT_REPS) {
                        DropdownMenuItem(text = { Text("Añadir calentamiento") }, onClick = { menu = false; actions.addWarmups(e.id) })
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
            }
        }
        if (!e.skipped && !compact) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                PillChip(if (e.restSec > 0) "Descanso: ${Formatting.duration(e.restSec)}" else "Descanso: --", Icons.Default.Timer) { restMenu = true }
                DropdownMenu(expanded = restMenu, onDismissRequest = { restMenu = false }) {
                    listOf(0, 30, 45, 60, 90, 120, 180, 240, 300).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text(if (sec == 0) "Sin descanso" else Formatting.duration(sec)) },
                            onClick = { restMenu = false; actions.setRest(e.id, sec) },
                        )
                    }
                }
            }
            PillChip(if (showNotes) "Ocultar nota" else "Nota", Icons.Default.NoteAdd) { showNotes = !showNotes }
        }
        if (!e.skipped && compact) {
            // Un solo botón: abre el temporizador; se marca ✓ y se pone verde cuando todas las series están hechas.
            val work = e.targetDurationSec ?: item.sets.firstOrNull()?.durationSec ?: 20
            val cycles = com.gymtrack.data.settings.IntervalPrefs.get(androidx.compose.ui.platform.LocalContext.current, e.exerciseId).cycles
            val summary = (if (cycles > 1) "$cycles ciclos × " else "") + "${item.sets.size} × $work s" + if (e.restSec > 0) " · descanso ${e.restSec} s" else ""
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (done) SetDone else c.primary).clickable { actions.startInterval(e.id) }
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Abrir el temporizador de ${e.exerciseName}" + if (done) ", completado" else "" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (done) "Completado" else "Iniciar temporizador", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (done) 1f else 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = if (done) SetDone else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
                }
            }
            TextButton(onClick = { showTable = !showTable }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(if (showTable) "Ocultar series" else "Ver series (${item.completedSets}/${item.sets.size})", color = c.textSecondary)
            }
        }

        AnimatedVisibility(visible = !e.skipped && showTable) {
            Column(Modifier.padding(top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                // Los calentamientos (W) y las series D/F no consumen número: 1, 2, 3 son las series normales.
                val normalNumbers = item.sets.filter { enumOr(it.setType, SetType.NORMAL) == SetType.NORMAL }.map { it.setNumber }.distinct().sorted()
                val isBarbell = enumOr(e.equipment, Equipment.OTHER) == Equipment.BARBELL
                item.sets.forEachIndexed { index, s ->
                    SetRow(
                        set = s, index = index, tracking = tracking, unit = unit, bwMode = bwMode,
                        repMin = e.repMin, repMax = e.repMax, targetDuration = e.targetDurationSec,
                        unilateral = e.unilateral, records = prs[s.id], actions = actions,
                        displayNumber = (normalNumbers.indexOf(s.setNumber).takeIf { it >= 0 }?.plus(1) ?: s.setNumber).toString(),
                        isBarbell = isBarbell,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh)
                        .clickable { actions.addSet(e.id) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar Serie", style = MaterialTheme.typography.titleSmall)
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
private fun PillChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
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

/** Verde de «serie hecha». */
private val SetDone = androidx.compose.ui.graphics.Color(0xFF34C759)

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
    TrackingType.WEIGHT_REPS -> listOf(unit.symbol.uppercase(), "REPS")
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
    displayNumber: String,
    isBarbell: Boolean,
) {
    val c = Brand.colors
    var menu by remember { mutableStateOf(false) }
    var rpeDialog by remember { mutableStateOf(false) }
    var typeDialog by remember { mutableStateOf(false) }
    var plateDialog by remember { mutableStateOf(false) }
    // Fila completada: tono verde (como Hevy) para que se vea de un vistazo qué series ya están hechas.
    val bg by animateColorAsState(if (set.completed) SetDone.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent, label = "setBg")
    val side = enumOr(set.side, Side.BOTH)
    val type = enumOr(set.setType, SetType.NORMAL)
    val numberOrType = if (type == SetType.NORMAL) displayNumber else type.short
    val label = numberOrType + if (unilateral && side != Side.BOTH) " ${side.short}" else ""
    val labelColor = when (type) {
        SetType.WARMUP -> c.warning
        SetType.FAILURE -> c.primary
        else -> c.text
    }
    val hasPrevious = set.prevWeightKg != null || set.prevReps != null || set.prevDurationSec != null || set.prevDistanceM != null
    val weightText = set.weightKg?.let { Formatting.weight(abs(it), unit, false) }.orEmpty()
    val prevWeightText = set.prevWeightKg?.takeIf { it != 0.0 }?.let { Formatting.weight(abs(it), unit, false) } ?: "—"

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(W_SET).heightIn(min = MinTouch).clickable { menu = true }.semantics { contentDescription = "Serie $label, más opciones" }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (set.completed) c.background.copy(alpha = 0.35f) else c.surfaceHigh), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = labelColor)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Tipo de serie: ${type.label}") }, onClick = { menu = false; typeDialog = true })
                    if (isBarbell && tracking == TrackingType.WEIGHT_REPS) {
                        DropdownMenuItem(text = { Text("Calculadora de discos") }, onClick = { menu = false; plateDialog = true })
                    }
                    if (index > 0) DropdownMenuItem(text = { Text("Repetir serie anterior") }, onClick = { menu = false; actions.repeatPrevious(set.id) })
                    DropdownMenuItem(
                        text = { Text(if (set.rpe != null) "Esfuerzo (RPE ${Formatting.number(set.rpe)})" else "Esfuerzo (RPE)") },
                        onClick = { menu = false; rpeDialog = true },
                    )
                    DropdownMenuItem(text = { Text("Eliminar serie", color = c.error) }, onClick = { menu = false; actions.removeSet(set.id) })
                }
            }
            Box(
                Modifier.weight(W_PREV).heightIn(min = MinTouch).clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = hasPrevious) { actions.copyPrevious(set.id) }
                    .semantics { if (hasPrevious) contentDescription = "Copiar valores de la sesión anterior: ${previousText(set, tracking, unit)}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    previousText(set, tracking, unit) ?: "—",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp), maxLines = 2,
                )
            }
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
    if (typeDialog) SetTypeDialog(current = type, onDismiss = { typeDialog = false }, onPick = { actions.setType(set.id, it); typeDialog = false })
    if (plateDialog) PlateDialog(weightKg = set.weightKg, unit = unit, onDismiss = { plateDialog = false })
}

@Composable
private fun SetTypeDialog(current: SetType, onDismiss: () -> Unit, onPick: (SetType) -> Unit) {
    val c = Brand.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Tipo de serie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Los calentamientos no cuentan para volumen, récords ni sugerencias de progresión.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                SetType.values().forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (t == current) c.text.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(t) }.padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = MinTouch),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t.short.ifEmpty { "·" }, style = MaterialTheme.typography.titleMedium,
                            color = when (t) { SetType.WARMUP -> c.warning; SetType.FAILURE -> c.primary; else -> c.text },
                            modifier = Modifier.width(28.dp),
                        )
                        Text(t.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.text) } },
    )
}

@Composable
private fun PlateDialog(weightKg: Double?, unit: WeightUnit, onDismiss: () -> Unit) {
    val c = Brand.colors
    val set = PlateCalculator.plateSetFor(unit)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Calculadora de discos") },
        text = {
            if (weightKg == null || weightKg <= 0.0) {
                Text("Escribe primero el peso de la serie.", color = c.textSecondary)
            } else {
                val target = unit.fromKg(weightKg)
                val plan = PlateCalculator.plan(target, set)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Objetivo: ${Formatting.number(target, 2)} ${unit.symbol} · barra ${Formatting.number(set.bar)} ${unit.symbol}", style = MaterialTheme.typography.bodyMedium)
                    Text("POR LADO", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    if (plan.perSide.isEmpty()) {
                        Text("Solo la barra", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            plan.perSide.forEach { (plate, n) ->
                                Text(
                                    "${Formatting.number(plate, 2)} × $n", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.border(1.dp, c.outline, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    if (plan.remainder > 0) {
                        Text(
                            "Faltan ${Formatting.number(plan.remainder, 2)} ${unit.symbol} para el objetivo con estos discos.",
                            style = MaterialTheme.typography.bodySmall, color = c.warning,
                        )
                    }
                    Text("Juego de discos estándar; ajústalo a lo que tengas en tu gimnasio.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.primary) } },
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FieldBox(weight: Float, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight).padding(horizontal = 2.dp)) { content() }
}

@Composable
private fun CompleteButton(completed: Boolean, label: String, onClick: () -> Unit) {
    val c = Brand.colors
    val fill by animateColorAsState(if (completed) SetDone else c.surfaceHigh, label = "checkFill")
    val stroke by animateColorAsState(if (completed) SetDone else androidx.compose.ui.graphics.Color.Transparent, label = "checkStroke")
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
                .clip(RoundedCornerShape(10.dp)).background(fill)
                .border(1.5.dp, stroke, RoundedCornerShape(10.dp))
                .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
                .semantics { contentDescription = if (completed) "Serie $label completada. Toca para deshacer" else "Completar serie $label" },
            contentAlignment = Alignment.Center,
        ) {
            // Inactivo: solo contorno; el check queda apenas insinuado para que el control siga siendo descubrible.
            Icon(
                Icons.Default.Check, contentDescription = null,
                tint = if (completed) androidx.compose.ui.graphics.Color.White else c.textMuted,
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
