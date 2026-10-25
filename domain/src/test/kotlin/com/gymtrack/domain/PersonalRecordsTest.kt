package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsTest {
    private val history = listOf(perf(bench, done(1, 60.0, 8), done(2, 60.0, 8), done(3, 60.0, 6)))
    private val bests = ExerciseBests.from(history, TrackingType.WEIGHT_REPS)

    @Test fun `primera vez no genera records`() {
        val empty = ExerciseBests.empty(TrackingType.WEIGHT_REPS)
        assertTrue(empty.recordsFor(done(1, 100.0, 5)).isEmpty())
    }

    @Test fun `nuevo peso maximo`() {
        val r = bests.recordsFor(done(1, 62.5, 5))
        assertTrue(r.any { it.type == RecordType.WEIGHT && it.value == 62.5 && it.previous == 60.0 })
    }

    @Test fun `mas repeticiones con el mismo peso`() {
        val r = bests.recordsFor(done(1, 60.0, 9))
        assertEquals(listOf(RecordType.REPS, RecordType.ESTIMATED_1RM), r.map { it.type })
    }

    @Test fun `repetir lo mismo no es record`() {
        assertTrue(bests.recordsFor(done(1, 60.0, 8)).isEmpty())
    }

    @Test fun `peor rendimiento no es record`() {
        assertTrue(bests.recordsFor(done(1, 50.0, 8)).isEmpty())
    }

    @Test fun `una serie sin completar nunca es record`() {
        assertTrue(bests.recordsFor(SetEntry(1, weightKg = 100.0, reps = 10, completed = false)).isEmpty())
    }

    @Test fun `1RM estimado mejora con menos peso y muchas reps`() {
        // 60×8 → 76,0 ; 55×12 → 77,0
        val r = bests.recordsFor(done(1, 55.0, 12))
        assertNotNull(r.firstOrNull { it.type == RecordType.ESTIMATED_1RM })
        assertNull(r.firstOrNull { it.type == RecordType.WEIGHT })
    }

    @Test fun `plus acumula marcas dentro de la sesion`() {
        val s1 = done(1, 62.5, 5)
        assertTrue(bests.recordsFor(s1).isNotEmpty())
        val updated = bests.plus(s1)
        assertTrue(updated.recordsFor(done(2, 62.5, 5)).isEmpty())
    }

    @Test fun `epley`() {
        assertEquals(100.0, OneRepMax.estimate(100.0, 1)!!, 1e-9)
        assertEquals(133.333, OneRepMax.estimate(100.0, 10)!!, 1e-2)
        assertNull(OneRepMax.estimate(100.0, 20))
        assertNull(OneRepMax.estimate(0.0, 5))
    }

    @Test fun `records de peso corporal por repeticiones`() {
        val b = ExerciseBests.from(listOf(perf(pullUp, done(1, null, 8), done(2, null, 7))), TrackingType.BODYWEIGHT)
        assertEquals(listOf(RecordType.REPS), b.recordsFor(done(1, null, 9)).map { it.type })
        assertTrue(b.recordsFor(done(1, null, 8)).isEmpty())
    }

    @Test fun `records de duracion`() {
        val b = ExerciseBests.from(listOf(perf(plank, done(1, null, null, durationSec = 60))), TrackingType.DURATION)
        assertEquals(listOf(RecordType.DURATION), b.recordsFor(done(1, null, null, durationSec = 75)).map { it.type })
    }
}
