package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {
    private val inc = IncrementSettings()
    private val target = RepTarget(8, 10)

    private fun suggest(ref: ExerciseRef, p: ExercisePerformance?, sets: Int = 3, t: RepTarget = target) =
        ProgressionEngine.suggest(ref, p, sets, t, inc, WeightUnit.KG)

    @Test fun `sin historial no inventa recomendaciones`() {
        val s = suggest(bench, null)
        assertEquals(SuggestionKind.FIRST_TIME, s.kind)
        assertNull(s.weightKg)
        assertTrue(s.reason.contains("Primera vez"))
    }

    @Test fun `historial sin series completadas se trata como primera vez`() {
        val p = perf(bench, SetEntry(1, weightKg = 40.0, reps = 10, completed = false))
        assertEquals(SuggestionKind.FIRST_TIME, suggest(bench, p).kind)
    }

    @Test fun `3x10 con 40 kg sugiere 42,5 kg y explica el motivo`() {
        val p = perf(bench, done(1, 40.0, 10), done(2, 40.0, 10), done(3, 40.0, 10))
        val s = suggest(bench, p)
        assertEquals(SuggestionKind.INCREASE, s.kind)
        assertEquals(42.5, s.weightKg!!, 1e-9)
        assertTrue(s.reason.contains("3 × 10/10/10 con 40 kg"))
        assertTrue(s.reason.contains("42,5 kg"))
        assertTrue(s.actionable)
    }

    @Test fun `10-9-8 mantiene el peso y busca mejorar repeticiones`() {
        val p = perf(bench, done(1, 40.0, 10), done(2, 40.0, 9), done(3, 40.0, 8))
        val s = suggest(bench, p)
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertEquals(40.0, s.weightKg!!, 1e-9)
        assertEquals(10, s.reps)
    }

    @Test fun `por debajo del minimo recomienda mantener o revisar`() {
        val p = perf(bench, done(1, 40.0, 8), done(2, 40.0, 7), done(3, 40.0, 6))
        val s = suggest(bench, p)
        assertEquals(SuggestionKind.HOLD_REVIEW, s.kind)
        assertEquals(40.0, s.weightKg!!, 1e-9)
        assertTrue(!s.actionable)
    }

    @Test fun `si faltan series no sube`() {
        val p = perf(bench, done(1, 40.0, 10), done(2, 40.0, 10))
        assertEquals(SuggestionKind.HOLD, suggest(bench, p, sets = 3).kind)
    }

    @Test fun `esfuerzo excesivo impide subir aunque se alcancen las repeticiones`() {
        val p = perf(bench, done(1, 40.0, 10, 9.5), done(2, 40.0, 10, 10.0), done(3, 40.0, 10, 10.0))
        assertEquals(SuggestionKind.HOLD_EFFORT, suggest(bench, p).kind)
    }

    @Test fun `RPE alto pero no extremo tambien frena el incremento`() {
        val p = perf(bench, done(1, 40.0, 10, 9.0), done(2, 40.0, 10, 9.0), done(3, 40.0, 10, 9.0))
        assertEquals(SuggestionKind.HOLD_EFFORT, suggest(bench, p).kind)
    }

    @Test fun `RPE moderado permite subir`() {
        val p = perf(bench, done(1, 40.0, 10, 7.0), done(2, 40.0, 10, 8.0), done(3, 40.0, 10, 8.0))
        assertEquals(SuggestionKind.INCREASE, suggest(bench, p).kind)
    }

    @Test fun `dolor o tecnica deteriorada impide subir`() {
        val p = perf(bench, done(1, 40.0, 10), done(2, 40.0, 10), done(3, 40.0, 10), discomfort = true)
        val s = suggest(bench, p)
        assertEquals(SuggestionKind.HOLD_EFFORT, s.kind)
        assertEquals(40.0, s.weightKg!!, 1e-9)
    }

    @Test fun `incremento respeta el paso disponible y ronda el 2 por ciento`() {
        assertEquals(2.5, ProgressionEngine.incrementFor(40.0, 2.5), 1e-9)
        assertEquals(2.5, ProgressionEngine.incrementFor(120.0, 2.5), 1e-9)
        assertEquals(5.0, ProgressionEngine.incrementFor(200.0, 2.5), 1e-9) // 4 kg ≈ 2 % → 2 pasos
        assertEquals(5.0, ProgressionEngine.incrementFor(20.0, 5.0), 1e-9)
    }

    @Test fun `mancuernas y tren inferior usan sus propios incrementos`() {
        val p = perf(dumbbellPress, done(1, 20.0, 10), done(2, 20.0, 10), done(3, 20.0, 10))
        assertEquals(22.0, suggest(dumbbellPress, p).weightKg!!, 1e-9)
        val q = perf(squat, done(1, 100.0, 10), done(2, 100.0, 10), done(3, 100.0, 10))
        assertEquals(105.0, suggest(squat, q).weightKg!!, 1e-9)
    }

    @Test fun `la sugerencia usa la carga mas alta de la sesion`() {
        val p = perf(bench, done(1, 40.0, 10), done(2, 42.5, 10), done(3, 42.5, 10))
        assertEquals(45.0, suggest(bench, p).weightKg!!, 1e-9)
    }

    @Test fun `peso corporal sin lastre sugiere mas repeticiones o variantes sin inventar peso`() {
        val p = perf(pullUp, done(1, null, 10), done(2, null, 10), done(3, null, 10))
        val s = suggest(pullUp, p)
        assertEquals(SuggestionKind.MORE_REPS, s.kind)
        assertNull(s.weightKg)
        assertTrue(s.reason.contains("variante"))
    }

    @Test fun `peso corporal con asistencia sugiere reducir asistencia`() {
        val p = perf(pullUp, done(1, -20.0, 10), done(2, -20.0, 10), done(3, -20.0, 10))
        val s = suggest(pullUp, p)
        assertEquals(SuggestionKind.INCREASE, s.kind)
        assertEquals(-17.5, s.weightKg!!, 1e-9)
    }

    @Test fun `peso corporal con lastre sugiere mas lastre`() {
        val p = perf(pullUp, done(1, 5.0, 10), done(2, 5.0, 10), done(3, 5.0, 10))
        assertEquals(7.5, suggest(pullUp, p).weightKg!!, 1e-9)
    }

    @Test fun `peso corporal por debajo del rango no avanza`() {
        val p = perf(pullUp, done(1, null, 6), done(2, null, 5), done(3, null, 4))
        assertEquals(SuggestionKind.HOLD, suggest(pullUp, p).kind)
    }

    @Test fun `duracion completada en todas las series sugiere mas tiempo`() {
        val p = perf(plank, done(1, null, null, durationSec = 45), done(2, null, null, durationSec = 40), done(3, null, null, durationSec = 40))
        val s = suggest(plank, p)
        assertEquals(SuggestionKind.MORE_TIME, s.kind)
        assertEquals(45, s.durationSec)
    }

    @Test fun `libras muestran el peso en libras`() {
        val p = perf(bench, done(1, 45.359237, 10), done(2, 45.359237, 10), done(3, 45.359237, 10))
        val s = ProgressionEngine.suggest(bench, p, 3, target, IncrementSettings.defaultsFor(WeightUnit.LB), WeightUnit.LB)
        assertTrue(s.reason.contains("100 lb"))
        assertTrue(s.reason.contains("105 lb"))
    }
}
