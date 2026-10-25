package com.gymtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseDatasetTest {
    private fun entry(name: String, target: String, equipment: String, secondary: List<String> = emptyList()) =
        DatasetEntry("1", name, target, equipment, secondary, listOf("Paso uno.", "Paso dos."))

    @Test fun `press de banca con barra se mapea a pecho barra y peso con repeticiones`() {
        val e = ExerciseDataset.toExercise(entry("barbell bench press", "pectorals", "barbell", listOf("triceps", "shoulders", "pectorals")))
        assertEquals("ds_1", e.id)
        assertEquals("Barbell bench press", e.name)
        assertEquals(MuscleGroup.CHEST, e.primaryMuscle)
        assertEquals(Equipment.BARBELL, e.equipment)
        assertEquals(TrackingType.WEIGHT_REPS, e.tracking)
        assertEquals(MovementType.PUSH, e.movement)
        assertEquals(listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS, MuscleGroup.CHEST).filter { it != MuscleGroup.CHEST }, e.secondaryMuscles)
        assertEquals("1. Paso uno.\n2. Paso dos.", e.cues)
        assertFalse(e.custom)
    }

    @Test fun `peso corporal y bandas no piden peso externo`() {
        assertEquals(TrackingType.BODYWEIGHT, ExerciseDataset.toExercise(entry("push-up", "pectorals", "body weight")).tracking)
        assertEquals(TrackingType.BODYWEIGHT, ExerciseDataset.toExercise(entry("band pull apart", "delts", "band")).tracking)
        assertEquals(TrackingType.BODYWEIGHT, ExerciseDataset.toExercise(entry("weighted pull-up", "lats", "weighted")).tracking)
        assertEquals(TrackingType.BODYWEIGHT, ExerciseDataset.toExercise(entry("assisted pull-up", "lats", "assisted")).tracking)
    }

    @Test fun `estiramientos y planchas se registran por duracion`() {
        assertEquals(TrackingType.DURATION, ExerciseDataset.toExercise(entry("assisted prone lying quads stretch", "quads", "assisted")).tracking)
        assertEquals(TrackingType.DURATION, ExerciseDataset.toExercise(entry("front plank with twist", "abs", "body weight")).tracking)
    }

    @Test fun `colgado dinamico no se confunde con isometrico`() {
        assertEquals(TrackingType.BODYWEIGHT, ExerciseDataset.toExercise(entry("hanging leg raise", "abs", "body weight")).tracking)
    }

    @Test fun `cardio se registra por distancia`() {
        val e = ExerciseDataset.toExercise(entry("stationary bike run v. 3", "cardiovascular system", "stationary bike"))
        assertEquals(TrackingType.DISTANCE, e.tracking)
        assertEquals(MovementType.CARDIO, e.movement)
        assertEquals(Equipment.MACHINE, e.equipment)
    }

    @Test fun `unilaterales se detectan por el nombre`() {
        assertTrue(ExerciseDataset.toExercise(entry("dumbbell one arm row", "lats", "dumbbell")).unilateral)
        assertFalse(ExerciseDataset.toExercise(entry("barbell row", "lats", "barbell")).unilateral)
    }

    @Test fun `movimientos de aislamiento antes que empuje`() {
        assertEquals(MovementType.ISOLATION, ExerciseDataset.toExercise(entry("cable pushdown", "triceps", "cable")).movement)
        assertEquals(MovementType.ISOLATION, ExerciseDataset.toExercise(entry("dumbbell lateral raise", "delts", "dumbbell")).movement)
        assertEquals(MovementType.PULL, ExerciseDataset.toExercise(entry("cable seated row", "upper back", "cable")).movement)
    }

    @Test fun `archivo invalido devuelve null sin lanzar`() {
        assertNull(ExerciseDataset.parse("no es json"))
        assertNull(ExerciseDataset.parse("[1,2]"))
    }

    @Test fun `el asset real de la app es valido y completo`() {
        val f = File("../app/src/main/assets/exercise_dataset.json")
        assertTrue("No se encuentra el asset en ${f.absolutePath}", f.exists())
        val file = ExerciseDataset.parse(f.readText())
        assertNotNull(file)
        file!!
        assertEquals("MIT", file.license)
        assertTrue(file.copyright.contains("Hasan Emir"))
        assertTrue(file.mediaNotice.contains("Gym visual"))
        val list = ExerciseDataset.toExercises(file)
        assertEquals(file.exercises.size, list.size)
        assertTrue("Esperaba más de 1300 ejercicios, hay ${list.size}", list.size > 1300)
        assertEquals("ids únicos", list.size, list.map { it.id }.toSet().size)
        assertTrue(list.all { it.id.startsWith("ds_") && it.name.isNotBlank() && !it.custom })
        assertTrue("todos con instrucciones", list.all { it.cues.isNotBlank() && it.description.isNotBlank() })
        // No debe chocar con los ids de la biblioteca básica.
        val basic = ExerciseLibrary.all.map { it.id }.toSet()
        assertTrue(list.none { it.id in basic })
        // Cobertura: todos los grupos y equipamientos están representados.
        for (m in MuscleGroup.values()) assertTrue("sin $m", list.any { it.primaryMuscle == m })
        for (t in TrackingType.values()) assertTrue("sin $t", list.any { it.tracking == t })
    }

    @Test fun `la relevancia pone primero los ejercicios revisados a mano`() {
        val ds = ExerciseDataset.toExercise(entry("a barbell bench press", "pectorals", "barbell"))
        val lib = ExerciseLibrary.all + ds
        val r = ExerciseRecommender.browse(lib, muscles = setOf(MuscleGroup.CHEST))
        assertTrue(r.indexOfFirst { it.id == ds.id } > r.indexOfLast { !it.id.startsWith("ds_") })
    }
}
