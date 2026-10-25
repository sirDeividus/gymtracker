package com.gymtrack.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RoutineDto(
    val id: String,
    val name: String,
    val description: String = "",
    /** Máscara de días: bit 0 = lunes … bit 6 = domingo. */
    val daysMask: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int = 0,
)

@Serializable
data class RoutineExerciseDto(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetDurationSec: Int? = null,
    val startWeightKg: Double? = null,
    val restSec: Int = 90,
    val notes: String = "",
)

@Serializable
data class SessionDto(
    val id: String,
    val routineId: String? = null,
    val routineName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus,
    val notes: String = "",
)

@Serializable
data class SessionExerciseDto(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val tracking: TrackingType,
    val unilateral: Boolean = false,
    val position: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetDurationSec: Int? = null,
    val restSec: Int = 90,
    val notes: String = "",
    val skipped: Boolean = false,
    val discomfort: Boolean = false,
)

@Serializable
data class SetRecordDto(
    val id: String,
    val sessionExerciseId: String,
    val setNumber: Int,
    val side: Side = Side.BOTH,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val note: String = "",
    val completedAt: Long? = null,
    val type: SetType = SetType.NORMAL,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    /** Solo ejercicios personalizados; los de la biblioteca inicial se restauran por id estable. */
    val customExercises: List<Exercise> = emptyList(),
    val routines: List<RoutineDto> = emptyList(),
    val routineExercises: List<RoutineExerciseDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val sessionExercises: List<SessionExerciseDto> = emptyList(),
    val sets: List<SetRecordDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FORMAT = "gymtrack-backup"
        const val VERSION = 1
    }
}

data class BackupSummary(
    val routines: Int,
    val customExercises: Int,
    val sessions: Int,
    val sets: Int,
    val exportedAt: Long,
)

sealed interface BackupParseResult {
    data class Valid(val file: BackupFile, val summary: BackupSummary) : BackupParseResult
    data class Invalid(val errors: List<String>) : BackupParseResult
}

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Decodifica y valida. Nunca lanza: devuelve la lista de problemas para mostrársela al usuario. */
    fun parse(text: String): BackupParseResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return BackupParseResult.Invalid(listOf("El archivo no es una copia de seguridad válida de GymTrack."))
        } catch (e: IllegalArgumentException) {
            return BackupParseResult.Invalid(listOf("El archivo no es una copia de seguridad válida de GymTrack."))
        }
        val errors = BackupValidator.validate(file)
        if (errors.isNotEmpty()) return BackupParseResult.Invalid(errors)
        val summary = BackupSummary(
            routines = file.routines.size,
            customExercises = file.customExercises.size,
            sessions = file.sessions.count { it.status != SessionStatus.DISCARDED },
            sets = file.sets.count { it.completed },
            exportedAt = file.exportedAt,
        )
        return BackupParseResult.Valid(file, summary)
    }
}

object BackupValidator {
    fun validate(f: BackupFile): List<String> {
        val errors = mutableListOf<String>()
        if (f.format != BackupFile.FORMAT) errors += "Formato desconocido: ${f.format}"
        if (f.version > BackupFile.VERSION) {
            errors += "La copia es de una versión más reciente (${f.version}). Actualiza la aplicación."
        }
        if (f.version < 1) errors += "Versión de copia inválida: ${f.version}"

        fun <T> checkUnique(label: String, items: List<T>, id: (T) -> String) {
            val dup = items.groupBy(id).filterValues { it.size > 1 }.keys
            if (dup.isNotEmpty()) errors += "$label con identificadores repetidos: ${dup.take(3).joinToString()}"
            if (items.any { id(it).isBlank() }) errors += "$label con identificador vacío"
        }
        checkUnique("Ejercicios", f.customExercises) { it.id }
        checkUnique("Rutinas", f.routines) { it.id }
        checkUnique("Ejercicios de rutina", f.routineExercises) { it.id }
        checkUnique("Sesiones", f.sessions) { it.id }
        checkUnique("Ejercicios de sesión", f.sessionExercises) { it.id }
        checkUnique("Series", f.sets) { it.id }

        val routineIds = f.routines.map { it.id }.toSet()
        val sessionIds = f.sessions.map { it.id }.toSet()
        val sessionExerciseIds = f.sessionExercises.map { it.id }.toSet()

        if (f.routineExercises.any { it.routineId !in routineIds }) {
            errors += "Hay ejercicios de rutina que apuntan a una rutina inexistente"
        }
        if (f.sessionExercises.any { it.sessionId !in sessionIds }) {
            errors += "Hay ejercicios de sesión que apuntan a una sesión inexistente"
        }
        if (f.sets.any { it.sessionExerciseId !in sessionExerciseIds }) {
            errors += "Hay series que apuntan a un ejercicio de sesión inexistente"
        }
        if (f.routineExercises.any { it.sets !in 1..SessionPlanner.MAX_SETS || it.repMin < 1 || it.repMax < it.repMin }) {
            errors += "Hay ejercicios de rutina con series o repeticiones inválidas"
        }
        if (f.sessionExercises.any { it.targetSets < 0 || it.repMin < 1 || it.repMax < it.repMin }) {
            errors += "Hay ejercicios de sesión con objetivos inválidos"
        }
        if (f.sessions.any { it.endedAt != null && it.endedAt < it.startedAt }) {
            errors += "Hay sesiones que terminan antes de empezar"
        }
        if (f.sessions.count { it.status == SessionStatus.IN_PROGRESS } > 1) {
            errors += "Hay más de una sesión en curso"
        }
        val badSet = f.sets.firstOrNull { SetRules.validate(SetEntry(it.setNumber, it.side, it.weightKg, it.reps, it.durationSec, it.distanceM, it.rpe)) != null }
        if (badSet != null) errors += "Hay series con valores fuera de rango (${badSet.id})"
        return errors
    }
}
