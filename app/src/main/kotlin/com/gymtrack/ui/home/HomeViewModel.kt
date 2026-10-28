package com.gymtrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.RoutineInfo
import com.gymtrack.domain.RoutineRecommendation
import com.gymtrack.domain.RoutineRecommender
import com.gymtrack.domain.Stats
import com.gymtrack.domain.SuggestionKind
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.history.HistoryItem
import com.gymtrack.ui.history.buildHistoryItems
import com.gymtrack.ui.routines.RoutineListItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class NextGoal(val exerciseName: String, val routineName: String, val text: String)

data class TodayExercise(val id: String, val name: String)

data class HomeState(
    val loading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val active: SessionEntity? = null,
    val routines: List<RoutineListItem> = emptyList(),
    val recommendation: RoutineRecommendation? = null,
    val last: HistoryItem? = null,
    val recent: List<HistoryItem> = emptyList(),
    val totalSessions: Int = 0,
    val totalTimeSec: Long = 0,
    val sessionsThisWeek: Int = 0,
    val recentMuscles: List<Pair<MuscleGroup, Int>> = emptyList(),
    /** Variación porcentual del volumen respecto a la sesión anterior de la misma rutina. */
    val volumeDeltaPct: Double? = null,
    val nextGoal: NextGoal? = null,
    /** Ejercicios de la rutina recomendada (para las miniaturas) y sus totales. */
    val todayExercises: List<TodayExercise> = emptyList(),
    val todaySets: Int = 0,
    /** Estimación orientativa: series × (trabajo + descanso). */
    val todayMinutes: Int = 0,
    /** Panel semanal: días entrenados (lun–dom), racha de semanas, series y minutos de la semana en curso. */
    val weekDays: Set<java.time.DayOfWeek> = emptySet(),
    val streakWeeks: Int = 0,
    val setsThisWeek: Int = 0,
    val minutesThisWeek: Int = 0,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        c.routines.routines, c.routines.counts, c.history.sessions, c.history.exerciseCounts, c.history.allRows,
    ) { routines, counts, sessions, exCounts, rows ->
        Snapshot(routines, counts, sessions, exCounts, rows)
    }.let { snap ->
        combine(snap, c.workout.activeSession, c.settings.settings) { s, active, settings -> build(s, active, settings) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private class Snapshot(
        val routines: List<com.gymtrack.data.db.RoutineEntity>,
        val counts: List<com.gymtrack.data.db.RoutineCounts>,
        val sessions: List<SessionEntity>,
        val exCounts: Map<String, Int>,
        val rows: List<com.gymtrack.data.db.HistorySetRow>,
    )

    private suspend fun build(s: Snapshot, active: SessionEntity?, settings: UserSettings): HomeState {
        val zone = Dates.zone
        val now = System.currentTimeMillis()
        val countById = s.counts.associateBy { it.routineId }
        val routineItems = s.routines.map { RoutineListItem(it, countById[it.id]?.exerciseCount ?: 0, countById[it.id]?.setCount ?: 0) }
        val lastUsed = s.sessions.filter { it.routineId != null }.groupBy { it.routineId!! }.mapValues { (_, l) -> l.maxOf { it.startedAt } }
        val recommendation = RoutineRecommender.recommend(
            s.routines.filter { (countById[it.id]?.exerciseCount ?: 0) > 0 }
                .map { RoutineInfo(it.id, it.name.ifBlank { "Rutina" }, it.daysMask, lastUsed[it.id]) },
            LocalDate.now().dayOfWeek,
        )
        val items = buildHistoryItems(s.sessions, s.exCounts, s.rows)
        val last = items.firstOrNull()
        val delta = last?.let { l ->
            val rid = l.session.routineId ?: return@let null
            val prev = items.firstOrNull { it.session.routineId == rid && it.session.startedAt < l.session.startedAt } ?: return@let null
            if (prev.totals.volumeKg > 0 && l.totals.volumeKg > 0) (l.totals.volumeKg - prev.totals.volumeKg) / prev.totals.volumeKg * 100 else null
        }
        val from = now - 14 * 86_400_000L
        val muscles = s.rows.filter { it.setType != "WARMUP" && it.performedAt >= from }
            .groupingBy { enumOr(it.primaryMuscle, MuscleGroup.FULL_BODY) }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        // "Tu próximo objetivo": solo con historial real del ejercicio.
        val plan = recommendation?.let { c.workout.previewRoutine(it.routine.id) }.orEmpty()
        val goal = recommendation?.let { rec ->
            val pick = plan.firstOrNull { it.suggestion.kind == SuggestionKind.INCREASE }
                ?: plan.firstOrNull { it.suggestion.kind != SuggestionKind.FIRST_TIME }
            pick?.let { NextGoal(it.spec.exercise.name, rec.routine.name, it.suggestion.reason) }
        }
        val starts = s.sessions.map { it.startedAt }
        val thisWeek = Stats.weekStart(now, zone)
        val setsThisWeek = s.rows.count { it.setType != "WARMUP" && Stats.weekStart(it.performedAt, zone) == thisWeek }
        val secThisWeek = s.sessions.filter { Stats.weekStart(it.startedAt, zone) == thisWeek }
            .sumOf { (((it.endedAt ?: it.startedAt) - it.startedAt) / 1000).coerceAtLeast(0) }
        val todaySets = plan.sumOf { it.spec.sets }
        val todaySeconds = plan.sumOf { p -> p.spec.sets * ((p.spec.targetDurationSec ?: 40) + p.spec.restSec) }
        return HomeState(
            loading = false, settings = settings, active = active, routines = routineItems, recommendation = recommendation,
            last = last, recent = items.take(3), totalSessions = items.size,
            totalTimeSec = s.sessions.sumOf { (((it.endedAt ?: it.startedAt) - it.startedAt) / 1000).coerceAtLeast(0) },
            sessionsThisWeek = Stats.sessionsInWeekOf(now, s.sessions.map { it.startedAt }, zone),
            recentMuscles = muscles, volumeDeltaPct = delta, nextGoal = goal,
            todayExercises = plan.map { TodayExercise(it.spec.exercise.id, it.spec.exercise.name) },
            todaySets = todaySets, todayMinutes = (todaySeconds + 59) / 60,
            weekDays = Stats.trainedDaysInWeekOf(now, starts, zone), streakWeeks = Stats.weekStreak(starts, now, zone),
            setsThisWeek = setsThisWeek, minutesThisWeek = (secThisWeek / 60).toInt(),
        )
    }

    fun start(routineId: String?, onStarted: () -> Unit) {
        viewModelScope.launch {
            c.workout.startSession(routineId)
            onStarted()
        }
    }
}
