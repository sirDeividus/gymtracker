package com.gymtrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.db.toEntry
import com.gymtrack.data.repo.ActiveWorkout
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.LoggedSet
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.SessionTotals
import com.gymtrack.domain.Stats
import com.gymtrack.domain.TrackingType
import com.gymtrack.ui.components.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HistoryItem(
    val session: SessionEntity,
    val exerciseCount: Int,
    val totals: SessionTotals,
)

data class HistoryState(
    val loading: Boolean = true,
    val items: List<HistoryItem> = emptyList(),
    val trainedDays: Set<LocalDate> = emptySet(),
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val settings: UserSettings = UserSettings(),
) {
    val visible: List<HistoryItem>
        get() = selectedDay?.let { d -> items.filter { Dates.localDate(it.session.startedAt) == d } } ?: items
}

fun buildHistoryItems(
    sessions: List<SessionEntity>,
    counts: Map<String, Int>,
    rows: List<com.gymtrack.data.db.HistorySetRow>,
): List<HistoryItem> {
    val bySession = rows.groupBy { it.sessionId }
    return sessions.map { s ->
        val logged = bySession[s.id].orEmpty().map {
            LoggedSet(it.exerciseId, it.exerciseName, enumOr(it.primaryMuscle, MuscleGroup.FULL_BODY), enumOr(it.tracking, TrackingType.WEIGHT_REPS), it.toEntry())
        }
        HistoryItem(s, counts[s.id] ?: 0, Stats.totals(logged))
    }
}

class HistoryViewModel(c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<HistoryState> = combine(
        c.history.sessions, c.history.exerciseCounts, c.history.allRows, month, selected,
    ) { sessions, counts, rows, m, sel ->
        val items = buildHistoryItems(sessions, counts, rows)
        HistoryState(false, items, sessions.map { Dates.localDate(it.startedAt) }.toSet(), m, sel)
    }.let { flow -> combine(flow, c.settings.settings) { st, settings -> st.copy(settings = settings) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun selectDay(d: LocalDate) = selected.update { if (it == d) null else d }
    fun clearDay() { selected.value = null }
}

class SessionDetailViewModel(private val c: AppContainer, val sessionId: String) : ViewModel() {
    val workout: StateFlow<ActiveWorkout?> = c.workout.observeWorkout(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<UserSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            c.workout.deleteFinished(sessionId)
            onDone()
        }
    }
}
