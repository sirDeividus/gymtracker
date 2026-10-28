package com.gymtrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetRecordEntity::class,
        ProgressPhotoEntity::class,
        BodyWeightEntity::class,
    ],
    version = GymDatabase.VERSION,
    exportSchema = true,
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun bodyDao(): BodyDao

    companion object {
        const val VERSION = 2
        const val NAME = "gymtrack.db"
    }
}

/**
 * Migraciones del esquema. NUNCA se usa fallbackToDestructiveMigration: perder datos es un error crítico.
 *
 * Al cambiar una entidad:
 *  1. Sube GymDatabase.VERSION.
 *  2. Añade aquí un Migration(n, n+1) con el SQL equivalente.
 *  3. Añade la prueba en MigrationTest (androidTest) usando el esquema exportado en app/schemas.
 */
object Migrations {
    /**
     * v1 → v2: tipo de serie (calentamiento, drop set, al fallo) y tablas de fotos de progreso y peso corporal.
     * Solo añade: no toca ni borra ningún dato existente.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE set_records ADD COLUMN setType TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS progress_photos (" +
                    "id TEXT NOT NULL, takenAt INTEGER NOT NULL, pose TEXT NOT NULL, weightKg REAL, " +
                    "note TEXT NOT NULL, fileName TEXT NOT NULL, PRIMARY KEY(id))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_photos_takenAt ON progress_photos (takenAt)")
            db.execSQL("CREATE TABLE IF NOT EXISTS body_weights (id TEXT NOT NULL, measuredAt INTEGER NOT NULL, weightKg REAL NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_body_weights_measuredAt ON body_weights (measuredAt)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
