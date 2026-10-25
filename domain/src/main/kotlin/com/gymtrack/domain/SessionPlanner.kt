package com.gymtrack.domain

/** Un ejercicio tal y como está definido en una rutina (plantilla, no resultado). */
data class RoutineExerciseSpec(
    val exercise: Exercise,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetDurationSec: Int? = null,
    val startWeightKg: Double? = null,
    val restSec: Int = 90,
    val notes: String = "",
) {
    val target: RepTarget get() = RepTarget(repMin, repMax)
    val ref: ExerciseRef
        get() = ExerciseRef(exercise.id, exercise.name, exercise.primaryMuscle, exercise.equipment, exercise.tracking)
}

data class PlannedSet(
    val setNumber: Int,
    val side: Side,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceM: Double?,
    /** Serie equivalente de la última sesión (para la columna ANTERIOR). */
    val previous: SetEntry?,
)

data class PlannedExercise(
    val spec: RoutineExerciseSpec,
    val sets: List<PlannedSet>,
    val suggestion: Suggestion,
    val previousPerformance: ExercisePerformance?,
)

object SessionPlanner {
    const val MAX_SETS = 20

    /**
     * Construye los ejercicios de una sesión nueva a partir de la rutina.
     * El número de series coincide exactamente con el configurado (×2 lados si es unilateral).
     * Los valores se rellenan con los de la última sesión del MISMO ejercicio (por id);
     * la sugerencia de progresión se muestra aparte y nunca se aplica sola.
     */
    fun plan(
        spec: RoutineExerciseSpec,
        previous: ExercisePerformance?,
        increments: IncrementSettings,
        unit: WeightUnit,
    ): PlannedExercise {
        require(spec.sets in 1..MAX_SETS) { "Número de series inválido: ${spec.sets}" }
        val prevDone = previous?.completedSets.orEmpty()
        val sides = if (spec.exercise.unilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
        val sets = buildList {
            for (n in 1..spec.sets) for (side in sides) {
                val prev = matchPrevious(prevDone, n, side)
                add(
                    PlannedSet(
                        setNumber = n,
                        side = side,
                        weightKg = prev?.weightKg ?: spec.startWeightKg,
                        reps = prev?.reps,
                        durationSec = prev?.durationSec ?: spec.targetDurationSec,
                        distanceM = prev?.distanceM,
                        previous = prev,
                    ),
                )
            }
        }
        val suggestion = ProgressionEngine.suggest(spec.ref, previous, spec.sets, spec.target, increments, unit)
        return PlannedExercise(spec, sets, suggestion, previous)
    }

    private fun matchPrevious(done: List<SetEntry>, setNumber: Int, side: Side): SetEntry? =
        done.firstOrNull { it.setNumber == setNumber && it.side == side }
            ?: done.lastOrNull { it.side == side }
            ?: done.lastOrNull()

    /** Serie extra: copia los valores de la última serie del ejercicio. */
    fun extraSet(existing: List<SetEntry>, side: Side = Side.BOTH): SetEntry {
        val last = existing.lastOrNull { it.side == side } ?: existing.lastOrNull()
        val next = (existing.maxOfOrNull { it.setNumber } ?: 0) + 1
        return SetEntry(
            setNumber = next, side = side,
            weightKg = last?.weightKg, reps = last?.reps,
            durationSec = last?.durationSec, distanceM = last?.distanceM,
        )
    }
}

sealed interface CompletionResult {
    data class Ok(val set: SetEntry) : CompletionResult
    data class Invalid(val message: String) : CompletionResult
}

object SetRules {
    const val MAX_WEIGHT_KG = 1000.0
    const val MAX_REPS = 999
    const val MAX_DURATION_SEC = 6 * 3600
    const val MAX_DISTANCE_M = 500_000.0

    /**
     * Valida y completa una serie (registro en dos toques): si falta un dato que se puede
     * deducir sin inventar nada (repeticiones → mínimo del rango, duración → objetivo) se rellena.
     * El peso externo nunca se inventa.
     */
    fun complete(
        set: SetEntry,
        tracking: TrackingType,
        target: RepTarget,
        targetDurationSec: Int?,
    ): CompletionResult {
        var s = set
        when (tracking) {
            TrackingType.WEIGHT_REPS -> {
                if (s.weightKg == null) return CompletionResult.Invalid("Introduce el peso")
                if (s.reps == null) s = s.copy(reps = target.min)
            }
            TrackingType.BODYWEIGHT -> if (s.reps == null) s = s.copy(reps = target.min)
            TrackingType.DURATION -> {
                if (s.durationSec == null && targetDurationSec != null) s = s.copy(durationSec = targetDurationSec)
                if (s.durationSec == null) return CompletionResult.Invalid("Introduce la duración")
            }
            TrackingType.DISTANCE -> if (s.distanceM == null && s.durationSec == null) {
                return CompletionResult.Invalid("Introduce distancia o tiempo")
            }
        }
        validate(s)?.let { return CompletionResult.Invalid(it) }
        return CompletionResult.Ok(s.copy(completed = true))
    }

    fun validate(s: SetEntry): String? {
        s.weightKg?.let {
            if (it.isNaN() || it > MAX_WEIGHT_KG || it < -MAX_WEIGHT_KG) return "Peso fuera de rango"
        }
        s.reps?.let { if (it < 0 || it > MAX_REPS) return "Repeticiones fuera de rango" }
        s.durationSec?.let { if (it < 0 || it > MAX_DURATION_SEC) return "Duración fuera de rango" }
        s.distanceM?.let { if (it.isNaN() || it < 0 || it > MAX_DISTANCE_M) return "Distancia fuera de rango" }
        s.rpe?.let { if (it < 1.0 || it > 10.0) return "El RPE debe estar entre 1 y 10" }
        return null
    }
}

/**
 * Estado del temporizador de descanso. Se basa en marcas de tiempo absolutas,
 * por lo que sigue siendo correcto si la app pasa a segundo plano o se reinicia.
 */
data class RestTimerState(
    val totalSec: Int,
    val endsAtMs: Long? = null,
    val pausedRemainingMs: Long? = null,
) {
    val active: Boolean get() = endsAtMs != null || pausedRemainingMs != null
    val paused: Boolean get() = pausedRemainingMs != null

    fun remainingMs(nowMs: Long): Long = when {
        pausedRemainingMs != null -> pausedRemainingMs
        endsAtMs != null -> (endsAtMs - nowMs).coerceAtLeast(0)
        else -> 0
    }

    fun finished(nowMs: Long): Boolean = endsAtMs != null && nowMs >= endsAtMs

    fun pause(nowMs: Long): RestTimerState =
        if (endsAtMs != null) copy(endsAtMs = null, pausedRemainingMs = remainingMs(nowMs)) else this

    fun resume(nowMs: Long): RestTimerState =
        if (pausedRemainingMs != null) copy(endsAtMs = nowMs + pausedRemainingMs, pausedRemainingMs = null) else this

    fun restart(nowMs: Long): RestTimerState = start(totalSec, nowMs)

    fun skip(): RestTimerState = copy(endsAtMs = null, pausedRemainingMs = null)

    fun adjust(deltaSec: Int, nowMs: Long): RestTimerState {
        if (!active) return this
        val newRemaining = (remainingMs(nowMs) + deltaSec * 1000L).coerceAtLeast(1000)
        return if (paused) copy(pausedRemainingMs = newRemaining, totalSec = totalSec + deltaSec)
        else copy(endsAtMs = nowMs + newRemaining, totalSec = (totalSec + deltaSec).coerceAtLeast(1))
    }

    companion object {
        fun idle(totalSec: Int = 90) = RestTimerState(totalSec)
        fun start(totalSec: Int, nowMs: Long) = RestTimerState(totalSec, endsAtMs = nowMs + totalSec * 1000L)
    }
}
