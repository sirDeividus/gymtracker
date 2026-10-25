package com.gymtrack.domain

data class TemplateItem(
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int = 90,
    val durationSec: Int? = null,
)

data class RoutineTemplate(
    val name: String,
    val description: String,
    val items: List<TemplateItem>,
    /** Días sugeridos (el usuario puede cambiarlos). */
    val days: Set<java.time.DayOfWeek> = emptySet(),
)

/** Puntos de partida editables, construidos solo con ejercicios de la biblioteca. No son obligatorios. */
object RoutineTemplates {
    val all: List<RoutineTemplate> = listOf(
        RoutineTemplate(
            "Push", "Pecho, hombros y tríceps",
            listOf(
                TemplateItem("ex_bench_press", 4, 6, 8, 120),
                TemplateItem("ex_incline_dumbbell_press", 3, 8, 10),
                TemplateItem("ex_overhead_press", 3, 6, 8, 120),
                TemplateItem("ex_lateral_raise", 3, 12, 15, 60),
                TemplateItem("ex_triceps_pushdown", 3, 10, 12, 60),
                TemplateItem("ex_overhead_triceps_extension", 3, 10, 12, 60),
            ),
        ),
        RoutineTemplate(
            "Pull", "Espalda y bíceps",
            listOf(
                TemplateItem("ex_pull_up", 4, 6, 10, 120),
                TemplateItem("ex_barbell_row", 3, 8, 10, 120),
                TemplateItem("ex_lat_pulldown", 3, 10, 12),
                TemplateItem("ex_face_pull", 3, 12, 15, 60),
                TemplateItem("ex_barbell_curl", 3, 8, 12, 60),
                TemplateItem("ex_hammer_curl", 3, 10, 12, 60),
            ),
        ),
        RoutineTemplate(
            "Piernas", "Cuádriceps, isquiotibiales, glúteos y gemelos",
            listOf(
                TemplateItem("ex_back_squat", 4, 6, 8, 150),
                TemplateItem("ex_romanian_deadlift", 3, 8, 10, 120),
                TemplateItem("ex_leg_press", 3, 10, 12),
                TemplateItem("ex_lying_leg_curl", 3, 10, 12, 60),
                TemplateItem("ex_standing_calf_raise", 4, 12, 15, 60),
                TemplateItem("ex_plank", 3, 1, 1, 60, durationSec = 45),
            ),
        ),
        RoutineTemplate(
            "Torso", "Empuje y tirón de tren superior",
            listOf(
                TemplateItem("ex_bench_press", 3, 6, 8, 120),
                TemplateItem("ex_barbell_row", 3, 6, 8, 120),
                TemplateItem("ex_dumbbell_shoulder_press", 3, 8, 10),
                TemplateItem("ex_lat_pulldown", 3, 10, 12),
                TemplateItem("ex_dumbbell_curl", 2, 10, 12, 60),
                TemplateItem("ex_triceps_pushdown", 2, 10, 12, 60),
            ),
        ),
        RoutineTemplate(
            "Full Body", "Cuerpo completo",
            listOf(
                TemplateItem("ex_back_squat", 3, 8, 10, 120),
                TemplateItem("ex_bench_press", 3, 8, 10, 120),
                TemplateItem("ex_barbell_row", 3, 8, 10, 120),
                TemplateItem("ex_overhead_press", 3, 8, 10),
                TemplateItem("ex_romanian_deadlift", 2, 8, 10),
                TemplateItem("ex_plank", 3, 1, 1, 60, durationSec = 30),
            ),
        ),
        RoutineTemplate(
            "Tabata + cardio", "Abdominales en intervalos (20 s / 10 s × 8) y carrera en caminadora",
            listOf(
                TemplateItem("ex_tabata_abs", 8, 1, 1, restSec = 10, durationSec = 20),
                TemplateItem("ex_treadmill_run", 1, 1, 1, restSec = 0),
            ),
            days = setOf(java.time.DayOfWeek.WEDNESDAY),
        ),
    )
}
