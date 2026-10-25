package com.gymtrack.data.repo

import androidx.room.withTransaction
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.data.db.SessionExerciseEntity
import com.gymtrack.data.db.SetRecordEntity
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.db.toDomain
import com.gymtrack.data.db.toEntry
import com.gymtrack.data.db.toRef
import com.gymtrack.data.db.toSpec
import com.gymtrack.data.settings.SettingsStore
import com.gymtrack.domain.CompletionResult
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.ExerciseBests
import com.gymtrack.domain.ExercisePerformance
import com.gymtrack.domain.PlannedExercise
import com.gymtrack.domain.PrReplay
import com.gymtrack.domain.RepTarget
import com.gymtrack.domain.RestTimerState
import com.gymtrack.domain.RoutineExerciseSpec
import com.gymtrack.domain.SessionPlanner
import com.gymtrack.domain.SessionStatus
import com.gymtrack.domain.PlateCalculator
import com.gymtrack.domain.SetEntry
import com.gymtrack.domain.SetType
import com.gymtrack.domain.WarmupCalculator
import com.gymtrack.domain.SetRules
import com.gymtrack.domain.Side
import com.gymtrack.domain.Stats
import com.gymtrack.domain.LoggedSet
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.Suggestion
import com.gymtrack.domain.SuggestionKind
import com.gymtrack.domain.TrackingType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Todo lo que se escribe aquí queda en Room de inmediato (cada toque es una transacción):
 * si Android mata el proceso o se reinicia el teléfono, la sesión en curso se recupera tal cual.
 */
class WorkoutRepository(
    private val db: GymDatabase,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val sessions = db.sessionDao()
    private val routines = db.routineDao()
    private val exercises = db.exerciseDao()

    // ---------------------------------------------------------------- Observación

    val activeSession: Flow<SessionEntity?> = sessions.observeActive()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveWorkout(): Flow<ActiveWorkout?> =
        sessions.observeActive().map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(null) else observeWorkout(id)
        }

    fun observeWorkout(sessionId: String): Flow<ActiveWorkout?> =
        combine(
            sessions.observe(sessionId),
            sessions.observeExercises(sessionId),
            sessions.observeSets(sessionId),
        ) { session, exs, sets ->
            if (session == null) null
            else {
                val bySe = sets.groupBy { it.sessionExerciseId }
                ActiveWorkout(session, exs.map { ActiveExercise(it, bySe[it.id].orEmpty()) })
            }
        }

    // ---------------------------------------------------------------- Inicio de sesión

    suspend fun lastPerformance(exerciseId: String): ExercisePerformance? =
        groupPerformances(sessions.historyForExercise(exerciseId)).lastOrNull()

    /** Vista previa (sin persistir) de lo que se entrenaría con una rutina: sirve para "Tu próximo objetivo". */
    suspend fun previewRoutine(routineId: String): List<PlannedExercise> {
        val s = settings.current()
        return routineSpecs(routineId).map { spec ->
            SessionPlanner.plan(spec, lastPerformance(spec.exercise.id), s.increments, s.unit)
        }
    }

    private suspend fun routineSpecs(routineId: String): List<RoutineExerciseSpec> =
        routines.getExercises(routineId).mapNotNull { re ->
            exercises.get(re.exerciseId)?.toDomain()?.let { re.toSpec(it) }
        }

    /**
     * Inicia una sesión a partir de una rutina (o libre si [routineId] es null).
     * Devuelve el id de la sesión; si ya hay una en curso devuelve esa, nunca crea dos.
     */
    suspend fun startSession(routineId: String?): String {
        sessions.getActive()?.let { return it.id }
        val s = settings.current()
        val routine = routineId?.let { routines.get(it) }
        val specs = if (routine != null) routineSpecs(routine.id) else emptyList()
        val planned = specs.map { spec ->
            SessionPlanner.plan(spec, lastPerformance(spec.exercise.id), s.increments, s.unit)
        }
        val sessionId = UUID.randomUUID().toString()
        db.withTransaction {
            sessions.insert(
                SessionEntity(
                    id = sessionId,
                    routineId = routine?.id,
                    routineName = routine?.name?.ifBlank { "Rutina" } ?: "Entrenamiento libre",
                    startedAt = now(),
                    endedAt = null,
                    status = SessionStatus.IN_PROGRESS.name,
                    notes = "",
                    restTotalSec = s.defaultRestSec,
                ),
            )
            planned.forEachIndexed { index, p -> insertPlanned(sessionId, index, p) }
        }
        return sessionId
    }

    private suspend fun insertPlanned(sessionId: String, position: Int, p: PlannedExercise) {
        val e = p.spec.exercise
        val sg = p.suggestion
        val seId = UUID.randomUUID().toString()
        sessions.upsertExercise(
            SessionExerciseEntity(
                id = seId, sessionId = sessionId, exerciseId = e.id, exerciseName = e.name,
                primaryMuscle = e.primaryMuscle.name, equipment = e.equipment.name, tracking = e.tracking.name,
                unilateral = e.unilateral, position = position, targetSets = p.spec.sets,
                repMin = p.spec.repMin, repMax = p.spec.repMax, targetDurationSec = p.spec.targetDurationSec,
                restSec = p.spec.restSec, notes = p.spec.notes, skipped = false, discomfort = false,
                suggestionKind = sg.kind.name, suggestionWeightKg = sg.weightKg, suggestionReps = sg.reps,
                suggestionDurationSec = sg.durationSec, suggestionReason = sg.reason, suggestionDismissed = false,
            ),
        )
        sessions.upsertSets(
            p.sets.map { ps ->
                SetRecordEntity(
                    id = UUID.randomUUID().toString(), sessionExerciseId = seId,
                    setNumber = ps.setNumber, side = ps.side.name,
                    weightKg = ps.weightKg, reps = ps.reps, durationSec = ps.durationSec, distanceM = ps.distanceM,
                    rpe = null, completed = false, note = "", completedAt = null,
                    prevWeightKg = ps.previous?.weightKg, prevReps = ps.previous?.reps,
                    prevDurationSec = ps.previous?.durationSec, prevDistanceM = ps.previous?.distanceM,
                )
            },
        )
    }

    /** Añade un ejercicio de la biblioteca a la sesión en curso con valores por defecto del tipo de registro. */
    suspend fun addExercise(sessionId: String, exercise: Exercise) {
        val s = settings.current()
        val (min, max, dur) = when (exercise.tracking) {
            TrackingType.WEIGHT_REPS -> Triple(8, 12, null)
            TrackingType.BODYWEIGHT -> Triple(8, 15, null)
            TrackingType.DURATION -> Triple(1, 1, 30)
            TrackingType.DISTANCE -> Triple(1, 1, null)
        }
        val spec = RoutineExerciseSpec(exercise, 3, min, max, dur, null, s.defaultRestSec)
        val planned = SessionPlanner.plan(spec, lastPerformance(exercise.id), s.increments, s.unit)
        db.withTransaction {
            insertPlanned(sessionId, sessions.maxExercisePosition(sessionId) + 1, planned)
        }
    }

    // ---------------------------------------------------------------- Series

    suspend fun editSet(setId: String, transform: (SetRecordEntity) -> SetRecordEntity): String? {
        val cur = sessions.getSet(setId) ?: return "Serie no encontrada"
        val new = transform(cur)
        SetRules.validate(new.toEntry())?.let { return it }
        sessions.upsertSet(new)
        return null
    }

    suspend fun completeSet(setId: String): CompleteOutcome {
        val cur = sessions.getSet(setId) ?: return CompleteOutcome.Invalid("Serie no encontrada")
        val se = sessions.getSessionExercise(cur.sessionExerciseId) ?: return CompleteOutcome.Invalid("Ejercicio no encontrado")
        val tracking = enumOr(se.tracking, TrackingType.WEIGHT_REPS)
        return when (val r = SetRules.complete(cur.toEntry(), tracking, RepTarget(se.repMin, se.repMax), se.targetDurationSec)) {
            is CompletionResult.Invalid -> CompleteOutcome.Invalid(r.message)
            is CompletionResult.Ok -> {
                var restStarted = false
                db.withTransaction {
                    sessions.upsertSet(
                        cur.copy(
                            weightKg = r.set.weightKg, reps = r.set.reps, durationSec = r.set.durationSec,
                            distanceM = r.set.distanceM, completed = true, completedAt = now(),
                        ),
                    )
                    val s = settings.current()
                    // En ejercicios unilaterales el descanso empieza al terminar el último lado.
                    val sideDone = cur.side == Side.LEFT.name && se.unilateral
                    if (s.autoRest && se.restSec > 0 && !sideDone) {
                        sessions.get(se.sessionId)?.let { session ->
                            val t = RestTimerState.start(se.restSec, now())
                            sessions.update(session.copy(restTotalSec = t.totalSec, restEndsAt = t.endsAtMs, restPausedRemainingMs = null))
                            restStarted = true
                        }
                    }
                }
                CompleteOutcome.Done(restStarted)
            }
        }
    }

    /** Marca como completada una serie cronometrada por el modo intervalos (no inicia el descanso normal). */
    suspend fun completeTimedSet(setId: String, durationSec: Int) {
        val cur = sessions.getSet(setId) ?: return
        if (cur.completed) return
        sessions.upsertSet(cur.copy(durationSec = durationSec, completed = true, completedAt = now()))
    }

    suspend fun setSetType(setId: String, type: SetType) {
        val cur = sessions.getSet(setId) ?: return
        sessions.upsertSet(cur.copy(setType = type.name))
    }

    /** Copia a la serie los valores de la sesión anterior (toque en la columna ANTERIOR). */
    suspend fun applyPreviousValues(setId: String): Boolean {
        val cur = sessions.getSet(setId) ?: return false
        if (cur.prevWeightKg == null && cur.prevReps == null && cur.prevDurationSec == null && cur.prevDistanceM == null) return false
        sessions.upsertSet(
            cur.copy(
                weightKg = cur.prevWeightKg ?: cur.weightKg, reps = cur.prevReps ?: cur.reps,
                durationSec = cur.prevDurationSec ?: cur.durationSec, distanceM = cur.prevDistanceM ?: cur.distanceM,
            ),
        )
        return true
    }

    /**
     * Inserta series de calentamiento antes de las de trabajo, calculadas desde el peso de la primera serie de trabajo.
     * @return nº de series añadidas; -1 si ya había calentamiento; 0 si no se pudo calcular (sin peso o no aplica).
     */
    suspend fun addWarmups(sessionExerciseId: String): Int {
        val se = sessions.getSessionExercise(sessionExerciseId) ?: return 0
        if (enumOr(se.tracking, TrackingType.WEIGHT_REPS) != TrackingType.WEIGHT_REPS) return 0
        val existing = sessions.getSetsFor(sessionExerciseId)
        if (existing.any { it.setType == SetType.WARMUP.name }) return -1
        val workingKg = existing.firstOrNull { (it.weightKg ?: 0.0) > 0.0 }?.weightKg ?: return 0
        val s = settings.current()
        val equipment = enumOr(se.equipment, com.gymtrack.domain.Equipment.OTHER)
        val barKg = if (equipment == com.gymtrack.domain.Equipment.BARBELL) s.unit.toKg(PlateCalculator.plateSetFor(s.unit).bar) else 0.0
        val warmups = WarmupCalculator.generate(workingKg, s.increments.stepFor(se.toRef()), barKg)
        if (warmups.isEmpty()) return 0
        val sides = if (se.unilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
        db.withTransaction {
            // Las series existentes se desplazan para que los calentamientos queden primero.
            existing.forEach { sessions.upsertSet(it.copy(setNumber = it.setNumber + warmups.size)) }
            warmups.forEachIndexed { i, w ->
                sides.forEach { side ->
                    sessions.upsertSet(
                        SetRecordEntity(
                            id = UUID.randomUUID().toString(), sessionExerciseId = sessionExerciseId,
                            setNumber = i + 1, side = side.name, weightKg = w.weightKg, reps = w.reps, durationSec = null,
                            distanceM = null, rpe = null, completed = false, note = "", completedAt = null,
                            prevWeightKg = null, prevReps = null, prevDurationSec = null, prevDistanceM = null,
                            setType = SetType.WARMUP.name,
                        ),
                    )
                }
            }
        }
        return warmups.size
    }

    suspend fun uncompleteSet(setId: String) {
        val cur = sessions.getSet(setId) ?: return
        sessions.upsertSet(cur.copy(completed = false, completedAt = null))
    }

    /** Copia peso y repeticiones de la serie anterior del mismo ejercicio (y lado). */
    suspend fun repeatPreviousSet(setId: String): Boolean {
        val cur = sessions.getSet(setId) ?: return false
        val siblings = sessions.getSetsFor(cur.sessionExerciseId)
        val idx = siblings.indexOfFirst { it.id == setId }
        val prev = siblings.take(idx).lastOrNull { it.side == cur.side } ?: return false
        sessions.upsertSet(
            cur.copy(weightKg = prev.weightKg, reps = prev.reps, durationSec = prev.durationSec, distanceM = prev.distanceM),
        )
        return true
    }

    suspend fun addSet(sessionExerciseId: String) {
        val se = sessions.getSessionExercise(sessionExerciseId) ?: return
        val existing = sessions.getSetsFor(sessionExerciseId)
        val entries = existing.map { it.toEntry() }
        val sides = if (se.unilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
        val nextNumber = (entries.maxOfOrNull { it.setNumber } ?: 0) + 1
        db.withTransaction {
            sides.forEach { side ->
                val template = SessionPlanner.extraSet(entries, side).copy(setNumber = nextNumber)
                sessions.upsertSet(
                    SetRecordEntity(
                        id = UUID.randomUUID().toString(), sessionExerciseId = sessionExerciseId,
                        setNumber = nextNumber, side = side.name, weightKg = template.weightKg, reps = template.reps,
                        durationSec = template.durationSec, distanceM = template.distanceM, rpe = null,
                        completed = false, note = "", completedAt = null,
                        prevWeightKg = null, prevReps = null, prevDurationSec = null, prevDistanceM = null,
                    ),
                )
            }
        }
    }

    suspend fun removeSet(setId: String) {
        val cur = sessions.getSet(setId) ?: return
        db.withTransaction {
            sessions.deleteSet(setId)
            // Renumera para que no queden huecos (cada par de lados comparte número).
            val rest = sessions.getSetsFor(cur.sessionExerciseId)
            val numbers = rest.map { it.setNumber }.distinct().sorted()
            rest.forEach { s ->
                val n = numbers.indexOf(s.setNumber) + 1
                if (n != s.setNumber) sessions.upsertSet(s.copy(setNumber = n))
            }
        }
    }

    // ---------------------------------------------------------------- Ejercicios de la sesión

    suspend fun setSkipped(sessionExerciseId: String, skipped: Boolean) =
        updateExercise(sessionExerciseId) { it.copy(skipped = skipped) }

    suspend fun setExerciseNotes(sessionExerciseId: String, notes: String) =
        updateExercise(sessionExerciseId) { it.copy(notes = notes.take(500)) }

    suspend fun setDiscomfort(sessionExerciseId: String, discomfort: Boolean) =
        updateExercise(sessionExerciseId) { it.copy(discomfort = discomfort) }

    suspend fun dismissSuggestion(sessionExerciseId: String) =
        updateExercise(sessionExerciseId) { it.copy(suggestionDismissed = true) }

    suspend fun removeExercise(sessionExerciseId: String) = sessions.deleteSessionExercise(sessionExerciseId)

    private suspend fun updateExercise(id: String, f: (SessionExerciseEntity) -> SessionExerciseEntity) {
        sessions.getSessionExercise(id)?.let { sessions.upsertExercise(f(it)) }
    }

    /** El usuario decide aplicar la sugerencia: solo toca series pendientes y nunca modifica las completadas. */
    suspend fun applySuggestion(sessionExerciseId: String) {
        val se = sessions.getSessionExercise(sessionExerciseId) ?: return
        db.withTransaction {
            sessions.getSetsFor(sessionExerciseId).filter { !it.completed }.forEach { s ->
                sessions.upsertSet(
                    s.copy(
                        weightKg = se.suggestionWeightKg ?: s.weightKg,
                        durationSec = if (se.suggestionKind == SuggestionKind.MORE_TIME.name) se.suggestionDurationSec ?: s.durationSec else s.durationSec,
                    ),
                )
            }
            sessions.upsertExercise(se.copy(suggestionDismissed = true))
        }
    }

    suspend fun setSessionNotes(sessionId: String, notes: String) {
        sessions.get(sessionId)?.let { sessions.update(it.copy(notes = notes.take(1000))) }
    }

    // ---------------------------------------------------------------- Temporizador de descanso

    fun restState(session: SessionEntity): RestTimerState =
        RestTimerState(session.restTotalSec, session.restEndsAt, session.restPausedRemainingMs)

    private suspend fun updateRest(sessionId: String, f: (RestTimerState) -> RestTimerState) {
        val s = sessions.get(sessionId) ?: return
        val t = f(restState(s))
        sessions.update(s.copy(restTotalSec = t.totalSec, restEndsAt = t.endsAtMs, restPausedRemainingMs = t.pausedRemainingMs))
    }

    suspend fun startRest(sessionId: String, totalSec: Int) = updateRest(sessionId) { RestTimerState.start(totalSec, now()) }
    suspend fun pauseRest(sessionId: String) = updateRest(sessionId) { it.pause(now()) }
    suspend fun resumeRest(sessionId: String) = updateRest(sessionId) { it.resume(now()) }
    suspend fun restartRest(sessionId: String) = updateRest(sessionId) { it.restart(now()) }
    suspend fun skipRest(sessionId: String) = updateRest(sessionId) { it.skip() }
    suspend fun adjustRest(sessionId: String, deltaSec: Int) = updateRest(sessionId) { it.adjust(deltaSec, now()) }

    // ---------------------------------------------------------------- Finalizar / descartar

    suspend fun finish(sessionId: String, notes: String?): FinishOutcome {
        val s = sessions.get(sessionId)
        if (s == null || s.status != SessionStatus.IN_PROGRESS.name) return FinishOutcome.NOT_ACTIVE
        if (sessions.getSets(sessionId).none { it.completed }) return FinishOutcome.NO_COMPLETED_SETS
        sessions.update(
            s.copy(
                status = SessionStatus.FINISHED.name, endedAt = now(),
                notes = notes?.take(1000) ?: s.notes,
                restEndsAt = null, restPausedRemainingMs = null,
            ),
        )
        return FinishOutcome.FINISHED
    }

    /** Descartar conserva el registro con estado DISCARDED (no aparece en historial ni estadísticas). */
    suspend fun discard(sessionId: String) {
        val s = sessions.get(sessionId) ?: return
        if (s.status != SessionStatus.IN_PROGRESS.name) return
        sessions.update(s.copy(status = SessionStatus.DISCARDED.name, endedAt = now(), restEndsAt = null, restPausedRemainingMs = null))
    }

    // ---------------------------------------------------------------- Récords y resumen

    /** Mejores marcas previas a [beforeMs] para cada ejercicio indicado. */
    suspend fun priorBests(beforeMs: Long, exercises: Map<String, TrackingType>): Map<String, ExerciseBests> {
        if (exercises.isEmpty()) return emptyMap()
        val rows = sessions.historyBefore(beforeMs, exercises.keys.toList())
        val byExercise = groupPerformances(rows).groupBy { it.exerciseId }
        return exercises.mapValues { (id, tracking) -> ExerciseBests.from(byExercise[id].orEmpty(), tracking) }
    }

    suspend fun summary(sessionId: String): SessionSummary? {
        val session = sessions.get(sessionId) ?: return null
        val rows = sessions.historyForSession(sessionId)
        val logged = rows.map {
            LoggedSet(
                it.exerciseId, it.exerciseName, enumOr(it.primaryMuscle, MuscleGroup.FULL_BODY),
                enumOr(it.tracking, TrackingType.WEIGHT_REPS), it.toEntry(),
            )
        }
        val totals = Stats.totals(logged)
        val trackingById = rows.associate { it.exerciseId to enumOr(it.tracking, TrackingType.WEIGHT_REPS) }
        val bests = priorBests(session.startedAt, trackingById)
        val records = buildList {
            for ((exerciseId, exRows) in rows.groupBy { it.exerciseId }) {
                val ordered = exRows.sortedBy { it.completedAt ?: 0L }.map { it.setId to it.toEntry() }
                val found = PrReplay.replay(bests.getValue(exerciseId), ordered)
                val name = exRows.first().exerciseName
                val tr = trackingById.getValue(exerciseId)
                found.values.forEach { list -> list.forEach { add(SessionPr(exerciseId, name, it, tr)) } }
            }
        }
        val previous = session.routineId?.let { rid ->
            sessions.getFinished().firstOrNull { it.routineId == rid && it.startedAt < session.startedAt }
        }?.let { prev ->
            val prevLogged = sessions.historyForSession(prev.id).map {
                LoggedSet(it.exerciseId, it.exerciseName, enumOr(it.primaryMuscle, MuscleGroup.FULL_BODY), enumOr(it.tracking, TrackingType.WEIGHT_REPS), it.toEntry())
            }
            val pt = Stats.totals(prevLogged)
            PreviousComparison(prev.startedAt, pt.volumeKg, pt.completedSets)
        }
        val end = session.endedAt ?: now()
        return SessionSummary(
            session = session,
            durationSec = ((end - session.startedAt) / 1000).coerceAtLeast(0),
            exerciseCount = rows.map { it.sessionExerciseId }.distinct().size,
            totals = totals,
            records = records,
            previous = previous,
        )
    }

    suspend fun deleteFinished(sessionId: String) {
        val s = sessions.get(sessionId) ?: return
        if (s.status == SessionStatus.IN_PROGRESS.name) return
        sessions.delete(sessionId)
    }

    // Reservado para pruebas de integración.
    internal suspend fun suggestionOf(sessionExerciseId: String): Suggestion? {
        val se = sessions.getSessionExercise(sessionExerciseId) ?: return null
        val kind = se.suggestionKind?.let { enumOr(it, SuggestionKind.HOLD) } ?: return null
        return Suggestion(kind, se.suggestionWeightKg, se.suggestionReps, se.suggestionDurationSec, se.suggestionReason.orEmpty())
    }
}
