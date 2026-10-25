package com.gymtrack

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.Migrations
import com.gymtrack.data.repo.BackupRepository
import com.gymtrack.data.repo.CompleteOutcome
import com.gymtrack.data.repo.ExerciseRepository
import com.gymtrack.data.repo.RoutineRepository
import com.gymtrack.data.repo.WorkoutRepository
import com.gymtrack.data.settings.SettingsStore
import com.gymtrack.domain.BackupCodec
import com.gymtrack.domain.BackupParseResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryAndMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GymDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "recovery-test.db"

    @Before @After fun clean() { context.deleteDatabase(dbName) }

    private fun open() = Room.databaseBuilder(context, GymDatabase::class.java, dbName).build()

    /** Simula que Android mata el proceso: se cierra la base y se vuelve a abrir desde disco. */
    @Test fun in_progress_session_survives_process_death() = runBlocking {
        var db = open()
        ExerciseRepository(db.exerciseDao()).seedLibrary()
        val routines = RoutineRepository(db)
        val workout = WorkoutRepository(db, SettingsStore(context))
        val rid = routines.create("Push")
        routines.addExercise(rid, "ex_bench_press")
        val sid = workout.startSession(rid)
        val set = db.sessionDao().getSets(sid).first()
        workout.editSet(set.id) { it.copy(weightKg = 40.0, reps = 9) }
        assertTrue(workout.completeSet(set.id) is CompleteOutcome.Done)
        db.close()

        db = open()
        val active = db.sessionDao().getActive()
        assertNotNull(active)
        assertEquals(sid, active!!.id)
        val restored = db.sessionDao().getSets(sid)
        assertEquals(3, restored.size)
        assertEquals(1, restored.count { it.completed })
        assertEquals(40.0, restored.first { it.completed }.weightKg!!, 1e-9)
        assertNotNull("el temporizador de descanso también se conserva", active.restEndsAt)
        db.close()
    }

    @Test fun schema_v1_opens_and_validates_with_all_migrations() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, GymDatabase.VERSION, true, *Migrations.ALL).close()
    }

    @Test fun backup_export_import_round_trip_keeps_everything() = runBlocking {
        val db = open()
        val settings = SettingsStore(context)
        ExerciseRepository(db.exerciseDao()).seedLibrary()
        val routines = RoutineRepository(db)
        val workout = WorkoutRepository(db, settings)
        val backup = BackupRepository(db, settings)

        val rid = routines.create("Push")
        routines.addExercise(rid, "ex_bench_press")
        val sid = workout.startSession(rid)
        for (s in db.sessionDao().getSets(sid)) {
            workout.editSet(s.id) { it.copy(weightKg = 40.0, reps = 10) }
            workout.completeSet(s.id)
        }
        workout.finish(sid, "ok")
        val json = backup.exportJson()

        val parsed = BackupCodec.parse(json) as BackupParseResult.Valid
        assertEquals(1, parsed.summary.sessions)

        // Se borra todo y se restaura.
        routines.delete(rid)
        db.sessionDao().delete(sid)
        assertNull(db.sessionDao().get(sid))
        backup.replaceAllWith(parsed.file)

        assertNotNull(db.sessionDao().get(sid))
        assertEquals(3, db.sessionDao().getSets(sid).count { it.completed })
        assertEquals(1, routines.getExercises(rid).size)
        db.close()
    }

    @Test fun invalid_backup_is_rejected_before_touching_data() {
        assertTrue(BackupCodec.parse("{\"format\":\"otra-cosa\"}") is BackupParseResult.Invalid)
    }
}
