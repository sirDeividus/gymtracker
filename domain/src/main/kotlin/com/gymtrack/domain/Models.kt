package com.gymtrack.domain

import kotlinx.serialization.Serializable

@Serializable
enum class MuscleGroup(val label: String, val lowerBody: Boolean = false) {
    CHEST("Pecho"),
    BACK("Espalda"),
    SHOULDERS("Hombros"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    FOREARMS("Antebrazos"),
    QUADS("Cuádriceps", true),
    HAMSTRINGS("Isquiotibiales", true),
    GLUTES("Glúteos", true),
    CALVES("Gemelos", true),
    CORE("Abdominales y core"),
    FULL_BODY("Cuerpo completo"),
}

@Serializable
enum class Equipment(val label: String) {
    BARBELL("Barra"),
    DUMBBELL("Mancuernas"),
    MACHINE("Máquina"),
    CABLE("Polea"),
    BODYWEIGHT("Peso corporal"),
    BANDS("Bandas elásticas"),
    OTHER("Otros"),
}

@Serializable
enum class MovementType(val label: String) {
    PUSH("Empuje"),
    PULL("Tirón"),
    SQUAT("Sentadilla"),
    HINGE("Bisagra de cadera"),
    LUNGE("Zancada"),
    ISOLATION("Aislamiento"),
    CORE("Core"),
    CARRY("Acarreo"),
    CARDIO("Cardio"),
}

/**
 * Método de registro de un ejercicio. Determina qué campos se muestran y cómo se evalúa el progreso.
 *  - WEIGHT_REPS: carga externa × repeticiones (barra, mancuernas, máquinas, poleas).
 *  - BODYWEIGHT: repeticiones; el peso es opcional (positivo = lastre, negativo = asistencia).
 *  - DURATION: tiempo (planchas, isometrías).
 *  - DISTANCE: distancia y/o tiempo (trabajo mixto).
 */
@Serializable
enum class TrackingType(val label: String) {
    WEIGHT_REPS("Peso y repeticiones"),
    BODYWEIGHT("Peso corporal"),
    DURATION("Duración"),
    DISTANCE("Distancia y tiempo"),
}

@Serializable
enum class SessionStatus { IN_PROGRESS, FINISHED, DISCARDED }

@Serializable
enum class Side(val short: String) { BOTH(""), LEFT("I"), RIGHT("D") }

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val movement: MovementType,
    val tracking: TrackingType,
    val unilateral: Boolean = false,
    val description: String = "",
    val cues: String = "",
    val custom: Boolean = false,
)

/** Un registro de serie, independiente de cómo se persista. */
data class SetEntry(
    val setNumber: Int,
    val side: Side = Side.BOTH,
    /** Carga externa en kg. En BODYWEIGHT: lastre (+) o asistencia (−). */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    val completed: Boolean = false,
)

/** Resultado de un ejercicio en una sesión finalizada (para historial y sugerencias). */
data class ExercisePerformance(
    val sessionId: String,
    val performedAt: Long,
    val exerciseId: String,
    val tracking: TrackingType,
    val sets: List<SetEntry>,
    /** El usuario marcó dolor o técnica deteriorada. */
    val discomfort: Boolean = false,
) {
    val completedSets: List<SetEntry> get() = sets.filter { it.completed }
}

data class RepTarget(val min: Int, val max: Int) {
    init {
        require(min in 1..max) { "Rango de repeticiones inválido: $min–$max" }
    }

    override fun toString(): String = if (min == max) "$min" else "$min–$max"
}
