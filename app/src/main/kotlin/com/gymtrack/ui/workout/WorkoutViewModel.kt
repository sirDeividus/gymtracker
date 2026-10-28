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
import com.gymtrack.domain.IntervalPhase
import com.gymtrack.domain.IntervalPlan
import com.gymtrack.domain.IntervalSnapshot
import com.gymtrack.domain.IntervalTimer
import com.gymtrack.domain.RestTimerState
import com.gymtrack.domain.SetType
import com.gymtrack.domain.TrackingType
import com.gymtrack.notify.RestNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Configuración previa del modo intervalos para un ejercicio por duración. */
data class IntervalSetup(
    val exerciseId: String,
    val exerciseName: String,
    val workSec: Int,
    val restSec: Int,
    /** Rondas (repeticiones de trabajo) por ciclo. */
    val rounds: Int,
    val cycles: Int = 1,
    /** Descanso entre un ciclo y el siguiente. */
    val cycleRestSec: Int = 30,
    /** Cuenta atrás antes de empezar. */
    val prepSec: Int = 10,
)

/** Estado de la pantalla del temporizador; [started] = false mientras espera el botón de empezar. */
data class IntervalUi(
    val exerciseName: String,
    val plan: IntervalPlan,
    val snapshot: IntervalSnapshot,
    val nextPhase: IntervalPhase,
    val started: Boolean,
    val paused: Boolean,
    val muted: Boolean,
)

private class IntervalRun(val exerciseId: String, var plan: IntervalPlan, val name: String) {
    var setIds: List<String> = emptyList()
    var started = false
    var startMs = 0L
    var pausedAt: Long? = null
    var pausedTotal = 0L
    var done = 0
    var muted = false
    var lastKey: Triple<IntervalPhase, Int, Int>? = null
    var lastCountKey: String? = null
    fun elapsed(now: Long): Long = if (!started) 0L else (pausedAt ?: now) - startMs - pausedTotal
    fun reset(newPlan: IntervalPlan) {
        plan = newPlan; setIds = emptyList(); started = false; startMs = 0; pausedAt = null; pausedTotal = 0; done = 0; lastKey = null; lastCountKey = null
    }
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
        c.speech.warmUp()
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

    private var lastCountKey: Pair<Long, Int>? = null

    /** Voz (o pitido corto) para un número de la cuenta atrás. */
    private fun cueNumber(n: Int) {
        val s = state.value.settings
        if (s.voice) c.speech.speak(when (n) { 3 -> "tres"; 2 -> "dos"; else -> "uno" }) else if (s.sound) RestNotifier.tick()
    }

    private fun cueWord(text: String) {
        if (state.value.settings.voice) c.speech.speak(text)
    }

    /** 3, 2, 1 en los últimos tres segundos del descanso (solo si el descanso dura más de tres). */
    private fun countdownRest(st: WorkoutUiState) {
        val rest = st.rest ?: return
        val ends = rest.endsAtMs ?: return
        val left = st.restRemainingSec
        if (left !in 1..3 || rest.totalSec <= 3) return
        val key = ends to left
        if (key == lastCountKey) return
        lastCountKey = key
        cueNumber(left)
    }

    private fun checkRestFinished(st: WorkoutUiState) {
        countdownRest(st)
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
        val km = Formatting.parseDecimal(text) ?: return
        edit(setId) { it.copy(distanceM = km * 1000) }
    }

    /** Minutos (admite decimales) → segundos. */
    fun setMinutes(setId: String, text: String) {
        if (text.isBlank()) return edit(setId) { it.copy(durationSec = null) }
        val min = Formatting.parseDecimal(text) ?: return
        edit(setId) { it.copy(durationSec = Math.round(min * 60).toInt()) }
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

    fun setType(setId: String, type: SetType) {
        viewModelScope.launch { c.workout.setSetType(setId, type) }
    }

    fun copyPrevious(setId: String) {
        viewModelScope.launch { if (!c.workout.applyPreviousValues(setId)) events.emit("No hay valores anteriores que copiar") }
    }

    fun addWarmups(sessionExerciseId: String) {
        viewModelScope.launch {
            when (val n = c.workout.addWarmups(sessionExerciseId)) {
                -1 -> events.emit("Este ejercicio ya tiene calentamiento")
                0 -> events.emit("Pon primero el peso de la primera serie de trabajo")
                else -> events.emit("Añadidas $n series de calentamiento (puedes editarlas)")
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

    fun setRest(id: String, sec: Int) {
        viewModelScope.launch { c.workout.setExerciseRest(id, sec) }
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

    // ------------------------------------------------------------ Modo intervalos (Tabata)

    /** Configuración que se está editando (null = el diálogo de edición está cerrado). */
    val intervalSetup = MutableStateFlow<IntervalSetup?>(null)
    val interval = MutableStateFlow<IntervalUi?>(null)
    private var run: IntervalRun? = null
    private var runJob: Job? = null
    /** Plan inicial tomado del ejercicio de la rutina: trabajo = duración, descanso = su descanso, rondas = sus series. */
    private fun planFor(ex: com.gymtrack.data.repo.ActiveExercise): IntervalPlan = runCatching {
        val extra = com.gymtrack.data.settings.IntervalPrefs.get(c.context, ex.entity.exerciseId)
        IntervalPlan(
            workSec = (ex.entity.targetDurationSec ?: ex.sets.firstOrNull()?.durationSec ?: 20).coerceIn(1, 3600),
            restSec = ex.entity.restSec.coerceIn(0, 3600),
            rounds = ex.sets.size.coerceIn(1, IntervalPlan.MAX_ROUNDS),
            prepSec = extra.prepSec, cycles = extra.cycles, cycleRestSec = extra.cycleRestSec,
        )
    }.getOrDefault(IntervalPlan(20, 10, 8, 10, 1, 30))

    /** Abre el temporizador listo para empezar (en pausa) con los valores del ejercicio. */
    fun openIntervalSetup(sessionExerciseId: String) {
        val ex = state.value.workout?.exercises?.firstOrNull { it.entity.id == sessionExerciseId } ?: return
        c.speech.warmUp()
        val r = IntervalRun(sessionExerciseId, planFor(ex), ex.entity.exerciseName)
        run = r
        runJob?.cancel()
        runJob = viewModelScope.launch {
            while (isActive) {
                tickInterval(r)
                delay(200)
            }
        }
    }

    /** Botón central: empezar, pausar o reanudar. Al empezar se asegura una serie pendiente por ronda. */
    fun playPauseInterval() {
        val r = run ?: return
        val now = System.currentTimeMillis()
        when {
            !r.started -> {
                r.started = true; r.startMs = now; r.pausedAt = null; r.pausedTotal = 0
                viewModelScope.launch { r.setIds = c.workout.pendingSetIds(r.exerciseId, r.plan.totalRounds) }
            }
            r.pausedAt == null -> r.pausedAt = now
            else -> { r.pausedTotal += now - (r.pausedAt ?: now); r.pausedAt = null }
        }
    }

    /** «Editar temporizador»: pausa (si corría) y abre la edición con la configuración actual. */
    fun editInterval() {
        val r = run ?: return
        if (r.started && r.pausedAt == null) r.pausedAt = System.currentTimeMillis()
        val p = r.plan
        intervalSetup.value = IntervalSetup(r.exerciseId, r.name, p.workSec, p.restSec, p.rounds, p.cycles, p.cycleRestSec, p.prepSec)
    }

    fun cancelIntervalSetup() { intervalSetup.value = null }

    /** Guarda la configuración y reinicia el temporizador (en pausa, listo para empezar). */
    fun applyIntervalConfig(cfg: IntervalSetup) {
        val r = run ?: return
        val plan = runCatching {
            IntervalPlan(cfg.workSec, cfg.restSec, cfg.rounds, cfg.prepSec, cfg.cycles, cfg.cycleRestSec)
        }.getOrNull()
        intervalSetup.value = null
        if (plan == null) {
            events.tryEmit("Valores fuera de rango")
            return
        }
        // Ciclos, descanso entre ciclos y cuenta atrás se recuerdan para este ejercicio.
        val exId = state.value.workout?.exercises?.firstOrNull { it.entity.id == r.exerciseId }?.entity?.exerciseId
        if (exId != null) {
            com.gymtrack.data.settings.IntervalPrefs.set(c.context, exId, com.gymtrack.data.settings.IntervalPrefs.Config(plan.cycles, plan.cycleRestSec, plan.prepSec))
        }
        r.reset(plan)
    }

    fun toggleIntervalSound() { run?.let { it.muted = !it.muted } }

    private suspend fun tickInterval(r: IntervalRun) {
        val now = System.currentTimeMillis()
        val elapsed = r.elapsed(now)
        val snap = IntervalTimer.snapshot(r.plan, elapsed)
        if (r.started) {
            while (r.done < snap.completedRounds && r.done < r.setIds.size) {
                c.workout.completeTimedSet(r.setIds[r.done], r.plan.workSec)
                r.done++
            }
            val key = Triple(snap.phase, snap.cycle, snap.round)
            val phaseChanged = key != r.lastKey
            if (phaseChanged) {
                val first = r.lastKey == null
                r.lastKey = key
                if (!first && !r.muted) intervalAlert()
            }
            if (!r.muted) {
                val secLeft = ceil(snap.phaseRemainingMs / 1000.0).toInt()
                val phaseSecs = (snap.phaseTotalMs / 1000).toInt()
                if (phaseChanged) {
                    // Al empezar cada tramo: qué toca hacer.
                    when (snap.phase) {
                        IntervalPhase.WORK -> cueWord("Go")
                        IntervalPhase.REST, IntervalPhase.CYCLE_REST -> cueWord("Descanso")
                        IntervalPhase.PREP -> cueWord("Prepárate")
                        IntervalPhase.DONE -> cueWord("Terminado")
                    }
                } else if (r.pausedAt == null && secLeft in 1..3 && secLeft < phaseSecs) {
                    val ck = "$key:$secLeft"
                    if (ck != r.lastCountKey) {
                        r.lastCountKey = ck
                        // En los descansos, a 3 s: «Prepárate»; después 2, 1. En trabajo y preparación: 3, 2, 1.
                        val rest = snap.phase == IntervalPhase.REST || snap.phase == IntervalPhase.CYCLE_REST
                        if (rest && secLeft == 3) cueWord("Prepárate") else cueNumber(secLeft)
                    }
                }
            }
        }
        interval.value = IntervalUi(
            r.name, r.plan, snap, IntervalTimer.nextPhase(r.plan, elapsed),
            started = r.started, paused = !r.started || r.pausedAt != null, muted = r.muted,
        )
    }

    private fun intervalAlert() {
        val s = state.value.settings
        if (s.vibration) RestNotifier.vibrate(c.context)
        if (s.sound) RestNotifier.beep()
    }

    /** Cierra el temporizador. Las rondas ya completadas se conservan. */
    fun stopInterval() {
        runJob?.cancel()
        runJob = null
        run = null
        interval.value = null
        intervalSetup.value = null
    }

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
