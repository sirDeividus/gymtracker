package com.gymtrack.data.repo

import com.gymtrack.data.db.HistorySetRow
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.data.db.SessionExerciseEntity
import com.gymtrack.data.db.SetRecordEntity
import com.gymtrack.data.db.enumOr
import com.gymtrack.data.db.toEntry
import com.gymtrack.domain.ExercisePerformance
import com.gymtrack.domain.PersonalRecord
import com.gymtrack.domain.SessionTotals
import com.gymtrack.domain.TrackingType

data class ActiveExercise(val entity: SessionExerciseEntity, val sets: List<SetRecordEntity>) {
    val completedSets: Int get() = sets.count { it.completed }
    val finished: Boolean get() = entity.skipped || (sets.isNotEmpty() && sets.all { it.completed })
}

data class ActiveWorkout(val session: SessionEntity, val exercises: List<ActiveExercise>) {
    val totalSets: Int get() = exercises.filter { !it.entity.skipped }.sumOf { it.sets.size }
    val completedSets: Int get() = exercises.filter { !it.entity.skipped }.sumOf { it.completedSets }
    val finishedExercises: Int get() = exercises.count { it.finished }
    val progress: Float get() = if (totalSets == 0) 0f else completedSets.toFloat() / totalSets
}

sealed interface CompleteOutcome {
    data class Done(val restStarted: Boolean) : CompleteOutcome
    data class Invalid(val message: String) : CompleteOutcome
}

enum class FinishOutcome { FINISHED, NO_COMPLETED_SETS, NOT_ACTIVE }

data class SessionPr(val exerciseId: String, val exerciseName: String, val record: PersonalRecord, val tracking: TrackingType)

data class PreviousComparison(
    val previousDate: Long,
    val previousVolumeKg: Double,
    val previousSets: Int,
)

data class SessionSummary(
    val session: SessionEntity,
    val durationSec: Long,
    val exerciseCount: Int,
    val totals: SessionTotals,
    val records: List<SessionPr>,
    val previous: PreviousComparison?,
)

/** Agrupa filas planas del historial en rendimientos por ejercicio y sesión, ordenados por fecha. */
internal fun groupPerformances(rows: List<HistorySetRow>): List<ExercisePerformance> =
    rows.groupBy { it.sessionExerciseId }.values.map { list ->
        val first = list.first()
        ExercisePerformance(
            sessionId = first.sessionId,
            performedAt = first.performedAt,
            exerciseId = first.exerciseId,
            tracking = enumOr(first.tracking, TrackingType.WEIGHT_REPS),
            sets = list.map { it.toEntry() },
            discomfort = first.discomfort,
        )
    }.sortedBy { it.performedAt }


/** Series completadas de una sesión como datos de estadística (peso × reps, series por músculo…). */
fun ActiveWorkout.loggedSets(): List<com.gymtrack.domain.LoggedSet> =
    exercises.filter { !it.entity.skipped }.flatMap { ex ->
        val tracking = enumOr(ex.entity.tracking, TrackingType.WEIGHT_REPS)
        val muscle = enumOr(ex.entity.primaryMuscle, com.gymtrack.domain.MuscleGroup.FULL_BODY)
        ex.sets.filter { it.completed }.map {
            com.gymtrack.domain.LoggedSet(ex.entity.exerciseId, ex.entity.exerciseName, muscle, tracking, it.toEntry())
        }
    }
