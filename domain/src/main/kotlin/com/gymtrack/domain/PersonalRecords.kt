package com.gymtrack.domain

enum class RecordType(val label: String) {
    WEIGHT("Peso máximo"),
    REPS("Más repeticiones"),
    ESTIMATED_1RM("1RM estimado"),
    DURATION("Mayor duración"),
    DISTANCE("Mayor distancia"),
}

data class PersonalRecord(val type: RecordType, val value: Double, val previous: Double?)

object OneRepMax {
    /** Fórmula de Epley. Solo es fiable con pocas repeticiones, por eso se limita a 12. */
    fun estimate(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0 || reps < 1 || reps > 12) return null
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }
}

/** Mejores marcas históricas de un ejercicio, construidas solo con series completadas. */
class ExerciseBests private constructor(private val sets: List<SetEntry>, private val tracking: TrackingType) {

    val isEmpty: Boolean get() = sets.isEmpty()

    fun maxWeight(): Double? = sets.mapNotNull { it.weightKg }.filter { it > 0 }.maxOrNull()
    fun maxReps(atOrAboveKg: Double): Int? =
        sets.filter { (it.weightKg ?: 0.0) >= atOrAboveKg }.mapNotNull { it.reps }.maxOrNull()
    fun maxReps(): Int? = sets.mapNotNull { it.reps }.maxOrNull()
    fun maxE1rm(): Double? = sets.mapNotNull { s ->
        val w = s.weightKg ?: return@mapNotNull null
        val r = s.reps ?: return@mapNotNull null
        OneRepMax.estimate(w, r)
    }.maxOrNull()
    fun maxDuration(): Int? = sets.mapNotNull { it.durationSec }.maxOrNull()
    fun maxDistance(): Double? = sets.mapNotNull { it.distanceM }.maxOrNull()

    fun plus(set: SetEntry): ExerciseBests =
        if (set.completed && set.type != SetType.WARMUP) ExerciseBests(sets + set, tracking) else this

    /**
     * Récords que supera [set]. Si no hay historial previo no se marca nada
     * (la primera marca de un ejercicio no es un "récord" que celebrar).
     */
    fun recordsFor(set: SetEntry): List<PersonalRecord> {
        if (!set.completed || set.type == SetType.WARMUP || isEmpty) return emptyList()
        val out = mutableListOf<PersonalRecord>()
        when (tracking) {
            TrackingType.WEIGHT_REPS -> {
                val w = set.weightKg ?: return emptyList()
                val r = set.reps ?: return emptyList()
                if (w <= 0 || r < 1) return emptyList()
                maxWeight()?.let { if (w > it) out += PersonalRecord(RecordType.WEIGHT, w, it) }
                val repsBest = maxReps(w)
                if (repsBest != null && r > repsBest && (maxWeight() ?: 0.0) >= w) {
                    out += PersonalRecord(RecordType.REPS, r.toDouble(), repsBest.toDouble())
                }
                val e1 = OneRepMax.estimate(w, r)
                val prevE1 = maxE1rm()
                if (e1 != null && prevE1 != null && e1 > prevE1 + 1e-6) {
                    out += PersonalRecord(RecordType.ESTIMATED_1RM, e1, prevE1)
                }
            }
            TrackingType.BODYWEIGHT -> {
                val r = set.reps ?: return emptyList()
                val load = set.weightKg ?: 0.0
                val best = maxReps(load) ?: sets.mapNotNull { it.reps }.maxOrNull()
                if (best != null && r > best) out += PersonalRecord(RecordType.REPS, r.toDouble(), best.toDouble())
                if (load > 0) {
                    maxWeight()?.let { if (load > it) out += PersonalRecord(RecordType.WEIGHT, load, it) }
                }
            }
            TrackingType.DURATION -> {
                val d = set.durationSec ?: return emptyList()
                maxDuration()?.let { if (d > it) out += PersonalRecord(RecordType.DURATION, d.toDouble(), it.toDouble()) }
            }
            TrackingType.DISTANCE -> {
                val d = set.distanceM ?: return emptyList()
                maxDistance()?.let { if (d > it) out += PersonalRecord(RecordType.DISTANCE, d, it) }
            }
        }
        return out
    }

    companion object {
        fun from(performances: List<ExercisePerformance>, tracking: TrackingType): ExerciseBests =
            ExerciseBests(performances.flatMap { it.workingSets }, tracking)

        fun empty(tracking: TrackingType) = ExerciseBests(emptyList(), tracking)
    }
}
