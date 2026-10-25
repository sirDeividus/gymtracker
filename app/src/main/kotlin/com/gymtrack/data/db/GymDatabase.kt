package com.gymtrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetRecordEntity::class,
    ],
    version = GymDatabase.VERSION,
    exportSchema = true,
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val VERSION = 1
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
    val ALL: Array<Migration> = arrayOf()
}
