package com.gymtrack.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Biblioteca ampliada basada en https://github.com/hasaneyldrm/exercises-dataset (MIT, © Hasan Emir Yıldırım).
 * Solo se usan los DATOS; el contenido multimedia del original es de Gym visual y no se incluye.
 */
@Serializable
data class DatasetEntry(
    val id: String,
    val name: String,
    val target: String = "",
    val equipment: String = "",
    val secondary: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
)

@Serializable
data class DatasetFile(
    val source: String = "",
    val license: String = "",
    val copyright: String = "",
    val mediaNotice: String = "",
    val version: Int = 1,
    val exercises: List<DatasetEntry> = emptyList(),
)

object ExerciseDataset {
    const val ID_PREFIX = "ds_"

    private val json = Json { ignoreUnknownKeys = true }

    /** Lee el asset. Devuelve null si el archivo no es válido (la app sigue con la biblioteca básica). */
    fun parse(text: String): DatasetFile? = try {
        json.decodeFromString(DatasetFile.serializer(), text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun toExercises(file: DatasetFile): List<Exercise> =
        file.exercises.filter { it.name.isNotBlank() && it.id.isNotBlank() }.map(::toExercise)

    fun toExercise(e: DatasetEntry): Exercise {
        val name = e.name.trim().replaceFirstChar { it.uppercase() }
        val lower = e.name.lowercase()
        val primary = muscleOf(e.target)
        val equipment = equipmentOf(e.equipment)
        val tracking = trackingOf(lower, e.target, e.equipment, equipment)
        return Exercise(
            id = ID_PREFIX + e.id,
            name = name,
            primaryMuscle = primary,
            secondaryMuscles = e.secondary.mapNotNull { secondaryMuscleOf(it) }.filter { it != primary }.distinct().take(4),
            equipment = equipment,
            movement = movementOf(lower, e.target, primary),
            tracking = tracking,
            unilateral = unilateralOf(lower),
            description = describe(e.steps),
            cues = e.steps.mapIndexed { i, s -> "${i + 1}. ${s.trim()}" }.joinToString("\n"),
            custom = false,
        )
    }

    fun muscleOf(target: String): MuscleGroup = when (target.lowercase().trim()) {
        "abs", "serratus anterior" -> MuscleGroup.CORE
        "pectorals" -> MuscleGroup.CHEST
        "biceps" -> MuscleGroup.BICEPS
        "triceps" -> MuscleGroup.TRICEPS
        "glutes", "abductors" -> MuscleGroup.GLUTES
        "delts" -> MuscleGroup.SHOULDERS
        "upper back", "lats", "traps", "spine", "levator scapulae" -> MuscleGroup.BACK
        "calves" -> MuscleGroup.CALVES
        "quads", "adductors" -> MuscleGroup.QUADS
        "hamstrings" -> MuscleGroup.HAMSTRINGS
        "forearms" -> MuscleGroup.FOREARMS
        else -> MuscleGroup.FULL_BODY // incluye "cardiovascular system"
    }

    fun secondaryMuscleOf(name: String): MuscleGroup? = when (name.lowercase().trim()) {
        "shoulders", "deltoids", "rear deltoids", "rotator cuff" -> MuscleGroup.SHOULDERS
        "hamstrings" -> MuscleGroup.HAMSTRINGS
        "forearms", "wrist flexors", "wrist extensors", "wrists", "hands", "grip muscles" -> MuscleGroup.FOREARMS
        "triceps" -> MuscleGroup.TRICEPS
        "biceps", "brachialis" -> MuscleGroup.BICEPS
        "quadriceps", "hip flexors", "inner thighs", "groin" -> MuscleGroup.QUADS
        "calves", "soleus", "ankles", "ankle stabilizers", "shins", "feet" -> MuscleGroup.CALVES
        "glutes" -> MuscleGroup.GLUTES
        "core", "obliques", "abdominals", "lower abs" -> MuscleGroup.CORE
        "chest", "upper chest" -> MuscleGroup.CHEST
        "rhomboids", "trapezius", "upper back", "traps", "back", "latissimus dorsi", "lats", "lower back" -> MuscleGroup.BACK
        else -> null
    }

    fun equipmentOf(raw: String): Equipment = when (raw.lowercase().trim()) {
        "body weight", "weighted", "assisted" -> Equipment.BODYWEIGHT
        "dumbbell" -> Equipment.DUMBBELL
        "cable" -> Equipment.CABLE
        "barbell", "ez barbell", "olympic barbell", "trap bar" -> Equipment.BARBELL
        "leverage machine", "smith machine", "sled machine", "skierg machine", "upper body ergometer",
        "stationary bike", "elliptical machine", "stepmill machine",
        -> Equipment.MACHINE
        "band", "resistance band" -> Equipment.BANDS
        else -> Equipment.OTHER
    }

    private val timedWords = Regex("stretch|plank|hold|hang\\b|wall sit|isometric|static|bridge hold|dead hang")

    fun trackingOf(lowerName: String, target: String, rawEquipment: String, equipment: Equipment): TrackingType = when {
        target.equals("cardiovascular system", ignoreCase = true) -> TrackingType.DISTANCE
        timedWords.containsMatchIn(lowerName) && !lowerName.contains("hanging") -> TrackingType.DURATION
        equipment == Equipment.BODYWEIGHT || equipment == Equipment.BANDS -> TrackingType.BODYWEIGHT
        else -> TrackingType.WEIGHT_REPS
    }.let { t ->
        // "weighted" y "assisted" son de peso corporal con lastre/asistencia.
        if (t == TrackingType.WEIGHT_REPS && rawEquipment.lowercase() in setOf("weighted", "assisted")) TrackingType.BODYWEIGHT else t
    }

    fun movementOf(lowerName: String, target: String, primary: MuscleGroup): MovementType {
        fun has(vararg w: String) = w.any { lowerName.contains(it) }
        return when {
            target.equals("cardiovascular system", ignoreCase = true) -> MovementType.CARDIO
            has("carry", "farmer", "suitcase") -> MovementType.CARRY
            primary == MuscleGroup.CORE -> MovementType.CORE
            has("pushdown", "push-down", "curl", "extension", "raise", "fly", "flye", "kickback", "shrug", "pullover", "pull-over", "crossover") -> MovementType.ISOLATION
            has("deadlift", "good morning", "hip thrust", "bridge", "swing", "hyperextension", "back extension") -> MovementType.HINGE
            has("lunge", "split squat", "step-up", "step up") -> MovementType.LUNGE
            has("squat", "leg press", "hack") -> MovementType.SQUAT
            has("press", "push-up", "push up", "pushup", "dip") -> MovementType.PUSH
            has("row", "pull", "chin-up", "chin up", "pulldown", "pull-down") -> MovementType.PULL
            else -> MovementType.ISOLATION
        }
    }

    fun unilateralOf(lowerName: String): Boolean =
        listOf("one arm", "single arm", "one leg", "single leg", "alternate", "unilateral", "one-arm", "single-arm")
            .any { lowerName.contains(it) }

    private fun describe(steps: List<String>): String {
        val first = steps.firstOrNull()?.trim().orEmpty()
        return if (first.length <= 140) first else first.take(137).trimEnd() + "…"
    }
}
