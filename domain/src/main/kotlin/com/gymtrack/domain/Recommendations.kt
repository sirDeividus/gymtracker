package com.gymtrack.domain

import java.time.DayOfWeek

data class RoutineInfo(
    val id: String,
    val name: String,
    /** Bit 0 = lunes … bit 6 = domingo. */
    val daysMask: Int,
    val lastUsedAt: Long?,
)

enum class RoutineReason { SCHEDULED_TODAY, LEAST_RECENT, NO_HISTORY }

data class RoutineRecommendation(val routine: RoutineInfo, val reason: RoutineReason)

object RoutineDays {
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or bit(d) }
    fun fromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.values().filter { contains(mask, it) }.toSet()
}

object RoutineRecommender {
    /** Rutina programada para hoy; si no hay, la que lleva más tiempo sin hacerse. Nunca inventa datos. */
    fun recommend(routines: List<RoutineInfo>, today: DayOfWeek): RoutineRecommendation? {
        if (routines.isEmpty()) return null
        val scheduled = routines.filter { RoutineDays.contains(it.daysMask, today) }
        if (scheduled.isNotEmpty()) {
            val pick = scheduled.minWith(compareBy(nullsFirst()) { it.lastUsedAt })
            return RoutineRecommendation(pick, RoutineReason.SCHEDULED_TODAY)
        }
        if (routines.all { it.lastUsedAt == null }) {
            return RoutineRecommendation(routines.first(), RoutineReason.NO_HISTORY)
        }
        val pick = routines.minWith(compareBy(nullsFirst()) { it.lastUsedAt })
        return RoutineRecommendation(pick, RoutineReason.LEAST_RECENT)
    }
}

/** Recalcula, en orden, qué series de una sesión superaron récords previos. */
object PrReplay {
    /** @param sets pares (id de serie, serie) en el orden en que se completaron. */
    fun replay(initial: ExerciseBests, sets: List<Pair<String, SetEntry>>): Map<String, List<PersonalRecord>> {
        var bests = initial
        val out = LinkedHashMap<String, List<PersonalRecord>>()
        for ((id, set) in sets) {
            val records = bests.recordsFor(set)
            if (records.isNotEmpty()) out[id] = records
            bests = bests.plus(set)
        }
        return out
    }
}
