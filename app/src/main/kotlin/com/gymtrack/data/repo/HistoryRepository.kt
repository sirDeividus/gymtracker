package com.gymtrack.data.repo

import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.HistorySetRow
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.domain.ExercisePerformance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(db: GymDatabase) {
    private val dao = db.sessionDao()

    /** Sesiones finalizadas, más recientes primero. Las descartadas y la que está en curso no aparecen. */
    val sessions: Flow<List<SessionEntity>> = dao.observeFinished()

    /** Ejercicios con al menos una serie completada, por sesión. */
    val exerciseCounts: Flow<Map<String, Int>> =
        dao.observeExerciseCounts().map { list -> list.associate { it.sessionId to it.exerciseCount } }

    /** Todas las series completadas de sesiones finalizadas (para estadísticas y progreso). */
    val allRows: Flow<List<HistorySetRow>> = dao.observeAllHistory()

    /** Veces que se ha usado cada ejercicio (para ordenar recomendaciones). */
    val usage: Flow<Map<String, Int>> = dao.observeUsage().map { l -> l.associate { it.exerciseId to it.uses } }

    fun performances(exerciseId: String): Flow<List<ExercisePerformance>> =
        dao.observeHistoryForExercise(exerciseId).map { groupPerformances(it) }
}
