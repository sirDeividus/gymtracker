package com.gymtrack.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.db.SetRecordEntity
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.db.toEntry
import com.gymtrack.data.repo.ActiveWorkout
import com.gymtrack.data.repo.CompleteOutcome
import com.gymtrack.data.repo.FinishOutcome
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.ExerciseBests
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.PersonalRecord
import com.gymtrack.domain.PrReplay
import com.gymtrack.domain.RestTimerState
import com.gymtrack.domain.TrackingType
import com.gymtrack.notify.RestNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: ActiveWorkout? = null,
    val settings: UserSettings = UserSettings(),
    val nowMs: Long = 0L,
    val rest: RestTimerState? = null,
    val restRemainingSec: Int = 0,
    val prs: Map<String, List<PersonalRecord>> = emptyMap(),
) {
    val elapsedSec: Long
        get() = workout?.let { ((nowMs - it.session.startedAt) / 1000).coerceAtLeast(0) } ?: 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    private val tick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(250)
        }
    }

    private val workoutFlow = c.workout.observeActiveWorkout().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private var bestsKey: String? = null
    private var bests: Map<String, ExerciseBests> = emptyMap()
    private var lastRestAlertFor: Long? = null

    /** Mensajes breves para la barra inferior (validaciones, descanso terminado…). */
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val prsFlow = workoutFlow.mapLatest { w -> if (w == null) emptyMap() else computePrs(w) }

    val state: StateFlow<WorkoutUiState> = combine(workoutFlow, prsFlow, c.settings.settings, tick) { w, prs, s, now ->
        val rest = w?.let { c.workout.restState(it.session) }?.takeIf { it.active }
        WorkoutUiState(
            loading = false, workout = w, settings = s, nowMs = now, rest = rest,
            restRemainingSec = rest?.let { ceil(it.remainingMs(now) / 1000.0).toInt() } ?: 0, prs = prs,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutUiState())

    init {
        viewModelScope.launch { state.collect { checkRestFinished(it) } }
    }

    // ------------------------------------------------------------ Récords (sin interrumpir)

    private suspend fun computePrs(w: ActiveWorkout): Map<String, List<PersonalRecord>> {
        val tracking = w.exercises.associate { it.entity.exerciseId to enumOr(it.entity.tracking, TrackingType.WEIGHT_REPS) }
        val key = w.session.id + tracking.keys.sorted().joinToString(",")
        if (key != bestsKey) {
            bests = c.workout.priorBests(w.session.startedAt, tracking)
            bestsKey = key
        }
        val out = LinkedHashMap<String, List<PersonalRecord>>()
        for ((exerciseId, list) in w.exercises.filter { !it.entity.skipped }.groupBy { it.entity.exerciseId }) {
            val sets = list.flatMap { it.sets }.filter { it.completed }
                .sortedBy { it.completedAt ?: 0L }
                .map { it.id to it.toEntry() }
            out += PrReplay.replay(bests[exerciseId] ?: ExerciseBests.empty(tracking.getValue(exerciseId)), sets)
        }
        return out
    }

    // ------------------------------------------------------------ Fin del descanso

    private fun checkRestFinished(st: WorkoutUiState) {
        val rest = st.rest ?: return
        val ends = rest.endsAtMs ?: return
        if (st.nowMs < ends || lastRestAlertFor == ends) return
        lastRestAlertFor = ends
        val sessionId = st.workout?.session?.id ?: return
        // Si el aviso llegó con la app en segundo plano (alarma), no repetimos vibración tarde.
        val onTime = st.nowMs - ends < 3_000
        viewModelScope.launch {
            c.workout.skipRest(sessionId)
            if (onTime) {
                if (st.settings.vibration) RestNotifier.vibrate(c.context)
                if (st.settings.sound) RestNotifier.beep()
                events.emit("Descanso terminado")
            }
        }
    }

    // ------------------------------------------------------------ Edición de series

    private fun parseKg(text: String, st: WorkoutUiState, sign: Int): Pair<Boolean, Double?> {
        if (text.isBlank()) return true to null
        val v = Formatting.parseDecimal(text) ?: return false to null
        return true to st.settings.unit.toKg(v) * sign
    }

    fun setWeight(setId: String, text: String, sign: Int = 1) {
        val (ok, kg) = parseKg(text, state.value, sign)
        if (!ok) return
        edit(setId) { it.copy(weightKg = kg) }
    }

    fun setReps(setId: String, text: String) {
        if (text.isBlank()) return edit(setId) { it.copy(reps = null) }
        val v = text.trim().toIntOrNull() ?: return
        edit(setId) { it.copy(reps = v) }
    }

    fun setDuration(setId: String, text: String) {
        if (text.isBlank()) return edit(setId) { it.copy(durationSec = null) }
        val v = text.trim().toIntOrNull() ?: return
        edit(setId) { it.copy(durationSec = v) }
    }

    fun setDistance(setId: String, text: String) {
        if (text.isBlank()) return edit(setId) { it.copy(distanceM = null) }
        val v = Formatting.parseDecimal(text) ?: return
        edit(setId) { it.copy(distanceM = v) }
    }

    fun setRpe(setId: String, rpe: Double?) = edit(setId) { it.copy(rpe = rpe) }

    /** Cambia a lastre (+1), asistencia (−1) o sin peso (0) conservando la magnitud ya escrita. */
    fun setBodyweightMode(exerciseSets: List<SetRecordEntity>, mode: Int) {
        viewModelScope.launch {
            exerciseSets.forEach { s ->
                val mag = abs(s.weightKg ?: 0.0)
                val new = if (mode == 0) null else if (s.weightKg == null) null else mag * mode
                c.workout.editSet(s.id) { it.copy(weightKg = new) }
            }
        }
    }

    private fun edit(setId: String, f: (SetRecordEntity) -> SetRecordEntity) {
        viewModelScope.launch { c.workout.editSet(setId, f)?.let { events.emit(it) } }
    }

    fun toggleComplete(set: SetRecordEntity) {
        viewModelScope.launch {
            if (set.completed) {
                c.workout.uncompleteSet(set.id)
            } else {
                when (val r = c.workout.completeSet(set.id)) {
                    is CompleteOutcome.Invalid -> events.emit(r.message)
                    is CompleteOutcome.Done -> Unit
                }
            }
        }
    }

    fun repeatPrevious(setId: String) {
        viewModelScope.launch { if (!c.workout.repeatPreviousSet(setId)) events.emit("No hay una serie anterior que copiar") }
    }

    fun addSet(sessionExerciseId: String) {
        viewModelScope.launch { c.workout.addSet(sessionExerciseId) }
    }

    fun removeSet(setId: String) {
        viewModelScope.launch { c.workout.removeSet(setId) }
    }

    // ------------------------------------------------------------ Ejercicios

    fun setSkipped(id: String, skipped: Boolean) {
        viewModelScope.launch { c.workout.setSkipped(id, skipped) }
    }

    fun removeExercise(id: String) {
        viewModelScope.launch { c.workout.removeExercise(id) }
    }

    fun setNotes(id: String, notes: String) {
        viewModelScope.launch { c.workout.setExerciseNotes(id, notes) }
    }

    fun setDiscomfort(id: String, on: Boolean) {
        viewModelScope.launch { c.workout.setDiscomfort(id, on) }
    }

    fun applySuggestion(id: String) {
        viewModelScope.launch { c.workout.applySuggestion(id) }
    }

    fun dismissSuggestion(id: String) {
        viewModelScope.launch { c.workout.dismissSuggestion(id) }
    }

    fun addExercise(ex: Exercise) {
        val sid = state.value.workout?.session?.id ?: return
        viewModelScope.launch { c.workout.addExercise(sid, ex) }
    }

    // ------------------------------------------------------------ Descanso

    private fun withSession(f: suspend (String) -> Unit) {
        val sid = state.value.workout?.session?.id ?: return
        viewModelScope.launch { f(sid) }
    }

    fun startRest(sec: Int) = withSession { c.workout.startRest(it, sec) }
    fun pauseRest() = withSession { c.workout.pauseRest(it) }
    fun resumeRest() = withSession { c.workout.resumeRest(it) }
    fun restartRest() = withSession { c.workout.restartRest(it) }
    fun skipRest() = withSession { c.workout.skipRest(it) }
    fun adjustRest(deltaSec: Int) = withSession { c.workout.adjustRest(it, deltaSec) }

    // ------------------------------------------------------------ Finalizar / descartar

    fun finish(notes: String, onFinished: (String) -> Unit) {
        val sid = state.value.workout?.session?.id ?: return
        viewModelScope.launch {
            when (c.workout.finish(sid, notes)) {
                FinishOutcome.FINISHED -> onFinished(sid)
                FinishOutcome.NO_COMPLETED_SETS -> events.emit("Completa al menos una serie para guardar el entrenamiento")
                FinishOutcome.NOT_ACTIVE -> events.emit("Este entrenamiento ya no está en curso")
            }
        }
    }

    fun discard(onDone: () -> Unit) {
        val sid = state.value.workout?.session?.id ?: return
        viewModelScope.launch {
            c.workout.discard(sid)
            onDone()
        }
    }
}
