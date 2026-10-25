package com.gymtrack.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class SessionTotals(
    val completedSets: Int,
    /** Σ peso × reps de ejercicios con carga externa. Métrica orientativa. */
    val volumeKg: Double,
    /** Repeticiones de ejercicios de peso corporal (no se mezclan con el volumen). */
    val bodyweightReps: Int,
    val timedSeconds: Int,
    val distanceM: Double,
)

/** Serie completada con los datos de su ejercicio (instantánea). */
data class LoggedSet(
    val exerciseId: String,
    val exerciseName: String,
    val muscle: MuscleGroup,
    val tracking: TrackingType,
    val set: SetEntry,
)

object Stats {
    fun totals(sets: List<LoggedSet>): SessionTotals {
        var count = 0
        var volume = 0.0
        var bwReps = 0
        var seconds = 0
        var distance = 0.0
        for (l in sets) {
            val s = l.set
            if (!s.completed) continue
            count++
            when (l.tracking) {
                TrackingType.WEIGHT_REPS -> volume += (s.weightKg ?: 0.0) * (s.reps ?: 0)
                TrackingType.BODYWEIGHT -> bwReps += s.reps ?: 0
                TrackingType.DURATION -> seconds += s.durationSec ?: 0
                TrackingType.DISTANCE -> {
                    distance += s.distanceM ?: 0.0
                    seconds += s.durationSec ?: 0
                }
            }
        }
        return SessionTotals(count, volume, bwReps, seconds, distance)
    }

    fun setsPerMuscle(sets: List<LoggedSet>): Map<MuscleGroup, Int> =
        sets.filter { it.set.completed }.groupingBy { it.muscle }.eachCount()

    fun weekStart(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Número de sesiones por semana (clave: lunes de la semana). */
    fun sessionsPerWeek(startTimes: List<Long>, zone: ZoneId): Map<LocalDate, Int> =
        startTimes.groupingBy { weekStart(it, zone) }.eachCount().toSortedMap()

    fun sessionsInWeekOf(nowMs: Long, startTimes: List<Long>, zone: ZoneId): Int {
        val ws = weekStart(nowMs, zone)
        return startTimes.count { weekStart(it, zone) == ws }
    }

    /** Días de la semana (lun–dom) en los que hubo al menos una sesión en la semana de [nowMs]. */
    fun trainedDaysInWeekOf(nowMs: Long, startTimes: List<Long>, zone: ZoneId): Set<DayOfWeek> {
        val ws = weekStart(nowMs, zone)
        return startTimes.filter { weekStart(it, zone) == ws }
            .map { Instant.ofEpochMilli(it).atZone(zone).dayOfWeek }.toSet()
    }

    /**
     * Racha: semanas consecutivas con al menos una sesión. La semana en curso todavía puede salvar la racha:
     * si aún no has entrenado esta semana, se cuenta desde la anterior.
     */
    fun weekStreak(startTimes: List<Long>, nowMs: Long, zone: ZoneId): Int {
        val weeks = startTimes.map { weekStart(it, zone) }.toSet()
        var cursor = weekStart(nowMs, zone)
        if (cursor !in weeks) cursor = cursor.minusWeeks(1)
        var n = 0
        while (cursor in weeks) {
            n++
            cursor = cursor.minusWeeks(1)
        }
        return n
    }

    fun epochDay(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}

/** Punto de una serie temporal de un ejercicio (una sesión). */
data class ExercisePoint(
    val performedAt: Long,
    val topWeightKg: Double?,
    val bestReps: Int?,
    val bestE1rmKg: Double?,
    val volumeKg: Double,
    val bestDurationSec: Int?,
)

object ExerciseEvolution {
    fun points(performances: List<ExercisePerformance>): List<ExercisePoint> =
        performances.sortedBy { it.performedAt }.mapNotNull { p ->
            val done = p.completedSets
            if (done.isEmpty()) return@mapNotNull null
            ExercisePoint(
                performedAt = p.performedAt,
                topWeightKg = done.mapNotNull { it.weightKg }.maxOrNull(),
                bestReps = done.mapNotNull { it.reps }.maxOrNull(),
                bestE1rmKg = done.mapNotNull { s ->
                    val w = s.weightKg ?: return@mapNotNull null
                    val r = s.reps ?: return@mapNotNull null
                    OneRepMax.estimate(w, r)
                }.maxOrNull(),
                volumeKg = if (p.tracking == TrackingType.WEIGHT_REPS) {
                    done.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
                } else 0.0,
                bestDurationSec = done.mapNotNull { it.durationSec }.maxOrNull(),
            )
        }
}
