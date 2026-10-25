package com.gymtrack.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE custom = 1")
    suspend fun getCustom(): List<ExerciseEntity>

    /** Semilla: no pisa nada que ya exista. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ExerciseEntity>)

    @Upsert
    suspend fun upsert(item: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(items: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE custom = 1")
    suspend fun deleteCustom()

    /** Muestra u oculta la biblioteca ampliada (ids "ds_…") sin borrar nada: las rutinas y el historial la siguen resolviendo. */
    @Query("UPDATE exercises SET archived = :archived WHERE id LIKE 'ds\\_%' ESCAPE '\\'")
    suspend fun setExtendedArchived(archived: Boolean)

    @Query("DELETE FROM exercises WHERE id = :id AND custom = 1")
    suspend fun deleteCustomById(id: String)

    @Query("UPDATE exercises SET archived = :archived WHERE id = :id AND custom = 1")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query(
        "SELECT (SELECT COUNT(*) FROM routine_exercises WHERE exerciseId = :id) + " +
            "(SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :id)",
    )
    suspend fun usageCount(id: String): Int
}

data class RoutineCounts(val routineId: String, val exerciseCount: Int, val setCount: Int)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY sortOrder, createdAt")
    suspend fun getAll(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observe(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: String): RoutineEntity?

    @Upsert
    suspend fun upsert(item: RoutineEntity)

    @Upsert
    suspend fun upsertAll(items: List<RoutineEntity>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM routines")
    suspend fun maxSortOrder(): Int

    @Query("SELECT routineId, COUNT(*) AS exerciseCount, COALESCE(SUM(sets), 0) AS setCount FROM routine_exercises GROUP BY routineId")
    fun observeCounts(): Flow<List<RoutineCounts>>

    // ---- Ejercicios de rutina ----
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position")
    fun observeExercises(routineId: String): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position")
    suspend fun getExercises(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercises")
    suspend fun getAllRoutineExercises(): List<RoutineExerciseEntity>

    @Upsert
    suspend fun upsertExercise(item: RoutineExerciseEntity)

    @Upsert
    suspend fun upsertExercises(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    @Query("UPDATE routine_exercises SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM routine_exercises WHERE routineId = :routineId")
    suspend fun maxPosition(routineId: String): Int
}

/** Fila plana de una serie completada con los datos de su ejercicio y sesión (solo sesiones finalizadas). */
data class HistorySetRow(
    val sessionId: String,
    val sessionExerciseId: String,
    val routineId: String?,
    val performedAt: Long,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val tracking: String,
    val discomfort: Boolean,
    val setId: String,
    val setNumber: Int,
    val side: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceM: Double?,
    val rpe: Double?,
    val completedAt: Long?,
)

private const val HISTORY_SELECT = """
    SELECT s.id AS sessionId, se.id AS sessionExerciseId, s.routineId AS routineId, s.startedAt AS performedAt,
           se.exerciseId AS exerciseId, se.exerciseName AS exerciseName, se.primaryMuscle AS primaryMuscle,
           se.tracking AS tracking, se.discomfort AS discomfort,
           r.id AS setId, r.setNumber AS setNumber, r.side AS side, r.weightKg AS weightKg, r.reps AS reps,
           r.durationSec AS durationSec, r.distanceM AS distanceM, r.rpe AS rpe, r.completedAt AS completedAt
    FROM set_records r
    JOIN session_exercises se ON se.id = r.sessionExerciseId
    JOIN sessions s ON s.id = se.sessionId
    WHERE s.status = 'FINISHED' AND se.skipped = 0 AND r.completed = 1
"""

data class SessionExerciseCount(val sessionId: String, val exerciseCount: Int)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'FINISHED' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = 'FINISHED' ORDER BY startedAt DESC")
    suspend fun getFinished(): List<SessionEntity>

    @Insert
    suspend fun insert(item: SessionEntity)

    @Upsert
    suspend fun upsertAll(items: List<SessionEntity>)

    @Update
    suspend fun update(item: SessionEntity)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    // ---- Ejercicios de sesión ----
    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY position")
    fun observeExercises(sessionId: String): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getExercises(sessionId: String): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: String): SessionExerciseEntity?

    @Query("SELECT * FROM session_exercises WHERE sessionId IN (SELECT id FROM sessions WHERE status = 'FINISHED')")
    suspend fun getFinishedSessionExercises(): List<SessionExerciseEntity>

    @Upsert
    suspend fun upsertExercise(item: SessionExerciseEntity)

    @Upsert
    suspend fun upsertExercises(items: List<SessionExerciseEntity>)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun maxExercisePosition(sessionId: String): Int

    // ---- Series ----
    @Query(
        "SELECT r.* FROM set_records r JOIN session_exercises se ON se.id = r.sessionExerciseId " +
            "WHERE se.sessionId = :sessionId ORDER BY se.position, r.setNumber, r.side",
    )
    fun observeSets(sessionId: String): Flow<List<SetRecordEntity>>

    @Query(
        "SELECT r.* FROM set_records r JOIN session_exercises se ON se.id = r.sessionExerciseId " +
            "WHERE se.sessionId = :sessionId ORDER BY se.position, r.setNumber, r.side",
    )
    suspend fun getSets(sessionId: String): List<SetRecordEntity>

    @Query("SELECT * FROM set_records WHERE id = :id")
    suspend fun getSet(id: String): SetRecordEntity?

    @Query("SELECT * FROM set_records WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber, side")
    suspend fun getSetsFor(sessionExerciseId: String): List<SetRecordEntity>

    @Query(
        "SELECT r.* FROM set_records r JOIN session_exercises se ON se.id = r.sessionExerciseId " +
            "WHERE se.sessionId IN (SELECT id FROM sessions WHERE status = 'FINISHED')",
    )
    suspend fun getFinishedSets(): List<SetRecordEntity>

    @Upsert
    suspend fun upsertSet(item: SetRecordEntity)

    @Upsert
    suspend fun upsertSets(items: List<SetRecordEntity>)

    @Query("DELETE FROM set_records WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query(
        "SELECT se.sessionId AS sessionId, COUNT(DISTINCT se.id) AS exerciseCount FROM session_exercises se " +
            "JOIN set_records r ON r.sessionExerciseId = se.id WHERE r.completed = 1 AND se.skipped = 0 GROUP BY se.sessionId",
    )
    fun observeExerciseCounts(): Flow<List<SessionExerciseCount>>

    // ---- Historial plano (solo sesiones finalizadas) ----
    @Query("$HISTORY_SELECT AND se.exerciseId = :exerciseId ORDER BY s.startedAt, r.setNumber, r.side")
    suspend fun historyForExercise(exerciseId: String): List<HistorySetRow>

    @Query("$HISTORY_SELECT AND se.exerciseId = :exerciseId ORDER BY s.startedAt, r.setNumber, r.side")
    fun observeHistoryForExercise(exerciseId: String): Flow<List<HistorySetRow>>

    @Query("$HISTORY_SELECT AND s.startedAt < :before AND se.exerciseId IN (:exerciseIds) ORDER BY s.startedAt, r.completedAt")
    suspend fun historyBefore(before: Long, exerciseIds: List<String>): List<HistorySetRow>

    @Query("$HISTORY_SELECT ORDER BY s.startedAt, se.position, r.setNumber, r.side")
    fun observeAllHistory(): Flow<List<HistorySetRow>>

    @Query("$HISTORY_SELECT AND s.id = :sessionId ORDER BY se.position, r.setNumber, r.side")
    suspend fun historyForSession(sessionId: String): List<HistorySetRow>

    @Query("SELECT exerciseId, COUNT(*) AS uses FROM session_exercises GROUP BY exerciseId")
    fun observeUsage(): Flow<List<ExerciseUsage>>
}

data class ExerciseUsage(val exerciseId: String, val uses: Int)
