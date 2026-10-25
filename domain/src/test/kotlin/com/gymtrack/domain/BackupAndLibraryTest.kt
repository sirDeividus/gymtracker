package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAndLibraryTest {
    private fun sample() = BackupFile(
        exportedAt = 123,
        customExercises = listOf(
            Exercise("custom_1", "Mi ejercicio", MuscleGroup.CHEST, emptyList(), Equipment.OTHER, MovementType.PUSH, TrackingType.WEIGHT_REPS, custom = true),
        ),
        routines = listOf(RoutineDto("r1", "Push", "desc", 0b0000101, 1, 2)),
        routineExercises = listOf(RoutineExerciseDto("re1", "r1", "ex_bench_press", 0, 3, 8, 10, startWeightKg = 40.0)),
        sessions = listOf(SessionDto("s1", "r1", "Push", 1000, 5000, SessionStatus.FINISHED, "bien")),
        sessionExercises = listOf(
            SessionExerciseDto("se1", "s1", "ex_bench_press", "Press de banca con barra", MuscleGroup.CHEST, Equipment.BARBELL, TrackingType.WEIGHT_REPS, false, 0, 3, 8, 10),
        ),
        sets = listOf(SetRecordDto("st1", "se1", 1, weightKg = 40.0, reps = 10, completed = true)),
        settings = mapOf("unit" to "KG"),
    )

    @Test fun `ida y vuelta conserva todos los datos`() {
        val original = sample()
        val parsed = BackupCodec.parse(BackupCodec.encode(original))
        assertTrue(parsed is BackupParseResult.Valid)
        parsed as BackupParseResult.Valid
        assertEquals(original, parsed.file)
        assertEquals(1, parsed.summary.sessions)
        assertEquals(1, parsed.summary.sets)
    }

    @Test fun `un archivo que no es una copia se rechaza sin lanzar`() {
        assertTrue(BackupCodec.parse("hola") is BackupParseResult.Invalid)
        assertTrue(BackupCodec.parse("{\"a\":1}") is BackupParseResult.Invalid)
        assertTrue(BackupCodec.parse("") is BackupParseResult.Invalid)
    }

    @Test fun `detecta referencias rotas`() {
        val bad = sample().copy(sets = sample().sets + SetRecordDto("st2", "no_existe", 1))
        val r = BackupCodec.parse(BackupCodec.encode(bad)) as BackupParseResult.Invalid
        assertTrue(r.errors.any { it.contains("series") })
    }

    @Test fun `detecta ids duplicados y version futura`() {
        val dup = sample().copy(routines = sample().routines + sample().routines)
        assertTrue(BackupValidator.validate(dup).any { it.contains("repetidos") })
        assertTrue(BackupValidator.validate(sample().copy(version = 99)).any { it.contains("más reciente") })
        assertTrue(BackupValidator.validate(sample().copy(format = "otra")).isNotEmpty())
    }

    @Test fun `detecta mas de una sesion en curso y valores fuera de rango`() {
        val s = sample()
        val two = s.copy(sessions = listOf(
            s.sessions[0].copy(status = SessionStatus.IN_PROGRESS, endedAt = null),
            s.sessions[0].copy(id = "s2", status = SessionStatus.IN_PROGRESS, endedAt = null),
        ))
        assertTrue(BackupValidator.validate(two).any { it.contains("en curso") })
        val bad = s.copy(sets = listOf(s.sets[0].copy(reps = -4)))
        assertTrue(BackupValidator.validate(bad).any { it.contains("fuera de rango") })
    }

    @Test fun `acepta campos desconocidos de versiones compatibles`() {
        val text = BackupCodec.encode(sample()).replaceFirst("\"version\": 1", "\"version\": 1,\n \"extra\": true")
        assertTrue(BackupCodec.parse(text) is BackupParseResult.Valid)
    }

    @Test fun `biblioteca con ids unicos y todos los grupos cubiertos`() {
        val all = ExerciseLibrary.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.name }.toSet().size)
        for (m in MuscleGroup.values()) assertTrue("Sin ejercicios para $m", all.any { it.primaryMuscle == m })
        for (e in Equipment.values()) assertTrue("Sin ejercicios para $e", all.any { it.equipment == e })
        assertTrue(all.all { it.description.isNotBlank() && it.id.startsWith("ex_") && !it.custom })
    }

    @Test fun `ejercicios cronometrados no requieren peso y los de peso corporal usan su tipo`() {
        assertEquals(TrackingType.DURATION, ExerciseLibrary.byId.getValue("ex_plank").tracking)
        assertEquals(TrackingType.BODYWEIGHT, ExerciseLibrary.byId.getValue("ex_pull_up").tracking)
        assertTrue(ExerciseLibrary.byId.getValue("ex_dumbbell_row").unilateral)
    }

    @Test fun `recomendaciones de pecho incluyen press y aperturas como sugerencia`() {
        val names = ExerciseRecommender.recommend(ExerciseLibrary.all, MuscleGroup.CHEST).map { it.name }
        assertTrue(names.any { it.contains("Press de banca") })
        assertTrue(names.any { it.contains("Press inclinado con mancuernas") })
        assertTrue(names.any { it.contains("Aperturas") })
        assertTrue(names.any { it.contains("Flexiones") })
    }

    @Test fun `la relevancia prioriza favoritos y uso real y se puede filtrar por equipamiento`() {
        val prefs = RecommendationPrefs(favoriteIds = setOf("ex_cable_crossover"), usage = mapOf("ex_push_up" to 9))
        val r = ExerciseRecommender.recommend(ExerciseLibrary.all, MuscleGroup.CHEST, prefs)
        assertEquals("ex_cable_crossover", r[0].id)
        assertEquals("ex_push_up", r[1].id)
        val onlyDumbbell = ExerciseRecommender.recommend(ExerciseLibrary.all, MuscleGroup.CHEST, equipment = setOf(Equipment.DUMBBELL))
        assertTrue(onlyDumbbell.isNotEmpty() && onlyDumbbell.all { it.equipment == Equipment.DUMBBELL })
    }

    @Test fun `navegar combina busqueda filtros y orden`() {
        val lib = ExerciseLibrary.all
        val r = ExerciseRecommender.browse(lib, "press", setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS), setOf(Equipment.DUMBBELL))
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.name.contains("ress") && it.equipment == Equipment.DUMBBELL })
        assertTrue(r.all { it.primaryMuscle == MuscleGroup.CHEST || it.primaryMuscle == MuscleGroup.SHOULDERS })
        val byMuscle = ExerciseRecommender.browse(lib, sort = RecommendationSort.MUSCLE).map { it.primaryMuscle.ordinal }
        assertEquals(byMuscle.sorted(), byMuscle)
        assertEquals(lib.size, ExerciseRecommender.browse(lib).size)
    }

    @Test fun `busqueda ignora acentos y mayusculas`() {
        assertTrue(ExerciseRecommender.search(ExerciseLibrary.all, "TRICEPS").any { it.name.contains("tríceps") })
        assertTrue(ExerciseRecommender.search(ExerciseLibrary.all, "").size == ExerciseLibrary.all.size)
    }

    @Test fun `formato de numeros con coma decimal`() {
        assertEquals("42,5 kg", Formatting.weight(42.5, WeightUnit.KG))
        assertEquals("40 kg", Formatting.weight(40.0, WeightUnit.KG))
        assertEquals("100 lb", Formatting.weight(45.359237, WeightUnit.LB))
        assertEquals(42.5, Formatting.parseDecimal("42,5")!!, 1e-9)
        assertEquals(null, Formatting.parseDecimal("abc"))
        assertEquals(null, Formatting.parseDecimal("-3"))
        assertEquals("1:05", Formatting.duration(65))
        assertEquals("1:01:01", Formatting.duration(3661))
    }
}
