package com.gymtrack.data.repo

import androidx.room.withTransaction
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.RoutineEntity
import com.gymtrack.data.db.RoutineExerciseEntity
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.data.db.SessionExerciseEntity
import com.gymtrack.data.db.SetRecordEntity
import com.gymtrack.data.db.toDomain
import com.gymtrack.data.db.toEntity
import com.gymtrack.data.settings.SettingsStore
import com.gymtrack.domain.BackupCodec
import com.gymtrack.domain.BackupFile
import com.gymtrack.domain.RoutineDto
import com.gymtrack.domain.RoutineExerciseDto
import com.gymtrack.domain.SessionDto
import com.gymtrack.domain.SessionExerciseDto
import com.gymtrack.domain.SessionStatus
import com.gymtrack.domain.SetRecordDto
import com.gymtrack.data.db.enumOr
import com.gymtrack.domain.Equipment
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.SetType
import com.gymtrack.domain.Side
import com.gymtrack.domain.TrackingType

class BackupRepository(
    private val db: GymDatabase,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Exporta rutinas, ejercicios personalizados y sesiones FINALIZADAS (las descartadas/en curso no se incluyen). */
    suspend fun exportJson(): String {
        val file = db.withTransaction {
            val finished = db.sessionDao().getFinished()
            val ids = finished.map { it.id }.toSet()
            val sessionExercises = db.sessionDao().getFinishedSessionExercises().filter { it.sessionId in ids }
            val seIds = sessionExercises.map { it.id }.toSet()
            BackupFile(
                exportedAt = now(),
                customExercises = db.exerciseDao().getCustom().map { it.toDomain() },
                routines = db.routineDao().getAll().map { it.toDto() },
                routineExercises = db.routineDao().getAllRoutineExercises().map { it.toDto() },
                sessions = finished.map { it.toDto() },
                sessionExercises = sessionExercises.map { it.toDto() },
                sets = db.sessionDao().getFinishedSets().filter { it.sessionExerciseId in seIds }.map { it.toDto() },
                settings = settings.exportMap(),
            )
        }
        return BackupCodec.encode(file)
    }

    /**
     * Reemplaza TODOS los datos por los de la copia, en una única transacción:
     * si algo falla, no se modifica nada. La validación se hace antes (BackupCodec.parse).
     */
    suspend fun replaceAllWith(file: BackupFile) {
        db.withTransaction {
            db.sessionDao().deleteAll() // cascada a ejercicios de sesión y series
            db.routineDao().deleteAll() // cascada a ejercicios de rutina
            db.exerciseDao().deleteCustom()
            db.exerciseDao().upsertAll(file.customExercises.map { it.copy(custom = true).toEntity() })
            db.routineDao().upsertAll(file.routines.map { it.toEntity() })
            db.routineDao().upsertExercises(file.routineExercises.map { it.toEntity() })
            db.sessionDao().upsertAll(file.sessions.map { it.toEntity() })
            db.sessionDao().upsertExercises(file.sessionExercises.map { it.toEntity() })
            db.sessionDao().upsertSets(file.sets.map { it.toEntity() })
        }
        settings.importMap(file.settings)
    }
}

private fun RoutineEntity.toDto() = RoutineDto(id, name, description, daysMask, createdAt, updatedAt, sortOrder)
private fun RoutineDto.toEntity() = RoutineEntity(id, name, description, daysMask, createdAt, updatedAt, sortOrder)

private fun RoutineExerciseEntity.toDto() =
    RoutineExerciseDto(id, routineId, exerciseId, position, sets, repMin, repMax, targetDurationSec, startWeightKg, restSec, notes)

private fun RoutineExerciseDto.toEntity() =
    RoutineExerciseEntity(id, routineId, exerciseId, position, sets, repMin, repMax, targetDurationSec, startWeightKg, restSec, notes)

private fun SessionEntity.toDto() = SessionDto(
    id, routineId, routineName, startedAt, endedAt, enumOr(status, SessionStatus.FINISHED), notes,
)

private fun SessionDto.toEntity() = SessionEntity(
    id = id, routineId = routineId, routineName = routineName, startedAt = startedAt, endedAt = endedAt,
    status = status.name, notes = notes,
)

private fun SessionExerciseEntity.toDto() = SessionExerciseDto(
    id, sessionId, exerciseId, exerciseName,
    enumOr(primaryMuscle, MuscleGroup.FULL_BODY), enumOr(equipment, Equipment.OTHER),
    enumOr(tracking, TrackingType.WEIGHT_REPS), unilateral, position, targetSets, repMin, repMax,
    targetDurationSec, restSec, notes, skipped, discomfort,
)

private fun SessionExerciseDto.toEntity() = SessionExerciseEntity(
    id = id, sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseName,
    primaryMuscle = primaryMuscle.name, equipment = equipment.name, tracking = tracking.name,
    unilateral = unilateral, position = position, targetSets = targetSets, repMin = repMin, repMax = repMax,
    targetDurationSec = targetDurationSec, restSec = restSec, notes = notes, skipped = skipped, discomfort = discomfort,
    suggestionKind = null, suggestionWeightKg = null, suggestionReps = null, suggestionDurationSec = null,
    suggestionReason = null, suggestionDismissed = true,
)

private fun SetRecordEntity.toDto() = SetRecordDto(
    id, sessionExerciseId, setNumber, enumOr(side, Side.BOTH), weightKg, reps, durationSec, distanceM, rpe, completed, note, completedAt,
    enumOr(setType, SetType.NORMAL),
)

private fun SetRecordDto.toEntity() = SetRecordEntity(
    id = id, sessionExerciseId = sessionExerciseId, setNumber = setNumber, side = side.name, weightKg = weightKg,
    reps = reps, durationSec = durationSec, distanceM = distanceM, rpe = rpe, completed = completed, note = note,
    completedAt = completedAt, prevWeightKg = null, prevReps = null, prevDurationSec = null, prevDistanceM = null,
    setType = type.name,
)
