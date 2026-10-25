package com.gymtrack.data.db

import com.gymtrack.domain.Equipment
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.ExerciseRef
import com.gymtrack.domain.MovementType
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.RoutineExerciseSpec
import com.gymtrack.domain.SessionStatus
import com.gymtrack.domain.SetEntry
import com.gymtrack.domain.Side
import com.gymtrack.domain.TrackingType

/** Lectura tolerante: un valor desconocido (p. ej. de una versión futura) no tumba la app. */
internal inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    primaryMuscle = enumOr(primaryMuscle, MuscleGroup.FULL_BODY),
    secondaryMuscles = secondaryMuscles.split(",").filter { it.isNotBlank() }.map { enumOr(it, MuscleGroup.FULL_BODY) },
    equipment = enumOr(equipment, Equipment.OTHER),
    movement = enumOr(movement, MovementType.ISOLATION),
    tracking = enumOr(tracking, TrackingType.WEIGHT_REPS),
    unilateral = unilateral,
    description = description,
    cues = cues,
    custom = custom,
)

fun Exercise.toEntity(archived: Boolean = false) = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle.name,
    secondaryMuscles = secondaryMuscles.joinToString(",") { it.name },
    equipment = equipment.name,
    movement = movement.name,
    tracking = tracking.name,
    unilateral = unilateral,
    description = description,
    cues = cues,
    custom = custom,
    archived = archived,
)

fun RoutineExerciseEntity.toSpec(exercise: Exercise) = RoutineExerciseSpec(
    exercise = exercise,
    sets = sets,
    repMin = repMin,
    repMax = repMax,
    targetDurationSec = targetDurationSec,
    startWeightKg = startWeightKg,
    restSec = restSec,
    notes = notes,
)

fun SetRecordEntity.toEntry() = SetEntry(
    setNumber = setNumber,
    side = enumOr(side, Side.BOTH),
    weightKg = weightKg,
    reps = reps,
    durationSec = durationSec,
    distanceM = distanceM,
    rpe = rpe,
    completed = completed,
)

fun SessionExerciseEntity.toRef() = ExerciseRef(
    id = exerciseId,
    name = exerciseName,
    primaryMuscle = enumOr(primaryMuscle, MuscleGroup.FULL_BODY),
    equipment = enumOr(equipment, Equipment.OTHER),
    tracking = enumOr(tracking, TrackingType.WEIGHT_REPS),
)

fun SessionEntity.statusEnum(): SessionStatus = enumOr(status, SessionStatus.FINISHED)

fun HistorySetRow.toEntry() = SetEntry(
    setNumber = setNumber,
    side = enumOr(side, Side.BOTH),
    weightKg = weightKg,
    reps = reps,
    durationSec = durationSec,
    distanceM = distanceM,
    rpe = rpe,
    completed = true,
)
