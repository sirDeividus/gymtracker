package com.gymtrack.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.db.HistorySetRow
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.repo.groupPerformances
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.ExerciseBests
import com.gymtrack.domain.ExercisePerformance
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.Stats
import com.gymtrack.domain.TrackingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class RecordRow(
    val exerciseId: String,
    val name: String,
    val tracking: TrackingType,
    val maxWeightKg: Double?,
    val e1rmKg: Double?,
    val maxReps: Int?,
    val maxDurationSec: Int?,
    val lastAt: Long,
)

data class ProgressState(
    val loading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val period: Period = Period.M3,
    val exercises: List<Pair<String, String>> = emptyList(),
    val selectedId: String? = null,
    val selectedPerformances: List<ExercisePerformance> = emptyList(),
    val selectedTracking: TrackingType = TrackingType.WEIGHT_REPS,
    val weeklySessions: List<Pair<LocalDate, Int>> = emptyList(),
    val weeklyVolumeKg: List<Pair<LocalDate, Double>> = emptyList(),
    val setsPerMuscle: List<Pair<MuscleGroup, Int>> = emptyList(),
    val records: List<RecordRow> = emptyList(),
    val sessionCount: Int = 0,
) {
    val hasData: Boolean get() = exercises.isNotEmpty()
}

class ProgressViewModel(c: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val period = MutableStateFlow(Period.M3)

    val state: StateFlow<ProgressState> = combine(c.history.allRows, c.settings.settings, selected, period) { rows, settings, sel, per ->
        compute(rows, settings, sel, per)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun select(id: String) { selected.value = id }
    fun setPeriod(p: Period) = period.update { p }

    private fun compute(rows: List<HistorySetRow>, settings: UserSettings, sel: String?, per: Period): ProgressState {
        val zone = com.gymtrack.ui.components.Dates.zone
        val now = System.currentTimeMillis()
        val perfs = groupPerformances(rows)
        val byExercise = perfs.groupBy { it.exerciseId }
        val names = rows.associate { it.exerciseId to it.exerciseName }
        val ordered = byExercise.entries.sortedByDescending { e -> e.value.maxOf { it.performedAt } }.map { it.key to (names[it.key] ?: it.key) }
        val chosen = sel?.takeIf { it in byExercise } ?: ordered.firstOrNull()?.first
        val chosenPerfs = chosen?.let { byExercise[it] }.orEmpty()

        // Últimas 8 semanas, incluyendo semanas sin entrenar.
        val thisWeek = Stats.weekStart(now, zone)
        val weeks = (7 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
        val sessionStarts = rows.associate { it.sessionId to it.performedAt }.values.toList()
        val perWeek = Stats.sessionsPerWeek(sessionStarts, zone)
        val volumeByWeek = HashMap<LocalDate, Double>()
        for (r in rows) {
            if (enumOr(r.tracking, TrackingType.WEIGHT_REPS) != TrackingType.WEIGHT_REPS) continue
            val w = Stats.weekStart(r.performedAt, zone)
            volumeByWeek[w] = (volumeByWeek[w] ?: 0.0) + (r.weightKg ?: 0.0) * (r.reps ?: 0)
        }

        val from = per.days?.let { now - it * 86_400_000L }
        val muscleCounts = rows.filter { from == null || it.performedAt >= from }
            .groupingBy { enumOr(it.primaryMuscle, MuscleGroup.FULL_BODY) }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        val records = byExercise.map { (id, list) ->
            val tr = list.first().tracking
            val b = ExerciseBests.from(list, tr)
            RecordRow(id, names[id] ?: id, tr, b.maxWeight(), b.maxE1rm(), b.maxReps(), b.maxDuration(), list.maxOf { it.performedAt })
        }.sortedByDescending { it.lastAt }

        return ProgressState(
            loading = false, settings = settings, period = per, exercises = ordered, selectedId = chosen,
            selectedPerformances = chosenPerfs, selectedTracking = chosenPerfs.firstOrNull()?.tracking ?: TrackingType.WEIGHT_REPS,
            weeklySessions = weeks.map { it to (perWeek[it] ?: 0) },
            weeklyVolumeKg = weeks.map { it to (volumeByWeek[it] ?: 0.0) },
            setsPerMuscle = muscleCounts, records = records, sessionCount = sessionStarts.size,
        )
    }
}
