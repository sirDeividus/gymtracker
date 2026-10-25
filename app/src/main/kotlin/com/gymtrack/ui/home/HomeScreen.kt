package com.gymtrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.R
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.RoutineReason
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.ExerciseThumbOrInitial
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.MuscleMap
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.components.pressScale
import com.gymtrack.ui.history.HistoryItem
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.theme.DeepBlack
import com.gymtrack.ui.theme.TextMuted
import com.gymtrack.ui.theme.ExerciseTitleStyle
import com.gymtrack.ui.workout.StartWorkoutDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val DayInitials = listOf("L", "M", "X", "J", "V", "S", "D")

/**
 * Dashboard: cuadrícula Bento (esquinas 16/24 dp, espaciado múltiplo de 4/8), rutina del día con miniaturas
 * y un botón flotante de acción principal. El acento se reserva para la acción principal, la racha y los temporizadores.
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenWorkout: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoutines: () -> Unit,
) {
    val c = Brand.colors
    val st by vm.state.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 6 -> "Buenas noches"
        hour < 13 -> "Buenos días"
        hour < 21 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val unit = st.settings.unit
    val today = LocalDate.now().dayOfWeek

    Box(Modifier.fillMaxSize().background(DeepBlack).padding(contentPadding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            // Espacio inferior para que el botón flotante no tape el contenido.
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting.uppercase(), style = MaterialTheme.typography.headlineLarge)
                        Text(Dates.dayLong(System.currentTimeMillis()), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            }

            // ---- Bento 1: semana (grande) + racha y series
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GymCard(Modifier.weight(1.4f).fillMaxHeight(), contentPadding = PaddingValues(16.dp)) {
                        Label("Esta semana")
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${st.sessionsThisWeek}", fontSize = 48.sp, fontWeight = FontWeight.Black, lineHeight = 52.sp)
                            Text(
                                if (st.sessionsThisWeek == 1) " sesión" else " sesiones",
                                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        WeekDots(st.weekDays, today)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BentoTile("Racha", "${st.streakWeeks}", Modifier.weight(1f), hint = if (st.streakWeeks == 1) "semana" else "semanas", accent = st.streakWeeks > 0)
                        BentoTile("Series", "${st.setsThisWeek}", Modifier.weight(1f), hint = "esta semana")
                    }
                }
            }

            // ---- Rutina del día / sesión en curso (hero)
            item {
                GymCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    val active = st.active
                    val rec = st.recommendation
                    if (active != null) {
                        Label("Entrenamiento en curso")
                        Text(active.routineName.uppercase(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                        Text("Empezó a las ${Dates.time(active.startedAt)}. Tus series están guardadas.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    } else if (rec != null) {
                        Label(
                            when (rec.reason) {
                                RoutineReason.SCHEDULED_TODAY -> "Rutina de hoy"
                                RoutineReason.LEAST_RECENT -> "Recomendada · la que más tiempo llevas sin hacer"
                                RoutineReason.NO_HISTORY -> "Tu primera rutina"
                            },
                        )
                        Text(rec.routine.name.uppercase(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                        if (st.todayExercises.isNotEmpty()) {
                            Text(
                                "${st.todayExercises.size} ejercicios · ${st.todaySets} series · ≈ ${st.todayMinutes} min",
                                style = MaterialTheme.typography.bodyLarge, color = c.textSecondary,
                            )
                        }
                        rec.routine.lastUsedAt?.let {
                            Text("Última vez: ${Dates.relative(it)}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                        if (st.todayExercises.isNotEmpty()) {
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                st.todayExercises.take(4).forEach { ExerciseThumbOrInitial(it.id, it.name, 64.dp) }
                                val extra = st.todayExercises.size - 4
                                if (extra > 0) Text("+$extra", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
                            }
                        }
                    } else {
                        Label("Empieza aquí")
                        Text("AÚN NO TIENES RUTINAS", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                        Text("Crea una rutina en la pestaña Rutinas o empieza un entrenamiento libre.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
            }

            // ---- Bento 2: tiempo y sesiones
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BentoTile("Tiempo semana", Dates.shortDuration(st.minutesThisWeek * 60L), Modifier.weight(1f).fillMaxHeight())
                    BentoTile("Tiempo total", Dates.shortDuration(st.totalTimeSec), Modifier.weight(1f).fillMaxHeight(), hint = "${st.totalSessions} sesiones")
                }
            }

            // ---- Próximo objetivo
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Tu próximo objetivo")
                    val goal = st.nextGoal
                    if (goal != null) {
                        Text(goal.exerciseName.uppercase(), style = ExerciseTitleStyle, modifier = Modifier.padding(top = 4.dp))
                        Text(goal.text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        Text("Solo una sugerencia basada en tu última sesión: puedes ignorarla o ajustarla.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text(
                            "Todavía no hay datos suficientes. Registra algún entrenamiento y aquí verás una meta concreta para tu próxima sesión.",
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // ---- Último entrenamiento
            item {
                GymCard(Modifier.fillMaxWidth(), onClick = st.last?.let { l -> { onOpenSession(l.session.id) } }) {
                    SectionTitle("Último entrenamiento")
                    val l = st.last
                    if (l == null) {
                        Text("Aún no has finalizado ningún entrenamiento.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text(l.session.routineName.uppercase(), style = ExerciseTitleStyle, modifier = Modifier.padding(top = 4.dp))
                        Text("${Dates.relative(l.session.startedAt)} · ${l.totals.completedSets} series · ${l.exerciseCount} ejercicios", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        st.volumeDeltaPct?.let { d ->
                            val up = d >= 0
                            Text(
                                "${if (up) "▲" else "▼"} ${Formatting.number(kotlin.math.abs(d), 1)} % de volumen respecto a la sesión anterior de esta rutina",
                                style = MaterialTheme.typography.bodyMedium, color = if (up) c.text else c.warning, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            // ---- Mapa de músculos recientes
            if (st.recentMuscles.isNotEmpty()) {
                item {
                    GymCard(Modifier.fillMaxWidth()) {
                        SectionTitle("Músculos entrenados · últimos 14 días")
                        val max = st.recentMuscles.maxOf { it.second }.coerceAtLeast(1)
                        MuscleMap(st.recentMuscles.toMap(), Modifier.padding(top = 10.dp, bottom = 6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            st.recentMuscles.take(5).forEach { (m, n) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("$n series", style = MaterialTheme.typography.bodyMedium, color = if (n == max) c.text else c.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            if (st.recent.isNotEmpty()) {
                item { SectionTitle("Últimos entrenamientos") }
                recentItems(st.recent, unit, onOpenSession)
            }
            item {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        // ---- Botón flotante de acción principal
        StartFab(
            label = if (st.active != null) "CONTINUAR ENTRENAMIENTO" else "INICIAR ENTRENAMIENTO",
            onClick = { if (st.active != null) onOpenWorkout() else chooser = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = ScreenPadding, vertical = 12.dp),
        )
    }

    if (chooser) {
        StartWorkoutDialog(
            routines = st.routines, recommendedId = st.recommendation?.routine?.id,
            onPick = { id -> chooser = false; vm.start(id, onOpenWorkout) },
            onDismiss = { chooser = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextMuted)
}

/** Tarjeta pequeña de la cuadrícula. El acento solo se usa para la racha. */
@Composable
private fun BentoTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, accent: Boolean = false) {
    val c = Brand.colors
    GymCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Label(label)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = if (accent) c.primary else c.text, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

/** Siete puntos (L–D): relleno = día entrenado; aro = hoy. */
@Composable
private fun WeekDots(trained: Set<DayOfWeek>, today: DayOfWeek) {
    val c = Brand.colors
    val summary = if (trained.isEmpty()) "ningún día entrenado todavía" else trained.sortedBy { it.value }.joinToString { DayInitials[it.value - 1] }
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Días entrenados esta semana: $summary" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DayOfWeek.values().forEach { day ->
            val did = day in trained
            val isToday = day == today
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (did) c.text else c.outline)
                        .let { if (isToday) it.border(2.dp, c.primary, CircleShape) else it },
                )
                Text(DayInitials[day.value - 1], style = MaterialTheme.typography.labelSmall, color = if (isToday) c.text else c.textSecondary)
            }
        }
    }
}

@Composable
private fun StartFab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 4.dp),
        modifier = modifier.pressScale(source).fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

private fun LazyListScope.recentItems(
    list: List<HistoryItem>,
    unit: com.gymtrack.domain.WeightUnit,
    onOpen: (String) -> Unit,
) {
    list.forEach { item ->
        item(key = "recent-" + item.session.id) {
            val c = Brand.colors
            GymCard(Modifier.fillMaxWidth(), onClick = { onOpen(item.session.id) }, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.session.routineName.uppercase(), style = ExerciseTitleStyle)
                        Text(Dates.day(item.session.startedAt), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${item.totals.completedSets} series", style = MaterialTheme.typography.bodyMedium)
                        if (item.totals.volumeKg > 0) Text(Formatting.weight(item.totals.volumeKg, unit), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}
