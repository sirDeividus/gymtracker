package com.gymtrack

import android.content.Context
import android.os.Build
import androidx.room.Room
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.gymtrack.data.media.MediaRepository
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.Migrations
import com.gymtrack.data.repo.BackupRepository
import com.gymtrack.data.repo.ExerciseRepository
import com.gymtrack.data.repo.HistoryRepository
import com.gymtrack.data.repo.RoutineRepository
import com.gymtrack.data.repo.WorkoutRepository
import com.gymtrack.data.settings.SettingsStore
import com.gymtrack.notify.RestNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Inyección de dependencias manual: simple, explícita y sin generación de código. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: GymDatabase = Room.databaseBuilder(context, GymDatabase::class.java, GymDatabase.NAME)
        .addMigrations(*Migrations.ALL)
        .build() // sin fallbackToDestructiveMigration: perder datos es un error crítico

    val settings = SettingsStore(context)
    val exercises = ExerciseRepository(database.exerciseDao())
    val routines = RoutineRepository(database)
    val workout = WorkoutRepository(database, settings)
    val history = HistoryRepository(database)
    val backup = BackupRepository(database, settings)
    val media = MediaRepository(context)

    /** Carga imágenes y GIF animados desde assets (sin red). */
    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()

    init {
        RestNotifier.ensureChannel(context)
        appScope.launch {
            exercises.seedLibrary()
            syncLibrary()
        }
        // Mantiene la alarma de fin de descanso sincronizada con el temporizador guardado.
        appScope.launch {
            workout.activeSession.map { it?.restEndsAt }.distinctUntilChanged().collect { RestNotifier.sync(context, it) }
        }
    }

    /** Aplica el ajuste «Biblioteca ampliada»: importa una vez y después muestra u oculta. */
    suspend fun syncLibrary() {
        val s = settings.current()
        val installed = exercises.syncExtendedLibrary(s.extendedLibrary, s.datasetVersion) {
            runCatching { context.assets.open("exercise_dataset.json").bufferedReader().use { it.readText() } }.getOrNull()
        }
        if (installed != s.datasetVersion) settings.update { it.copy(datasetVersion = installed) }
    }
}
