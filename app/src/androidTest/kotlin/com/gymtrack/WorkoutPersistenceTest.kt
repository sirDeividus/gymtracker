package com.gymtrack

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.repo.CompleteOutcome
import com.gymtrack.data.repo.ExerciseRepository
import com.gymtrack.data.repo.FinishOutcome
import com.gymtrack.data.repo.RoutineRepository
import com.gymtrack.data.repo.WorkoutRepository
import com.gymtrack.data.settings.SettingsStore
import com.gymtrack.domain.SessionStatus
import com.gymtrack.domain.SuggestionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas de persistencia con Room real (requieren dispositivo o emulador). */
@RunWith(AndroidJUnit4::class)
class WorkoutPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: GymDatabase
    private lateinit var settings: SettingsStore
    private lateinit var routines: RoutineRepository
    private lateinit var workout: WorkoutRepository

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GymDatabase::class.java).build()
        settings = SettingsStore(context)
        ExerciseRepository(db.exerciseDao()).seedLibrary()
        routines = RoutineRepository(db)
        workout = WorkoutRepository(db, settings)
    }

    @After fun tearDown() { db.close() }

    private suspend fun pushRoutine(sets: Int = 3): String {
        val id = routines.create("Push", "")
        routines.addExercise(id, "ex_bench_press") { copy(sets = sets, repMin = 8, repMax = 10) }
        routines.addExercise(id, "ex_pull_up") { copy(sets = 2, repMin = 5, repMax = 8) }
        return id
    }

    private suspend fun completeAll(sessionId: String, weight: Double, reps: Int) {
        val w = workout.observeWorkout(sessionId).first()!!
        for (ex in w.exercises) for (s in ex.sets) {
            workout.editSet(s.id) { it.copy(weightKg = if (ex.entity.tracking == "WEIGHT_REPS") weight else null, reps = reps) }
            assertTrue(workout.completeSet(s.id) is CompleteOutcome.Done)
        }
    }

    @Test fun session_has_exactly_the_configured_sets() = runBlocking {
        val sessionId = workout.startSession(pushRoutine(sets = 4))
        val w = workout.observeWorkout(sessionId).first()!!
        assertEquals(2, w.exercises.size)
        assertEquals(4, w.exercises[0].sets.size)
        assertEquals(2, w.exercises[1].sets.size)
        assertTrue(w.exercises.flatMap { it.sets }.none { it.completed })
    }

    @Test fun only_one_session_can_be_active() = runBlocking {
        val r = pushRoutine()
        val a = workout.startSession(r)
        val b = workout.startSession(r)
        assertEquals(a, b)
    }

    @Test fun previous_values_come_from_the_last_finished_session_of_the_same_exercise() = runBlocking {
        val r = pushRoutine()
        val first = workout.startSession(r)
        assertTrue(workout.observeWorkout(first).first()!!.exercises.all { ex -> ex.sets.all { it.prevReps == null } })
        completeAll(first, weight = 40.0, reps = 10)
        assertEquals(FinishOutcome.FINISHED, workout.finish(first, "bien"))

        val second = workout.startSession(r)
        val bench = workout.observeWorkout(second).first()!!.exercises.first { it.entity.exerciseId == "ex_bench_press" }
        assertTrue(bench.sets.all { it.prevWeightKg == 40.0 && it.prevReps == 10 })
        assertTrue("se precarga el peso anterior", bench.sets.all { it.weightKg == 40.0 })
        // 3 x 10 con 40 kg → se sugiere subir, pero NO se aplica solo.
        assertEquals(SuggestionKind.INCREASE.name, bench.entity.suggestionKind)
        assertEquals(42.5, bench.entity.suggestionWeightKg!!, 1e-9)
    }

    @Test fun bodyweight_exercises_do_not_require_external_weight_but_loaded_ones_do() = runBlocking {
        val sessionId = workout.startSession(pushRoutine())
        val w = workout.observeWorkout(sessionId).first()!!
        val pull = w.exercises.first { it.entity.exerciseId == "ex_pull_up" }.sets.first()
        val bench = w.exercises.first { it.entity.exerciseId == "ex_bench_press" }.sets.first()
        assertTrue(workout.completeSet(bench.id) is CompleteOutcome.Invalid)
        assertTrue(workout.completeSet(pull.id) is CompleteOutcome.Done)
    }

    @Test fun completed_sets_can_be_edited_and_extra_sets_added() = runBlocking {
        val sessionId = workout.startSession(pushRoutine())
        var bench = workout.observeWorkout(sessionId).first()!!.exercises.first { it.entity.exerciseId == "ex_bench_press" }
        val s = bench.sets.first()
        workout.editSet(s.id) { it.copy(weightKg = 40.0, reps = 9) }
        workout.completeSet(s.id)
        workout.editSet(s.id) { it.copy(weightKg = 42.5) }
        workout.addSet(bench.entity.id)
        bench = workout.observeWorkout(sessionId).first()!!.exercises.first { it.entity.exerciseId == "ex_bench_press" }
        assertEquals(4, bench.sets.size)
        val edited = bench.sets.first { it.id == s.id }
        assertTrue(edited.completed)
        assertEquals(42.5, edited.weightKg!!, 1e-9)
        assertEquals(9, edited.reps)
    }

    @Test fun completing_a_set_starts_the_persisted_rest_timer() = runBlocking {
        val sessionId = workout.startSession(pushRoutine())
        val s = workout.observeWorkout(sessionId).first()!!.exercises[0].sets[0]
        workout.editSet(s.id) { it.copy(weightKg = 40.0, reps = 8) }
        val out = workout.completeSet(s.id) as CompleteOutcome.Done
        assertTrue(out.restStarted)
        assertNotNull(workout.activeSession.first()!!.restEndsAt)
        workout.skipRest(sessionId)
        assertNull(workout.activeSession.first()!!.restEndsAt)
    }

    @Test fun finished_session_appears_in_history_and_discarded_does_not() = runBlocking {
        val r = pushRoutine()
        val a = workout.startSession(r)
        completeAll(a, 40.0, 8)
        workout.finish(a, "")
        val b = workout.startSession(r)
        workout.discard(b)
        val finished = db.sessionDao().observeFinished().first()
        assertEquals(listOf(a), finished.map { it.id })
        assertEquals(SessionStatus.DISCARDED.name, db.sessionDao().get(b)!!.status)
        assertNull(db.sessionDao().getActive())
    }

    @Test fun cannot_finish_without_completed_sets() = runBlocking {
        val id = workout.startSession(pushRoutine())
        assertEquals(FinishOutcome.NO_COMPLETED_SETS, workout.finish(id, ""))
        assertNotNull(db.sessionDao().getActive())
    }

    @Test fun editing_or_deleting_the_routine_does_not_change_history() = runBlocking {
        val r = pushRoutine()
        val a = workout.startSession(r)
        completeAll(a, 40.0, 8)
        workout.finish(a, "")
        val before = db.sessionDao().getSets(a).map { it.weightKg to it.reps }
        val names = db.sessionDao().getExercises(a).map { it.exerciseName to it.targetSets }

        routines.updateDetails(r, "Otro nombre", "", 0)
        routines.getExercises(r).forEach { routines.updateExercise(it.copy(sets = 9, repMin = 1, repMax = 2)) }
        routines.removeExercise(r, routines.getExercises(r).first().id)
        routines.delete(r)

        assertEquals(before, db.sessionDao().getSets(a).map { it.weightKg to it.reps })
        assertEquals(names, db.sessionDao().getExercises(a).map { it.exerciseName to it.targetSets })
        assertEquals("Push", db.sessionDao().get(a)!!.routineName)
    }

    @Test fun summary_reports_records_only_against_earlier_history() = runBlocking {
        val r = pushRoutine()
        val a = workout.startSession(r)
        completeAll(a, 40.0, 8)
        workout.finish(a, "")
        assertTrue("la primera vez no hay récords", workout.summary(a)!!.records.isEmpty())
        val b = workout.startSession(r)
        completeAll(b, 45.0, 8)
        workout.finish(b, "")
        val s = workout.summary(b)!!
        assertTrue(s.records.any { it.exerciseId == "ex_bench_press" })
        assertNotNull(s.previous)
    }

    @Test fun routine_operations_duplicate_reorder_and_copy() = runBlocking {
        val r = pushRoutine()
        val rows = routines.getExercises(r)
        routines.reorder(r, rows.map { it.id }.reversed())
        assertEquals(rows.map { it.exerciseId }.reversed(), routines.getExercises(r).map { it.exerciseId })
        val copy = routines.duplicate(r)!!
        assertEquals(2, routines.getExercises(copy).size)
        val other = routines.create("Otra")
        assertEquals(1, routines.copyExercises(r, setOf(rows.first().id), other))
        assertEquals(1, routines.getExercises(other).size)
    }
}
