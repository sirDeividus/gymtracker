package com.gymtrack.domain

enum class RecommendationSort(val label: String) {
    RELEVANCE("Relevancia"),
    EQUIPMENT("Equipamiento"),
    NAME("Nombre"),
}

data class RecommendationPrefs(
    val preferredEquipment: Set<Equipment> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    /** Veces que el usuario ha usado cada ejercicio (historial real). */
    val usage: Map<String, Int> = emptyMap(),
)

object ExerciseRecommender {
    /**
     * Sugerencias editables para un grupo muscular: nunca obligatorias.
     * Relevancia: favoritos > uso real > equipamiento preferido > compuestos antes que aislamiento.
     */
    fun recommend(
        library: List<Exercise>,
        muscle: MuscleGroup,
        prefs: RecommendationPrefs = RecommendationPrefs(),
        sort: RecommendationSort = RecommendationSort.RELEVANCE,
        equipment: Set<Equipment> = emptySet(),
    ): List<Exercise> {
        val candidates = library.filter {
            it.primaryMuscle == muscle && (equipment.isEmpty() || it.equipment in equipment)
        }
        return when (sort) {
            RecommendationSort.NAME -> candidates.sortedBy { it.name }
            RecommendationSort.EQUIPMENT ->
                candidates.sortedWith(compareBy<Exercise>({ it.equipment.ordinal }, { it.name }))
            RecommendationSort.RELEVANCE -> candidates.sortedWith(
                compareByDescending<Exercise> { it.id in prefs.favoriteIds }
                    .thenByDescending { prefs.usage[it.id] ?: 0 }
                    .thenByDescending { it.equipment in prefs.preferredEquipment }
                    .thenByDescending { it.movement != MovementType.ISOLATION }
                    .thenBy { it.name },
            )
        }
    }

    /** Búsqueda instantánea tolerante a mayúsculas y acentos. */
    fun search(library: List<Exercise>, query: String): List<Exercise> {
        val q = normalize(query)
        if (q.isBlank()) return library
        return library.filter { normalize(it.name).contains(q) }
    }

    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").trim()
}
