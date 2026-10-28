package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class IntervalTimerTest {
    private val tabata = IntervalPlan.TABATA // 5 s prep + 8×20 s + 7×10 s = 235 s

    private fun at(sec: Double) = IntervalTimer.snapshot(tabata, (sec * 1000).toLong())

    @Test fun `tabata dura 235 segundos`() {
        assertEquals(235, tabata.totalSec)
        assertEquals(20, tabata.workSec); assertEquals(10, tabata.restSec); assertEquals(8, tabata.rounds)
    }

    @Test fun `empieza con la preparacion`() {
        val s = at(0.0)
        assertEquals(IntervalPhase.PREP, s.phase); assertEquals(0, s.round); assertEquals(5000, s.phaseRemainingMs)
        assertEquals(235_000, s.totalRemainingMs)
    }

    @Test fun `primera ronda de trabajo y su descanso`() {
        val w = at(5.0)
        assertEquals(IntervalPhase.WORK, w.phase); assertEquals(1, w.round); assertEquals(20_000, w.phaseRemainingMs)
        assertEquals(0, w.completedRounds)
        val last = at(24.999)
        assertEquals(IntervalPhase.WORK, last.phase); assertEquals(0, last.completedRounds)
        val r = at(25.0)
        assertEquals(IntervalPhase.REST, r.phase); assertEquals(1, r.round); assertEquals(10_000, r.phaseRemainingMs)
        assertEquals(1, r.completedRounds)
    }

    @Test fun `la segunda ronda empieza a los 35 s`() {
        val s = at(35.0)
        assertEquals(IntervalPhase.WORK, s.phase); assertEquals(2, s.round); assertEquals(1, s.completedRounds)
    }

    @Test fun `la ultima ronda termina sin descanso`() {
        val lastWork = at(5.0 + 7 * 30 + 19.0)
        assertEquals(IntervalPhase.WORK, lastWork.phase); assertEquals(8, lastWork.round)
        val done = at(235.0)
        assertEquals(IntervalPhase.DONE, done.phase); assertEquals(8, done.completedRounds); assertTrue(done.finished)
        assertEquals(IntervalPhase.DONE, at(1000.0).phase)
    }

    @Test fun `rondas completadas nunca decrecen con el tiempo`() {
        var prev = 0
        for (ms in 0..240_000 step 100) {
            val c = IntervalTimer.snapshot(tabata, ms.toLong()).completedRounds
            assertTrue(c >= prev); prev = c
        }
        assertEquals(8, prev)
    }

    @Test fun `sin descanso encadena rondas de trabajo`() {
        val p = IntervalPlan(workSec = 30, restSec = 0, rounds = 3, prepSec = 0)
        assertEquals(90, p.totalSec)
        assertEquals(2, IntervalTimer.snapshot(p, 31_000).round)
        assertEquals(IntervalPhase.DONE, IntervalTimer.snapshot(p, 90_000).phase)
        assertFalse(IntervalTimer.snapshot(p, 89_999).finished)
    }

    @Test fun `varios ciclos con descanso entre ciclos`() {
        // 3 s prep + 2 ciclos × (2 rondas × 10 s + 5 s descanso entre rondas) + 20 s entre ciclos
        val p = IntervalPlan(workSec = 10, restSec = 5, rounds = 2, prepSec = 3, cycles = 2, cycleRestSec = 20)
        assertEquals(4, p.totalRounds)
        assertEquals(3 + 40 + 2 * 5 + 20, p.totalSec)
        val w1 = IntervalTimer.snapshot(p, 3_000)
        assertEquals(IntervalPhase.WORK, w1.phase); assertEquals(1, w1.cycle); assertEquals(1, w1.round)
        val rest = IntervalTimer.snapshot(p, 13_000)
        assertEquals(IntervalPhase.REST, rest.phase); assertEquals(1, rest.completedRounds)
        val w2 = IntervalTimer.snapshot(p, 18_000)
        assertEquals(IntervalPhase.WORK, w2.phase); assertEquals(2, w2.round)
        val cycleRest = IntervalTimer.snapshot(p, 28_000)
        assertEquals(IntervalPhase.CYCLE_REST, cycleRest.phase); assertEquals(1, cycleRest.cycle)
        assertEquals(2, cycleRest.completedRounds); assertEquals(20_000, cycleRest.phaseRemainingMs)
        val c2 = IntervalTimer.snapshot(p, 48_000)
        assertEquals(IntervalPhase.WORK, c2.phase); assertEquals(2, c2.cycle); assertEquals(1, c2.round); assertEquals(2, c2.completedRounds)
        assertEquals(IntervalPhase.DONE, IntervalTimer.snapshot(p, p.totalSec * 1000L).phase)
        assertEquals(4, IntervalTimer.snapshot(p, p.totalSec * 1000L).completedRounds)
    }

    @Test fun `cuenta atras de inicio configurable y sin descansos`() {
        val p = IntervalPlan(workSec = 20, restSec = 0, rounds = 2, prepSec = 10, cycles = 2, cycleRestSec = 0)
        assertEquals(10 + 80, p.totalSec)
        assertEquals(IntervalPhase.PREP, IntervalTimer.snapshot(p, 9_999).phase)
        assertEquals(IntervalPhase.WORK, IntervalTimer.snapshot(p, 10_000).phase)
        assertEquals(2, IntervalTimer.snapshot(p, 10_000 + 40_000).cycle)
    }

    @Test fun `lista de tramos y fase siguiente`() {
        val p = IntervalPlan(workSec = 10, restSec = 5, rounds = 2, prepSec = 3, cycles = 2, cycleRestSec = 20)
        val steps = IntervalTimer.schedule(p)
        assertEquals(IntervalPhase.PREP, steps.first().phase)
        assertEquals(listOf(IntervalPhase.PREP, IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK, IntervalPhase.CYCLE_REST, IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK), steps.map { it.phase })
        assertEquals(p.totalSec, steps.sumOf { it.seconds })
        assertEquals(IntervalPhase.WORK, IntervalTimer.nextPhase(p, 0))            // prep -> trabajo
        assertEquals(IntervalPhase.REST, IntervalTimer.nextPhase(p, 5_000))        // trabajo 1 -> descanso
        assertEquals(IntervalPhase.CYCLE_REST, IntervalTimer.nextPhase(p, 20_000)) // trabajo 2 -> descanso de ciclo
        assertEquals(IntervalPhase.DONE, IntervalTimer.nextPhase(p, p.totalSec * 1000L - 1))
    }

    @Test fun `ciclos fuera de rango se rechazan`() {
        assertTrue(runCatching { IntervalPlan(20, 10, 8, cycles = 0) }.isFailure)
        assertTrue(runCatching { IntervalPlan(20, 10, 8, cycles = 31) }.isFailure)
    }

    @Test fun `tiempo negativo se trata como cero`() {
        assertEquals(IntervalPhase.PREP, IntervalTimer.snapshot(tabata, -500).phase)
    }

    @Test fun `plan invalido se rechaza`() {
        assertTrue(runCatching { IntervalPlan(0, 10, 8) }.isFailure)
        assertTrue(runCatching { IntervalPlan(20, 10, 0) }.isFailure)
        assertTrue(runCatching { IntervalPlan(20, -1, 8) }.isFailure)
    }

    @Test fun `la plantilla del miercoles usa ejercicios por duracion y distancia`() {
        val t = RoutineTemplates.all.first { it.name == "Tabata + cardio" }
        assertEquals(setOf(DayOfWeek.WEDNESDAY), t.days)
        val abs = ExerciseLibrary.byId.getValue(t.items[0].exerciseId)
        assertEquals(TrackingType.DURATION, abs.tracking)
        assertEquals(8, t.items[0].sets); assertEquals(20, t.items[0].durationSec); assertEquals(10, t.items[0].restSec)
        assertEquals(TrackingType.DISTANCE, ExerciseLibrary.byId.getValue(t.items[1].exerciseId).tracking)
    }
}
