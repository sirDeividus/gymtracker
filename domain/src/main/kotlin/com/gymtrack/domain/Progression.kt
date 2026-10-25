package com.gymtrack.domain

import kotlin.math.ceil
import kotlin.math.max

/** Incrementos de carga sugeridos (siempre en kg). Configurables por el usuario. */
data class IncrementSettings(
    val upperBodyKg: Double = 2.5,
    val lowerBodyKg: Double = 5.0,
    val dumbbellKg: Double = 2.0,
    val barbellKg: Double = 2.5,
    val machineKg: Double = 2.5,
    val bodyweightKg: Double = 2.5,
) {
    fun stepFor(exercise: ExerciseRef): Double = when (exercise.equipment) {
        Equipment.DUMBBELL -> dumbbellKg
        Equipment.MACHINE, Equipment.CABLE -> machineKg
        Equipment.BARBELL -> if (exercise.primaryMuscle.lowerBody) max(barbellKg, lowerBodyKg) else barbellKg
        Equipment.BODYWEIGHT -> bodyweightKg
        else -> if (exercise.primaryMuscle.lowerBody) lowerBodyKg else upperBodyKg
    }

    companion object {
        fun defaultsFor(unit: WeightUnit): IncrementSettings = when (unit) {
            WeightUnit.KG -> IncrementSettings()
            WeightUnit.LB -> IncrementSettings(
                upperBodyKg = 5 * WeightUnit.LB.kgPerUnit,
                lowerBodyKg = 10 * WeightUnit.LB.kgPerUnit,
                dumbbellKg = 5 * WeightUnit.LB.kgPerUnit,
                barbellKg = 5 * WeightUnit.LB.kgPerUnit,
                machineKg = 5 * WeightUnit.LB.kgPerUnit,
                bodyweightKg = 5 * WeightUnit.LB.kgPerUnit,
            )
        }
    }
}

/** Datos mínimos del ejercicio que necesita el motor de progresión. */
data class ExerciseRef(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val tracking: TrackingType,
)

enum class SuggestionKind {
    /** No hay historial: el usuario introduce una carga inicial. */
    FIRST_TIME,
    /** Subir carga (o reducir asistencia / añadir lastre). */
    INCREASE,
    /** Mantener carga y buscar más repeticiones. */
    HOLD,
    /** Mantener carga y valorar si es demasiado exigente. */
    HOLD_REVIEW,
    /** Mantener por esfuerzo excesivo, dolor o técnica. */
    HOLD_EFFORT,
    /** Peso corporal sin carga: más repeticiones / variante / lastre. */
    MORE_REPS,
    /** Ejercicio cronometrado: más tiempo. */
    MORE_TIME,
}

data class Suggestion(
    val kind: SuggestionKind,
    /** Carga recomendada (kg) para la próxima sesión; null si no aplica. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    /** Motivo legible y breve. */
    val reason: String,
) {
    /** Solo las sugerencias que proponen un cambio concreto se pueden aplicar con un toque. */
    val actionable: Boolean
        get() = kind == SuggestionKind.INCREASE || kind == SuggestionKind.MORE_TIME
}

/**
 * Sugerencias de progresión basadas únicamente en el último rendimiento real.
 * Nunca modifica datos: devuelve una propuesta que el usuario puede ignorar o editar.
 */
object ProgressionEngine {
    const val MIN_PERCENT = 0.02
    const val MAX_RPE_FOR_INCREASE = 9.0
    const val DURATION_STEP_SEC = 5

    fun suggest(
        exercise: ExerciseRef,
        last: ExercisePerformance?,
        targetSets: Int,
        target: RepTarget,
        increments: IncrementSettings,
        unit: WeightUnit,
    ): Suggestion {
        val done = last?.workingSets.orEmpty()
        if (last == null || done.isEmpty()) {
            return Suggestion(
                SuggestionKind.FIRST_TIME,
                reason = "Primera vez con este ejercicio: empieza con una carga cómoda y registra tus series.",
            )
        }
        return when (exercise.tracking) {
            TrackingType.WEIGHT_REPS -> weightReps(exercise, last, done, targetSets, target, increments, unit)
            TrackingType.BODYWEIGHT -> bodyweight(exercise, last, done, targetSets, target, increments, unit)
            TrackingType.DURATION -> duration(last, done, targetSets)
            TrackingType.DISTANCE -> Suggestion(
                SuggestionKind.HOLD,
                reason = "Intenta mejorar la distancia o el tiempo respecto a la sesión anterior, de forma gradual.",
            )
        }
    }

    /** Incremento: múltiplo del paso disponible que ronda el 2 %, con el paso como mínimo. */
    fun incrementFor(weightKg: Double, stepKg: Double): Double {
        require(stepKg > 0) { "El incremento debe ser positivo" }
        val k = max(1, ceil(weightKg * MIN_PERCENT / stepKg - 1e-9).toInt())
        return k * stepKg
    }

    private fun avgRpe(sets: List<SetEntry>): Double? =
        sets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()

    private fun weightReps(
        ex: ExerciseRef,
        last: ExercisePerformance,
        done: List<SetEntry>,
        targetSets: Int,
        target: RepTarget,
        inc: IncrementSettings,
        unit: WeightUnit,
    ): Suggestion {
        val weight = done.maxOf { it.weightKg ?: 0.0 }
        val w = Formatting.weight(weight, unit)
        val repsList = done.map { it.reps ?: 0 }
        val summary = "${done.size} × ${repsList.joinToString("/")} con $w"
        val rpe = avgRpe(done)

        if (last.discomfort) {
            return Suggestion(
                SuggestionKind.HOLD_EFFORT, weight,
                reason = "Marcaste dolor o técnica deteriorada ($summary). Mantén $w hasta sentirte seguro.",
            )
        }
        if (rpe != null && rpe >= 9.5) {
            return Suggestion(
                SuggestionKind.HOLD_EFFORT, weight,
                reason = "El esfuerzo fue muy alto (RPE ${Formatting.number(rpe)}). Mantén $w antes de subir.",
            )
        }
        if (done.size < targetSets) {
            return Suggestion(
                SuggestionKind.HOLD, weight,
                reason = "Completaste ${done.size} de $targetSets series. Mantén $w y completa todas.",
            )
        }
        if (repsList.any { it < target.min }) {
            return Suggestion(
                SuggestionKind.HOLD_REVIEW, weight,
                reason = "Alguna serie quedó por debajo de ${target.min} reps ($summary). " +
                    "Mantén $w o valora si resulta demasiado exigente.",
            )
        }
        if (repsList.all { it >= target.max }) {
            if (rpe != null && rpe >= MAX_RPE_FOR_INCREASE) {
                return Suggestion(
                    SuggestionKind.HOLD_EFFORT, weight,
                    reason = "Completaste $summary, pero con esfuerzo alto (RPE ${Formatting.number(rpe)}). " +
                        "Repite $w y sube cuando se sienta más controlado.",
                )
            }
            val step = inc.stepFor(ex)
            val next = weight + incrementFor(weight, step)
            return Suggestion(
                SuggestionKind.INCREASE, next, reps = target.min,
                reason = "Completaste $summary. Si el esfuerzo fue adecuado, puedes probar " +
                    "${Formatting.weight(next, unit)} en la próxima sesión.",
            )
        }
        return Suggestion(
            SuggestionKind.HOLD, weight, reps = target.max,
            reason = "Hiciste $summary. Mantén $w y busca llegar a ${target.max} reps en todas las series.",
        )
    }

    private fun bodyweight(
        ex: ExerciseRef,
        last: ExercisePerformance,
        done: List<SetEntry>,
        targetSets: Int,
        target: RepTarget,
        inc: IncrementSettings,
        unit: WeightUnit,
    ): Suggestion {
        val repsList = done.map { it.reps ?: 0 }
        val load = done.maxOf { it.weightKg ?: 0.0 }
        val summary = "${done.size} × ${repsList.joinToString("/")}"
        val rpe = avgRpe(done)
        if (last.discomfort || (rpe != null && rpe >= 9.5)) {
            return Suggestion(
                SuggestionKind.HOLD_EFFORT, load.takeIf { it != 0.0 },
                reason = "Repite el mismo nivel ($summary): el esfuerzo o la técnica indican que aún no toca avanzar.",
            )
        }
        if (done.size < targetSets || repsList.any { it < target.min }) {
            return Suggestion(
                SuggestionKind.HOLD, load.takeIf { it != 0.0 }, reps = target.min,
                reason = "Hiciste $summary. Busca completar $targetSets series de al menos ${target.min} reps.",
            )
        }
        if (repsList.all { it >= target.max }) {
            val step = inc.stepFor(ex)
            return when {
                load < 0 -> {
                    val next = minOf(0.0, load + step)
                    Suggestion(
                        SuggestionKind.INCREASE, next,
                        reason = "Completaste $summary con ${Formatting.weight(-load, unit)} de asistencia. " +
                            "Prueba con menos asistencia: ${Formatting.bodyweightLoad(next, unit)}.",
                    )
                }
                load > 0 -> {
                    val next = load + step
                    Suggestion(
                        SuggestionKind.INCREASE, next,
                        reason = "Completaste $summary con +${Formatting.weight(load, unit)}. " +
                            "Puedes probar +${Formatting.weight(next, unit)}.",
                    )
                }
                else -> Suggestion(
                    SuggestionKind.MORE_REPS,
                    reason = "Completaste $summary. Para progresar: más repeticiones, más tiempo bajo tensión, " +
                        "una variante más difícil o añadir peso (+${Formatting.weight(step, unit)}).",
                )
            }
        }
        return Suggestion(
            SuggestionKind.MORE_REPS, load.takeIf { it != 0.0 }, reps = target.max,
            reason = "Hiciste $summary. Intenta sumar repeticiones hasta llegar a ${target.max} en todas las series.",
        )
    }

    private fun duration(last: ExercisePerformance, done: List<SetEntry>, targetSets: Int): Suggestion {
        val times = done.map { it.durationSec ?: 0 }
        val best = times.min()
        val summary = times.joinToString("/") { Formatting.duration(it) }
        if (last.discomfort) {
            return Suggestion(
                SuggestionKind.HOLD_EFFORT, durationSec = best,
                reason = "Marcaste dolor o técnica deteriorada. Repite ${Formatting.duration(best)} con buena forma.",
            )
        }
        if (done.size < targetSets) {
            return Suggestion(
                SuggestionKind.HOLD, durationSec = best,
                reason = "Completaste ${done.size} de $targetSets series ($summary). Intenta terminar todas.",
            )
        }
        val next = best + DURATION_STEP_SEC
        return Suggestion(
            SuggestionKind.MORE_TIME, durationSec = next,
            reason = "Aguantaste $summary en todas las series. Prueba con ${Formatting.duration(next)} la próxima vez.",
        )
    }
}
