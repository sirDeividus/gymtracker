package com.gymtrack.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class PhotoPose(val label: String) {
    FRONT("Frente"),
    SIDE("Perfil"),
    BACK("Espalda"),
    OTHER("Otra"),
}

/** Diferencia entre dos fotos de progreso (la "antes" siempre es la más antigua). */
data class PhotoComparison(val days: Long, val weightDeltaKg: Double?)

object PhotoComparer {
    fun compare(aAt: Long, aWeightKg: Double?, bAt: Long, bWeightKg: Double?, zone: ZoneId): Pair<Boolean, PhotoComparison> {
        val swap = aAt > bAt
        val (beforeAt, beforeKg) = if (swap) bAt to bWeightKg else aAt to aWeightKg
        val (afterAt, afterKg) = if (swap) aAt to aWeightKg else bAt to bWeightKg
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(beforeAt).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(afterAt).atZone(zone).toLocalDate(),
        )
        val delta = if (beforeKg != null && afterKg != null) afterKg - beforeKg else null
        return swap to PhotoComparison(days, delta)
    }
}

/** Resumen del peso corporal registrado a lo largo del tiempo. */
data class WeightTrend(val latestKg: Double, val firstKg: Double, val deltaKg: Double, val entries: Int)

object BodyWeightStats {
    /** @param entries pares (instante, kg) en cualquier orden. Devuelve null si no hay datos. */
    fun trend(entries: List<Pair<Long, Double>>): WeightTrend? {
        if (entries.isEmpty()) return null
        val sorted = entries.sortedBy { it.first }
        return WeightTrend(sorted.last().second, sorted.first().second, sorted.last().second - sorted.first().second, sorted.size)
    }

    fun isValid(kg: Double): Boolean = !kg.isNaN() && kg in 20.0..500.0
}
