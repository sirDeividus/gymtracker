package com.gymtrack.data.repo

import androidx.room.withTransaction
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.RoutineCounts
import com.gymtrack.data.db.RoutineEntity
import com.gymtrack.data.db.RoutineExerciseEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoutineRepository(private val db: GymDatabase) {
    private val dao = db.routineDao()

    val routines: Flow<List<RoutineEntity>> = dao.observeAll()
    val counts: Flow<List<RoutineCounts>> = dao.observeCounts()

    fun observe(id: String): Flow<RoutineEntity?> = dao.observe(id)
    fun observeExercises(routineId: String): Flow<List<RoutineExerciseEntity>> = dao.observeExercises(routineId)

    suspend fun get(id: String) = dao.get(id)
    suspend fun getExercises(routineId: String) = dao.getExercises(routineId)

    /** Crea una rutina vacía ya persistida: a partir de aquí todo se guarda automáticamente. */
    suspend fun create(name: String = "", description: String = ""): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            dao.upsert(
                RoutineEntity(
                    id = id, name = name, description = description, daysMask = 0,
                    createdAt = now, updatedAt = now, sortOrder = dao.maxSortOrder() + 1,
                ),
            )
        }
        return id
    }

    suspend fun updateDetails(id: String, name: String, description: String, daysMask: Int) {
        val r = dao.get(id) ?: return
        dao.upsert(r.copy(name = name.take(60), description = description.take(300), daysMask = daysMask, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addExercise(routineId: String, exerciseId: String, defaults: RoutineExerciseEntity.() -> RoutineExerciseEntity = { this }): String {
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            val base = RoutineExerciseEntity(
                id = id, routineId = routineId, exerciseId = exerciseId,
                position = dao.maxPosition(routineId) + 1,
                sets = 3, repMin = 8, repMax = 12, targetDurationSec = null,
                startWeightKg = null, restSec = 90, notes = "",
            )
            dao.upsertExercise(base.defaults())
            touch(routineId)
        }
        return id
    }

    suspend fun updateExercise(item: RoutineExerciseEntity) {
        val fixed = item.copy(
            sets = item.sets.coerceIn(1, 20),
            repMin = item.repMin.coerceIn(1, 100),
            repMax = item.repMax.coerceIn(item.repMin.coerceIn(1, 100), 100),
            restSec = item.restSec.coerceIn(0, 1800),
            notes = item.notes.take(500),
        )
        db.withTransaction {
            dao.upsertExercise(fixed)
            touch(item.routineId)
        }
    }

    suspend fun removeExercise(routineId: String, id: String) {
        db.withTransaction {
            dao.deleteExercise(id)
            normalizePositions(routineId)
            touch(routineId)
        }
    }

    /** Persiste el nuevo orden completo (arrastrar y soltar). */
    suspend fun reorder(routineId: String, orderedIds: List<String>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.setPosition(id, index) }
            touch(routineId)
        }
    }

    /** Copia ejercicios (con su configuración) de una rutina a otra, al final. */
    suspend fun copyExercises(fromRoutineId: String, exerciseRowIds: Set<String>, toRoutineId: String): Int {
        var copied = 0
        db.withTransaction {
            var pos = dao.maxPosition(toRoutineId) + 1
            for (row in dao.getExercises(fromRoutineId)) {
                if (row.id !in exerciseRowIds) continue
                dao.upsertExercise(row.copy(id = UUID.randomUUID().toString(), routineId = toRoutineId, position = pos++))
                copied++
            }
            touch(toRoutineId)
        }
        return copied
    }

    suspend fun duplicate(id: String): String? {
        val src = dao.get(id) ?: return null
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.upsert(
                src.copy(
                    id = newId, name = "${src.name} (copia)".take(60),
                    createdAt = now, updatedAt = now, sortOrder = dao.maxSortOrder() + 1,
                ),
            )
            dao.upsertExercises(
                dao.getExercises(id).map { it.copy(id = UUID.randomUUID().toString(), routineId = newId) },
            )
        }
        return newId
    }

    /** Elimina la rutina. El historial NO se toca: las sesiones guardan su propia instantánea. */
    suspend fun delete(id: String) = dao.delete(id)

    private suspend fun normalizePositions(routineId: String) {
        dao.getExercises(routineId).forEachIndexed { index, e -> if (e.position != index) dao.setPosition(e.id, index) }
    }

    private suspend fun touch(routineId: String) {
        dao.get(routineId)?.let { dao.upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }
}
