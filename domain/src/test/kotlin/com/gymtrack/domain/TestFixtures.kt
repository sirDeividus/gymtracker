package com.gymtrack.domain

internal val bench = ExerciseRef("ex_bench_press", "Press de banca con barra", MuscleGroup.CHEST, Equipment.BARBELL, TrackingType.WEIGHT_REPS)
internal val squat = ExerciseRef("ex_back_squat", "Sentadilla con barra", MuscleGroup.QUADS, Equipment.BARBELL, TrackingType.WEIGHT_REPS)
internal val dumbbellPress = ExerciseRef("ex_dumbbell_bench_press", "Press con mancuernas", MuscleGroup.CHEST, Equipment.DUMBBELL, TrackingType.WEIGHT_REPS)
internal val pullUp = ExerciseRef("ex_pull_up", "Dominadas", MuscleGroup.BACK, Equipment.BODYWEIGHT, TrackingType.BODYWEIGHT)
internal val plank = ExerciseRef("ex_plank", "Plancha", MuscleGroup.CORE, Equipment.BODYWEIGHT, TrackingType.DURATION)

internal fun done(n: Int, kg: Double?, reps: Int?, rpe: Double? = null, durationSec: Int? = null) =
    SetEntry(n, weightKg = kg, reps = reps, durationSec = durationSec, rpe = rpe, completed = true)

internal fun perf(
    ref: ExerciseRef,
    vararg sets: SetEntry,
    at: Long = 1_000,
    discomfort: Boolean = false,
    session: String = "s$at",
) = ExercisePerformance(session, at, ref.id, ref.tracking, sets.toList(), discomfort)
