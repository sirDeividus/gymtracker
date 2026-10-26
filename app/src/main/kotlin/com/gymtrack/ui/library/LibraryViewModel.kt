package com.gymtrack.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.domain.Equipment
import com.gymtrack.domain.Exercise
import com.gymtrack.domain.ExerciseRecommender
import com.gymtrack.domain.MovementType
import com.gymtrack.domain.MuscleGroup
import com.gymtrack.domain.RecommendationPrefs
import com.gymtrack.domain.RecommendationSort
import com.gymtrack.domain.TrackingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryFilters(
    val query: String = "",
    val muscles: Set<MuscleGroup> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
    val sort: RecommendationSort = RecommendationSort.RELEVANCE,
)

data class LibraryState(
    val loading: Boolean = true,
    val items: List<Exercise> = emptyList(),
    val total: Int = 0,
    val usage: Map<String, Int> = emptyMap(),
    val filters: LibraryFilters = LibraryFilters(),
)

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    private val filters = MutableStateFlow(LibraryFilters())

    val state: StateFlow<LibraryState> = combine(c.exercises.exercises, c.history.usage, filters) { all, usage, f ->
        // Preferencias inferidas del uso real: equipamiento de los ejercicios más usados.
        val preferred = all.filter { (usage[it.id] ?: 0) > 0 }
            .groupBy { it.equipment }
            .mapValues { (_, list) -> list.sumOf { usage[it.id] ?: 0 } }
            .entries.sortedByDescending { it.value }.take(2).map { it.key }.toSet()
        val items = ExerciseRecommender.browse(
            all, f.query, f.muscles, f.equipment, f.sort,
            RecommendationPrefs(preferredEquipment = preferred, usage = usage),
        )
        LibraryState(false, items, all.size, usage, f)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default) // filtrar ~1.400 ejercicios no debe bloquear la interfaz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun toggleMuscle(m: MuscleGroup) = filters.update { it.copy(muscles = it.muscles.toggle(m)) }
    fun toggleEquipment(e: Equipment) = filters.update { it.copy(equipment = it.equipment.toggle(e)) }
    fun setSort(s: RecommendationSort) = filters.update { it.copy(sort = s) }
    fun clearFilters() = filters.update { LibraryFilters(sort = it.sort) }

    fun saveCustom(
        id: String?, name: String, primary: MuscleGroup, secondary: List<MuscleGroup>, equipment: Equipment,
        movement: MovementType, tracking: TrackingType, unilateral: Boolean, description: String, cues: String,
        video: java.io.File? = null, removeVideo: Boolean = false,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val r = c.exercises.saveCustom(id, name, primary, secondary, equipment, movement, tracking, unilateral, description, cues)
            r.getOrNull()?.let { ex ->
                when {
                    video != null -> c.media.attachVideo(ex.id, video)
                    removeVideo -> c.media.removeVideo(ex.id)
                }
            }
            if (r.isFailure) video?.delete()
            onResult(r.exceptionOrNull()?.message)
        }
    }

    fun deleteCustom(id: String) {
        viewModelScope.launch { c.exercises.deleteCustom(id); c.media.removeVideo(id) }
    }

    private fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x
}
