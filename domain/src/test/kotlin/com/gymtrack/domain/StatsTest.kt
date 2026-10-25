package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class StatsTest {
    private fun logged(ref: ExerciseRef, set: SetEntry) = LoggedSet(ref.id, ref.name, ref.primaryMuscle, ref.tracking, set)

    @Test fun `el volumen solo suma peso por repeticiones de ejercicios con carga externa`() {
        val sets = listOf(
            logged(bench, done(1, 40.0, 10)),
            logged(bench, done(2, 40.0, 8)),
            logged(bench, SetEntry(3, weightKg = 100.0, reps = 10, completed = false)),
            logged(pullUp, done(1, 20.0, 5)), // lastre: no se mezcla con el volumen
            logged(plank, done(1, null, null, durationSec = 60)),
        )
        val t = Stats.totals(sets)
        assertEquals(720.0, t.volumeKg, 1e-9)
        assertEquals(4, t.completedSets)
        assertEquals(5, t.bodyweightReps)
        assertEquals(60, t.timedSeconds)
    }

    @Test fun `series por grupo muscular ignora pendientes`() {
        val sets = listOf(
            logged(bench, done(1, 40.0, 10)),
            logged(bench, SetEntry(2, weightKg = 40.0, reps = 10)),
            logged(squat, done(1, 60.0, 10)),
        )
        val m = Stats.setsPerMuscle(sets)
        assertEquals(1, m[MuscleGroup.CHEST])
        assertEquals(1, m[MuscleGroup.QUADS])
    }

    @Test fun `semana empieza en lunes`() {
        val zone = ZoneId.of("UTC")
        val wed = LocalDate.of(2026, 10, 21).atStartOfDay(zone).toInstant().toEpochMilli() // miércoles
        assertEquals(LocalDate.of(2026, 10, 19), Stats.weekStart(wed, zone))
        val sunday = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 19), Stats.weekStart(sunday, zone))
    }

    @Test fun `sesiones por semana`() {
        val zone = ZoneOffset.UTC
        fun day(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = day(22)
        val starts = listOf(day(19), day(21), day(22), day(12))
        assertEquals(3, Stats.sessionsInWeekOf(now, starts, zone))
        assertEquals(mapOf(LocalDate.of(2026, 10, 12) to 1, LocalDate.of(2026, 10, 19) to 3), Stats.sessionsPerWeek(starts, zone))
    }

    @Test fun `evolucion de un ejercicio ordena por fecha y omite sesiones sin series completadas`() {
        val ps = listOf(
            perf(bench, done(1, 42.5, 8), at = 2_000),
            perf(bench, done(1, 40.0, 10), done(2, 40.0, 9), at = 1_000),
            perf(bench, SetEntry(1, weightKg = 50.0, reps = 8), at = 3_000),
        )
        val pts = ExerciseEvolution.points(ps)
        assertEquals(listOf(1_000L, 2_000L), pts.map { it.performedAt })
        assertEquals(760.0, pts[0].volumeKg, 1e-9)
        assertEquals(42.5, pts[1].topWeightKg!!, 1e-9)
    }
}
