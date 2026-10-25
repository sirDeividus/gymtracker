package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlannerTest {
    private val lib = ExerciseLibrary.byId
    private fun spec(id: String, sets: Int = 3, min: Int = 8, max: Int = 10, start: Double? = null, dur: Int? = null) =
        RoutineExerciseSpec(lib.getValue(id), sets, min, max, dur, start)

    @Test fun `crea exactamente las series configuradas`() {
        val p = SessionPlanner.plan(spec("ex_bench_press", sets = 4), null, IncrementSettings(), WeightUnit.KG)
        assertEquals(4, p.sets.size)
        assertEquals(listOf(1, 2, 3, 4), p.sets.map { it.setNumber })
        assertEquals(SuggestionKind.FIRST_TIME, p.suggestion.kind)
        assertTrue(p.sets.all { it.previous == null })
    }

    @Test fun `repite los valores de la ultima sesion del mismo ejercicio`() {
        val prev = perf(bench, done(1, 40.0, 10), done(2, 40.0, 9), done(3, 40.0, 8))
        val p = SessionPlanner.plan(spec("ex_bench_press"), prev, IncrementSettings(), WeightUnit.KG)
        assertEquals(listOf(10, 9, 8), p.sets.map { it.reps })
        assertEquals(listOf(40.0, 40.0, 40.0), p.sets.map { it.weightKg })
        assertEquals(9, p.sets[1].previous!!.reps)
    }

    @Test fun `la sugerencia no se aplica sola`() {
        val prev = perf(bench, done(1, 40.0, 10), done(2, 40.0, 10), done(3, 40.0, 10))
        val p = SessionPlanner.plan(spec("ex_bench_press"), prev, IncrementSettings(), WeightUnit.KG)
        assertEquals(42.5, p.suggestion.weightKg!!, 1e-9)
        assertTrue(p.sets.all { it.weightKg == 40.0 })
    }

    @Test fun `series adicionales sobre el historial reutilizan la ultima serie`() {
        val prev = perf(bench, done(1, 40.0, 10), done(2, 40.0, 9))
        val p = SessionPlanner.plan(spec("ex_bench_press", sets = 4), prev, IncrementSettings(), WeightUnit.KG)
        assertEquals(4, p.sets.size)
        assertEquals(9, p.sets[3].reps)
        assertNull(p.sets[3].previous?.takeIf { it.setNumber == 4 })
    }

    @Test fun `peso inicial de la rutina se usa solo sin historial`() {
        val p = SessionPlanner.plan(spec("ex_bench_press", start = 30.0), null, IncrementSettings(), WeightUnit.KG)
        assertTrue(p.sets.all { it.weightKg == 30.0 })
        val prev = perf(bench, done(1, 40.0, 10), done(2, 40.0, 10), done(3, 40.0, 10))
        val q = SessionPlanner.plan(spec("ex_bench_press", start = 30.0), prev, IncrementSettings(), WeightUnit.KG)
        assertTrue(q.sets.all { it.weightKg == 40.0 })
    }

    @Test fun `ejercicio de peso corporal no exige peso`() {
        val p = SessionPlanner.plan(spec("ex_pull_up"), null, IncrementSettings(), WeightUnit.KG)
        assertTrue(p.sets.all { it.weightKg == null })
        val r = SetRules.complete(SetEntry(1, reps = 7), TrackingType.BODYWEIGHT, RepTarget(6, 10), null)
        assertTrue(r is CompletionResult.Ok)
    }

    @Test fun `unilateral crea una serie por lado`() {
        val p = SessionPlanner.plan(spec("ex_dumbbell_row", sets = 3), null, IncrementSettings(), WeightUnit.KG)
        assertEquals(6, p.sets.size)
        assertEquals(listOf(Side.LEFT, Side.RIGHT), p.sets.take(2).map { it.side })
        assertEquals(listOf(1, 1, 2, 2, 3, 3), p.sets.map { it.setNumber })
    }

    @Test fun `duracion objetivo precarga la plancha`() {
        val p = SessionPlanner.plan(spec("ex_plank", dur = 45), null, IncrementSettings(), WeightUnit.KG)
        assertTrue(p.sets.all { it.durationSec == 45 })
    }

    @Test fun `serie extra copia la anterior y numera correctamente`() {
        val e = SessionPlanner.extraSet(listOf(done(1, 40.0, 10), done(2, 42.5, 8)))
        assertEquals(3, e.setNumber)
        assertEquals(42.5, e.weightKg!!, 1e-9)
        assertEquals(8, e.reps)
        assertTrue(!e.completed)
    }

    @Test(expected = IllegalArgumentException::class) fun `rechaza numero de series invalido`() {
        SessionPlanner.plan(spec("ex_bench_press", sets = 0), null, IncrementSettings(), WeightUnit.KG)
    }
}
