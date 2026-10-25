package com.gymtrack.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.R
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.RoutineReason
import com.gymtrack.ui.components.BigButton
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.ExerciseThumbOrInitial
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.components.StatTile
import com.gymtrack.ui.history.HistoryItem
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.workout.StartWorkoutDialog
import java.time.LocalTime

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

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineLarge)
                    Text(Dates.dayLong(System.currentTimeMillis()), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                }
            }
        }

        // ---- ENTRENAR / continuar
        item {
            GymCard(Modifier.fillMaxWidth(), highlighted = true) {
                val active = st.active
                if (active != null) {
                    Text("ENTRENAMIENTO EN CURSO", style = MaterialTheme.typography.labelSmall, color = c.primary)
                    Text(active.routineName, style = MaterialTheme.typography.titleLarge)
                    Text("Empezó a las ${Dates.time(active.startedAt)}. Tus series registradas están guardadas.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    BigButton("CONTINUAR ENTRENAMIENTO", onOpenWorkout, Modifier.padding(top = 12.dp), icon = Icons.Default.PlayArrow)
                } else {
                    val rec = st.recommendation
                    if (rec != null) {
                        Text(
                            when (rec.reason) {
                                RoutineReason.SCHEDULED_TODAY -> "PROGRAMADA PARA HOY"
                                RoutineReason.LEAST_RECENT -> "RECOMENDADA · LA QUE MÁS TIEMPO LLEVAS SIN HACER"
                                RoutineReason.NO_HISTORY -> "TU PRIMERA RUTINA"
                            },
                            style = MaterialTheme.typography.labelSmall, color = c.primary,
                        )
                        Text(rec.routine.name, style = MaterialTheme.typography.headlineMedium)
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
                            Row(
                                Modifier.padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                st.todayExercises.take(4).forEach { ExerciseThumbOrInitial(it.id, it.name, 64.dp) }
                                val extra = st.todayExercises.size - 4
                                if (extra > 0) Text("+$extra", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
                            }
                        }
                    } else {
                        Text("EMPIEZA AQUÍ", style = MaterialTheme.typography.labelSmall, color = c.primary)
                        Text("Aún no tienes rutinas con ejercicios", style = MaterialTheme.typography.titleLarge)
                        Text("Crea una rutina o empieza un entrenamiento libre.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    BigButton("ENTRENAR", { chooser = true }, Modifier.padding(top = 12.dp), icon = Icons.Default.PlayArrow)
                }
            }
        }

        // ---- Resumen
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Esta semana", "${st.sessionsThisWeek}", Modifier.weight(1f), hint = if (st.sessionsThisWeek == 1) "sesión" else "sesiones")
                StatTile("Tiempo total", com.gymtrack.ui.components.Dates.shortDuration(st.totalTimeSec), Modifier.weight(1f))
                StatTile("Sesiones", "${st.totalSessions}", Modifier.weight(1f), hint = "en total")
            }
        }

        // ---- Próximo objetivo
        item {
            GymCard(Modifier.fillMaxWidth()) {
                SectionTitle("Tu próximo objetivo")
                val goal = st.nextGoal
                if (goal != null) {
                    Text(goal.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(goal.text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    Text("Es solo una sugerencia basada en tu última sesión: puedes ignorarla o ajustarla.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(
                        "Todavía no hay datos suficientes. Registra algún entrenamiento y aquí verás una meta concreta de progresión para tu próxima sesión.",
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
                    Text(l.session.routineName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    Text("${Dates.relative(l.session.startedAt)} · ${l.totals.completedSets} series · ${l.exerciseCount} ejercicios", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    st.volumeDeltaPct?.let { d ->
                        val up = d >= 0
                        Text(
                            "${if (up) "▲" else "▼"} ${Formatting.number(kotlin.math.abs(d), 1)} % de volumen respecto a la sesión anterior de esta rutina",
                            style = MaterialTheme.typography.bodyMedium, color = if (up) c.primary else c.warning, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // ---- Músculos recientes
        if (st.recentMuscles.isNotEmpty()) {
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Músculos entrenados · últimos 14 días")
                    val max = st.recentMuscles.maxOf { it.second }.coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        st.recentMuscles.forEach { (m, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("$n series", style = MaterialTheme.typography.bodyMedium, color = if (n == max) c.primary else c.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        // ---- Últimos entrenamientos
        if (st.recent.isNotEmpty()) {
            item { SectionTitle("Últimos entrenamientos") }
            items3(st.recent, unit, onOpenSession)
        }
        item { Text(stringResourceCompat(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = 8.dp)) }
    }

    if (chooser) {
        StartWorkoutDialog(
            routines = st.routines, recommendedId = st.recommendation?.routine?.id,
            onPick = { id -> chooser = false; vm.start(id, onOpenWorkout) },
            onDismiss = { chooser = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items3(
    list: List<HistoryItem>,
    unit: com.gymtrack.domain.WeightUnit,
    onOpen: (String) -> Unit,
) {
    list.forEach { item ->
        item(key = "recent-" + item.session.id) {
            val c = Brand.colors
            GymCard(Modifier.fillMaxWidth(), onClick = { onOpen(item.session.id) }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.session.routineName, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
