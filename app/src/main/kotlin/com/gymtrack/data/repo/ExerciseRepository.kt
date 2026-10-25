package com.gymtrack.data.repo

import com.gymtrack.data.db.ExerciseDao
import com.gymtrack.data.db.toDomain
import com.gymtrack.data.db.toEntity
import com.gymtrack.domain.Equipment
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.ExerciseDataset
import com.gymtrack.domain.ExerciseLibrary
import com.gymtrack.domain.MovementType
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.TrackingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ExerciseRepository(private val dao: ExerciseDao) {
    /** Ejercicios visibles (sin archivados). */
    val exercises: Flow<List<Exercise>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    /** Todos, incluidos archivados: para resolver nombres de rutinas antiguas. */
    val allById: Flow<Map<String, Exercise>> =
        dao.observeAll().map { list -> list.map { it.toDomain() }.associateBy { it.id } }

    /** Inserta la biblioteca inicial sin tocar nada existente (idempotente). */
    suspend fun seedLibrary() {
        dao.insertIgnore(ExerciseLibrary.all.map { it.toEntity() })
    }

    /**
     * Biblioteca ampliada (datos MIT de hasaneyldrm/exercises-dataset; sin multimedia).
     * Idempotente: importa una sola vez por versión y después solo muestra/oculta.
     * @return la versión del dataset instalada, para guardarla en los ajustes.
     */
    suspend fun syncExtendedLibrary(enabled: Boolean, installedVersion: Int, load: () -> String?): Int {
        if (!enabled) {
            dao.setExtendedArchived(true)
            return installedVersion
        }
        var version = installedVersion
        if (installedVersion < DATASET_VERSION) {
            val file = load()?.let { ExerciseDataset.parse(it) } ?: return installedVersion
            file.let { ExerciseDataset.toExercises(it) }.chunked(300).forEach { chunk ->
                dao.insertIgnore(chunk.map { it.toEntity() })
            }
            version = DATASET_VERSION
        }
        dao.setExtendedArchived(false)
        return version
    }

    suspend fun get(id: String): Exercise? = dao.get(id)?.toDomain()

    fun validate(name: String): String? = when {
        name.isBlank() -> "Escribe un nombre"
        name.trim().length > 60 -> "El nombre es demasiado largo (máx. 60)"
        else -> null
    }

    suspend fun nameExists(name: String, exceptId: String? = null): Boolean =
        dao.getAll().any { it.id != exceptId && !it.archived && it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** Crea o edita un ejercicio PERSONALIZADO. Los de la biblioteca inicial no se modifican. */
    suspend fun saveCustom(
        id: String?,
        name: String,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>,
        equipment: Equipment,
        movement: MovementType,
        tracking: TrackingType,
        unilateral: Boolean,
        description: String,
        cues: String,
    ): Result<Exercise> {
        validate(name)?.let { return Result.failure(IllegalArgumentException(it)) }
        if (nameExists(name, id)) return Result.failure(IllegalArgumentException("Ya existe un ejercicio con ese nombre"))
        if (id != null) {
            val existing = dao.get(id)
            if (existing == null || !existing.custom) {
                return Result.failure(IllegalArgumentException("Solo se pueden editar ejercicios personalizados"))
            }
        }
        val ex = Exercise(
            id = id ?: "custom_${UUID.randomUUID()}",
            name = name.trim(),
            primaryMuscle = primary,
            secondaryMuscles = secondary.filter { it != primary }.distinct(),
            equipment = equipment,
            movement = movement,
            tracking = tracking,
            unilateral = unilateral,
            description = description.trim(),
            cues = cues.trim(),
            custom = true,
        )
        dao.upsert(ex.toEntity())
        return Result.success(ex)
    }

    /** Borra un ejercicio personalizado si nadie lo usa; si está en rutinas o historial, lo archiva. */
    suspend fun deleteCustom(id: String) {
        if (dao.usageCount(id) == 0) dao.deleteCustomById(id) else dao.setArchived(id, true)
    }

    companion object {
        /** Debe coincidir con "version" de assets/exercise_dataset.json. */
        const val DATASET_VERSION = 1
    }
}
