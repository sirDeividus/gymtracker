package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTypesAndCalculatorsTest {
    private fun warm(n: Int, kg: Double, reps: Int) = SetEntry(n, weightKg = kg, reps = reps, completed = true, type = SetType.WARMUP)

    @Test fun `los calentamientos no cuentan para volumen ni series`() {
        val sets = listOf(
            LoggedSet(bench.id, bench.name, bench.primaryMuscle, bench.tracking, warm(1, 20.0, 10)),
            LoggedSet(bench.id, bench.name, bench.primaryMuscle, bench.tracking, done(2, 40.0, 10)),
        )
        val t = Stats.totals(sets)
        assertEquals(1, t.completedSets)
        assertEquals(400.0, t.volumeKg, 1e-9)
        assertEquals(1, Stats.setsPerMuscle(sets)[MuscleGroup.CHEST])
    }

    @Test fun `la progresion ignora los calentamientos`() {
        // 2 calentamientos + 3×10 con 40 kg → debe sugerir subir, no tratar los calentamientos como series flojas.
        val p = perf(bench, warm(1, 20.0, 10), warm(2, 30.0, 5), done(3, 40.0, 10), done(4, 40.0, 10), done(5, 40.0, 10))
        val s = ProgressionEngine.suggest(bench, p, 3, RepTarget(8, 10), IncrementSettings(), WeightUnit.KG)
        assertEquals(SuggestionKind.INCREASE, s.kind)
        assertEquals(42.5, s.weightKg!!, 1e-9)
    }

    @Test fun `un calentamiento pesado no es un record ni entra en las mejores marcas`() {
        val history = listOf(perf(bench, done(1, 60.0, 8)))
        val bests = ExerciseBests.from(history, TrackingType.WEIGHT_REPS)
        assertTrue(bests.recordsFor(warm(1, 100.0, 5)).isEmpty())
        assertEquals(60.0, bests.plus(warm(1, 100.0, 5)).maxWeight()!!, 1e-9)
        assertEquals(60.0, ExerciseBests.from(listOf(perf(bench, warm(1, 100.0, 5), done(2, 60.0, 8))), TrackingType.WEIGHT_REPS).maxWeight()!!, 1e-9)
    }

    @Test fun `la columna anterior se empareja solo con series de trabajo`() {
        val prev = perf(bench, warm(1, 20.0, 10), done(2, 40.0, 10), done(3, 40.0, 9))
        val spec = RoutineExerciseSpec(ExerciseLibrary.byId.getValue("ex_bench_press"), 2, 8, 10)
        val plan = SessionPlanner.plan(spec, prev, IncrementSettings(), WeightUnit.KG)
        assertEquals(40.0, plan.sets[0].previous!!.weightKg!!, 1e-9)
        assertEquals(9, plan.sets[1].previous!!.reps)
    }

    @Test fun `calentamiento para 100 kg`() {
        val w = WarmupCalculator.generate(100.0, 2.5, minKg = 20.0)
        assertEquals(listOf(40.0, 60.0, 80.0), w.map { it.weightKg })
        assertEquals(listOf(8, 5, 3), w.map { it.reps })
    }

    @Test fun `calentamiento descarta cargas bajo la barra y duplicados`() {
        val w = WarmupCalculator.generate(30.0, 2.5, minKg = 20.0) // 12 y 18 < 20; 24 ok; 24 ≥ 0,95·30? no
        assertEquals(listOf(25.0), w.map { it.weightKg }) // 0,8·30 = 24 → 25 (≥ 28,5? no) — se conserva
        assertTrue(WarmupCalculator.generate(0.0, 2.5).isEmpty())
        assertTrue(WarmupCalculator.generate(20.0, 2.5, minKg = 20.0).isEmpty())
    }

    @Test fun `discos por lado para 100 kg`() {
        val p = PlateCalculator.plan(100.0, PlateCalculator.plateSetFor(WeightUnit.KG))
        assertEquals(listOf(25.0 to 1, 15.0 to 1), p.perSide) // (100-20)/2 = 40 = 25 + 15
        assertEquals(100.0, p.achievable, 1e-9)
        assertEquals(0.0, p.remainder, 1e-9)
    }

    @Test fun `discos con carga no alcanzable informa el resto`() {
        val p = PlateCalculator.plan(61.0, PlateCalculator.plateSetFor(WeightUnit.KG)) // 20.5 por lado → 20 + 0,5 sobra
        assertEquals(listOf(20.0 to 1), p.perSide)
        assertEquals(60.0, p.achievable, 1e-9)
        assertEquals(1.0, p.remainder, 1e-9)
    }

    @Test fun `solo la barra y cargas menores que la barra`() {
        val set = PlateCalculator.plateSetFor(WeightUnit.KG)
        assertTrue(PlateCalculator.plan(20.0, set).perSide.isEmpty())
        assertTrue(PlateCalculator.plan(10.0, set).perSide.isEmpty())
        val lb = PlateCalculator.plan(135.0, PlateCalculator.plateSetFor(WeightUnit.LB))
        assertEquals(listOf(45.0 to 1), lb.perSide)
    }

    @Test fun `copia de seguridad antigua sin tipo de serie se lee como normal`() {
        val json = """{"id":"a","sessionExerciseId":"b","setNumber":1,"completed":true}"""
        val dto = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(SetRecordDto.serializer(), json)
        assertEquals(SetType.NORMAL, dto.type)
    }

    @Test fun `comparacion de fotos ordena antes y despues y calcula dias y peso`() {
        val zone = java.time.ZoneOffset.UTC
        val day = { d: Int -> java.time.LocalDate.of(2026, 1, d).atStartOfDay(zone).toInstant().toEpochMilli() }
        val (swapped, c) = PhotoComparer.compare(day(31), 78.0, day(1), 84.5, zone)
        assertTrue(swapped)
        assertEquals(30, c.days)
        assertEquals(-6.5, c.weightDeltaKg!!, 1e-9) // 84,5 (antes) → 78,0 (después)
        val (sw2, c2) = PhotoComparer.compare(day(1), null, day(10), 80.0, zone)
        assertTrue(!sw2); assertEquals(9, c2.days); assertEquals(null, c2.weightDeltaKg)
    }

    @Test fun `tendencia de peso corporal`() {
        assertEquals(null, BodyWeightStats.trend(emptyList()))
        val t = BodyWeightStats.trend(listOf(3L to 80.0, 1L to 85.0, 2L to 83.0))!!
        assertEquals(80.0, t.latestKg, 1e-9); assertEquals(85.0, t.firstKg, 1e-9); assertEquals(-5.0, t.deltaKg, 1e-9); assertEquals(3, t.entries)
        assertTrue(BodyWeightStats.isValid(70.0)); assertTrue(!BodyWeightStats.isValid(5.0)); assertTrue(!BodyWeightStats.isValid(Double.NaN))
    }
}
