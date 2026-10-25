package com.gymtrack.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Biblioteca de ejercicios (inicial + personalizados). Nunca se borra un ejercicio usado: se archiva. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val equipment: String,
    val movement: String,
    val tracking: String,
    val unilateral: Boolean,
    val description: String,
    val cues: String,
    val custom: Boolean,
    val archived: Boolean = false,
)

/** Definición de una rutina (plantilla editable). Separada de los resultados históricos. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val daysMask: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetDurationSec: Int?,
    val startWeightKg: Double?,
    val restSec: Int,
    val notes: String,
)

/**
 * Sesión de entrenamiento. NO tiene clave foránea a la rutina: borrar o editar la rutina
 * no toca el historial. [routineName] es una instantánea.
 */
@Entity(tableName = "sessions", indices = [Index("status"), Index("startedAt")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val routineId: String?,
    val routineName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: String,
    val notes: String,
    /** Estado del temporizador de descanso (marcas absolutas, sobrevive a que Android mate el proceso). */
    val restTotalSec: Int = 90,
    val restEndsAt: Long? = null,
    val restPausedRemainingMs: Long? = null,
)

/** Ejercicio dentro de una sesión: instantánea de todo lo necesario para reconstruir lo realizado. */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val equipment: String,
    val tracking: String,
    val unilateral: Boolean,
    val position: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetDurationSec: Int?,
    val restSec: Int,
    val notes: String,
    val skipped: Boolean,
    val discomfort: Boolean,
    // Sugerencia de progresión calculada al empezar (estable durante la sesión).
    val suggestionKind: String?,
    val suggestionWeightKg: Double?,
    val suggestionReps: Int?,
    val suggestionDurationSec: Int?,
    val suggestionReason: String?,
    val suggestionDismissed: Boolean,
)

@Entity(
    tableName = "set_records",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetRecordEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val setNumber: Int,
    val side: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceM: Double?,
    val rpe: Double?,
    val completed: Boolean,
    val note: String,
    val completedAt: Long?,
    // Instantánea de la serie equivalente de la última sesión válida (columna ANTERIOR).
    val prevWeightKg: Double?,
    val prevReps: Int?,
    val prevDurationSec: Int?,
    val prevDistanceM: Double?,
)
