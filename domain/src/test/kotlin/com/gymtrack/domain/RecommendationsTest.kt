package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class RecommendationsTest {
    private val mon = RoutineDays.bit(DayOfWeek.MONDAY)
    private val wed = RoutineDays.bit(DayOfWeek.WEDNESDAY)

    @Test fun `sin rutinas no recomienda nada`() {
        assertNull(RoutineRecommender.recommend(emptyList(), DayOfWeek.MONDAY))
    }

    @Test fun `prioriza la rutina programada para hoy`() {
        val r = listOf(RoutineInfo("a", "Push", mon, 10), RoutineInfo("b", "Pull", wed, null))
        val rec = RoutineRecommender.recommend(r, DayOfWeek.WEDNESDAY)!!
        assertEquals("b", rec.routine.id)
        assertEquals(RoutineReason.SCHEDULED_TODAY, rec.reason)
    }

    @Test fun `si no hay programada elige la menos reciente`() {
        val r = listOf(RoutineInfo("a", "Push", 0, 500), RoutineInfo("b", "Pull", 0, 100), RoutineInfo("c", "Piernas", 0, 900))
        val rec = RoutineRecommender.recommend(r, DayOfWeek.FRIDAY)!!
        assertEquals("b", rec.routine.id)
        assertEquals(RoutineReason.LEAST_RECENT, rec.reason)
    }

    @Test fun `una rutina nunca hecha va antes que las hechas`() {
        val r = listOf(RoutineInfo("a", "Push", 0, 500), RoutineInfo("b", "Pull", 0, null))
        assertEquals("b", RoutineRecommender.recommend(r, DayOfWeek.FRIDAY)!!.routine.id)
    }

    @Test fun `sin historial ni programacion lo indica`() {
        val r = listOf(RoutineInfo("a", "Push", 0, null), RoutineInfo("b", "Pull", 0, null))
        assertEquals(RoutineReason.NO_HISTORY, RoutineRecommender.recommend(r, DayOfWeek.FRIDAY)!!.reason)
    }

    @Test fun `mascara de dias ida y vuelta`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
        assertEquals(days, RoutineDays.fromMask(RoutineDays.toMask(days)))
        assertEquals(0b1000001, RoutineDays.toMask(days))
    }

    @Test fun `replay detecta records dentro de la misma sesion en orden`() {
        val bests = ExerciseBests.from(listOf(perf(bench, done(1, 60.0, 8))), TrackingType.WEIGHT_REPS)
        val out = PrReplay.replay(bests, listOf("a" to done(1, 62.5, 5), "b" to done(2, 62.5, 5), "c" to done(3, 65.0, 3)))
        assertEquals(setOf("a", "c"), out.keys)
    }
}
