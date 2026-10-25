package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetRulesAndTimerTest {
    private val t = RepTarget(8, 10)

    @Test fun `completar con peso y sin reps usa el minimo del rango`() {
        val r = SetRules.complete(SetEntry(1, weightKg = 40.0), TrackingType.WEIGHT_REPS, t, null) as CompletionResult.Ok
        assertEquals(8, r.set.reps)
        assertTrue(r.set.completed)
    }

    @Test fun `no se inventa el peso externo`() {
        val r = SetRules.complete(SetEntry(1, reps = 8), TrackingType.WEIGHT_REPS, t, null)
        assertTrue(r is CompletionResult.Invalid)
    }

    @Test fun `duracion es obligatoria salvo que haya objetivo`() {
        assertTrue(SetRules.complete(SetEntry(1), TrackingType.DURATION, t, null) is CompletionResult.Invalid)
        val ok = SetRules.complete(SetEntry(1), TrackingType.DURATION, t, 30) as CompletionResult.Ok
        assertEquals(30, ok.set.durationSec)
    }

    @Test fun `valores fuera de rango se rechazan`() {
        assertTrue(SetRules.complete(SetEntry(1, weightKg = 5000.0, reps = 5), TrackingType.WEIGHT_REPS, t, null) is CompletionResult.Invalid)
        assertTrue(SetRules.complete(SetEntry(1, weightKg = 50.0, reps = -1), TrackingType.WEIGHT_REPS, t, null) is CompletionResult.Invalid)
        assertTrue(SetRules.complete(SetEntry(1, weightKg = 50.0, reps = 5, rpe = 11.0), TrackingType.WEIGHT_REPS, t, null) is CompletionResult.Invalid)
    }

    @Test fun `temporizador cuenta atras con marcas absolutas`() {
        val s = RestTimerState.start(90, 10_000)
        assertEquals(90_000, s.remainingMs(10_000))
        assertEquals(30_000, s.remainingMs(70_000))
        assertFalse(s.finished(99_999))
        assertTrue(s.finished(100_000))
        assertEquals(0, s.remainingMs(500_000))
    }

    @Test fun `pausar y reanudar conserva el tiempo restante`() {
        val s = RestTimerState.start(60, 0).pause(20_000)
        assertTrue(s.paused)
        assertEquals(40_000, s.remainingMs(999_999))
        val r = s.resume(100_000)
        assertFalse(r.paused)
        assertEquals(40_000, r.remainingMs(100_000))
        assertEquals(140_000L, r.endsAtMs)
    }

    @Test fun `reiniciar y saltar`() {
        val s = RestTimerState.start(60, 0)
        assertEquals(60_000, s.restart(30_000).remainingMs(30_000))
        assertFalse(s.skip().active)
    }

    @Test fun `ajustar descanso suma y resta sin bajar de un segundo`() {
        val s = RestTimerState.start(60, 0)
        assertEquals(45_000, s.adjust(15, 0).remainingMs(30_000))
        assertEquals(1_000, s.adjust(-120, 0).remainingMs(0))
        assertFalse(RestTimerState.idle().adjust(15, 0).active)
    }
}
