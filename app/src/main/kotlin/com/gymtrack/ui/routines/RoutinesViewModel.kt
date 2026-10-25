package com.gymtrack.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.db.RoutineEntity
import com.gymtrack.data.db.RoutineExerciseEntity
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.RoutineDays
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineListItem(
    val routine: RoutineEntity,
    val exerciseCount: Int,
    val setCount: Int,
    /** Grupos musculares principales de la rutina, de más a menos ejercicios. */
    val muscles: List<MuscleGroup> = emptyList(),
)

data class RoutinesState(
    val loading: Boolean = true,
    val items: List<RoutineListItem> = emptyList(),
    val hasActiveSession: Boolean = false,
)

class RoutinesViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<RoutinesState> =
        combine(c.routines.routines, c.routines.counts, c.workout.activeSession, c.routines.allExercises, c.exercises.allById) { routines, counts, active, rows, exercises ->
            val byId = counts.associateBy { it.routineId }
            val musclesByRoutine = rows.groupBy { it.routineId }.mapValues { (_, list) ->
                list.mapNotNull { exercises[it.exerciseId]?.primaryMuscle }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            }
            RoutinesState(
                loading = false,
                items = routines.map {
                    RoutineListItem(it, byId[it.id]?.exerciseCount ?: 0, byId[it.id]?.setCount ?: 0, musclesByRoutine[it.id].orEmpty())
                },
                hasActiveSession = active != null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesState())

    fun create(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(c.routines.create()) }
    }

    fun createFromTemplate(template: com.gymtrack.domain.RoutineTemplate, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val rest = c.settings.current().defaultRestSec
            onCreated(c.routines.createFromTemplate(template, rest))
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch { c.routines.duplicate(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { c.routines.delete(id) }
    }

    fun start(routineId: String?, onStarted: () -> Unit) {
        viewModelScope.launch {
            c.workout.startSession(routineId)
            onStarted()
        }
    }
}

data class RoutineExerciseItem(val row: RoutineExerciseEntity, val exercise: Exercise?)

data class RoutineDraft(val name: String, val description: String, val daysMask: Int)

data class RoutineEditState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val draft: RoutineDraft? = null,
    val items: List<RoutineExerciseItem> = emptyList(),
    val others: List<RoutineListItem> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val hasActiveSession: Boolean = false,
) {
    val totalSets: Int get() = items.sumOf { it.row.sets }
}

@OptIn(FlowPreview::class)
class RoutineEditViewModel(private val c: AppContainer, val routineId: String) : ViewModel() {
    private val draft = MutableStateFlow<RoutineDraft?>(null)
    private val missing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val r = c.routines.get(routineId)
            if (r == null) missing.value = true else draft.value = RoutineDraft(r.name, r.description, r.daysMask)
        }
        // Autoguardado con una pequeña espera para no escribir en cada pulsación.
        viewModelScope.launch {
            draft.filterNotNull().debounce(300).collect { c.routines.updateDetails(routineId, it.name, it.description, it.daysMask) }
        }
    }

    override fun onCleared() {
        // Si el usuario sale antes de que venza la espera, no se pierde el último cambio.
        val d = draft.value ?: return
        c.appScope.launch { c.routines.updateDetails(routineId, d.name, d.description, d.daysMask) }
    }

    val state: StateFlow<RoutineEditState> = combine(
        draft,
        missing,
        c.routines.observeExercises(routineId),
        c.exercises.allById,
        combine(c.routines.routines, c.routines.counts) { r, k -> r to k },
    ) { d, miss, rows, byId, (routines, counts) ->
        val countById = counts.associateBy { it.routineId }
        RoutineEditState(
            loading = d == null && !miss,
            missing = miss,
            draft = d,
            items = rows.map { RoutineExerciseItem(it, byId[it.exerciseId]) },
            others = routines.filter { it.id != routineId }
                .map { RoutineListItem(it, countById[it.id]?.exerciseCount ?: 0, countById[it.id]?.setCount ?: 0) },
        )
    }.let { f ->
        combine(f, c.settings.settings, c.workout.activeSession) { s, settings, active ->
            s.copy(settings = settings, hasActiveSession = active != null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditState())

    fun setName(v: String) = draft.update { it?.copy(name = v.take(60)) }
    fun setDescription(v: String) = draft.update { it?.copy(description = v.take(300)) }
    fun toggleDay(day: java.time.DayOfWeek) = draft.update {
        it?.copy(daysMask = it.daysMask xor RoutineDays.bit(day))
    }

    fun addExercise(ex: Exercise) {
        viewModelScope.launch {
            val restDefault = c.settings.current().defaultRestSec
            c.routines.addExercise(routineId, ex.id) {
                when (ex.tracking) {
                    com.gymtrack.domain.TrackingType.DURATION -> copy(repMin = 1, repMax = 1, targetDurationSec = 30, restSec = restDefault)
                    com.gymtrack.domain.TrackingType.BODYWEIGHT -> copy(repMin = 8, repMax = 15, restSec = restDefault)
                    com.gymtrack.domain.TrackingType.DISTANCE -> copy(repMin = 1, repMax = 1, restSec = restDefault)
                    com.gymtrack.domain.TrackingType.WEIGHT_REPS -> copy(restSec = restDefault)
                }
            }
        }
    }

    fun update(row: RoutineExerciseEntity) {
        viewModelScope.launch { c.routines.updateExercise(row) }
    }

    fun remove(rowId: String) {
        viewModelScope.launch { c.routines.removeExercise(routineId, rowId) }
    }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { c.routines.reorder(routineId, orderedIds) }
    }

    fun move(rowId: String, delta: Int) {
        val ids = state.value.items.map { it.row.id }.toMutableList()
        val from = ids.indexOf(rowId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        reorder(ids)
    }

    suspend fun exercisesOf(otherRoutineId: String): List<RoutineExerciseItem> {
        val byId = c.exercises.allById.let { f -> f.first() }
        return c.routines.getExercises(otherRoutineId).map { RoutineExerciseItem(it, byId[it.exerciseId]) }
    }

    fun copyFrom(otherRoutineId: String, rowIds: Set<String>) {
        viewModelScope.launch { c.routines.copyExercises(otherRoutineId, rowIds, routineId) }
    }

    fun start(onStarted: () -> Unit) {
        viewModelScope.launch {
            c.workout.startSession(routineId)
            onStarted()
        }
    }
}
