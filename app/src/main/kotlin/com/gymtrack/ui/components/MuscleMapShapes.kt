package com.gymtrack.ui.components

import com.gymtrack.domain.MuscleGroup

// GENERADO por tools/gen_muscle_map.py — no editar a mano. Dibujo original (sin recursos de terceros).
// Lienzo de 100 x 200 unidades.

internal sealed interface MapShape {
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : MapShape
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : MapShape
}

/** [muscle] null = silueta base; si no, zona que se colorea según las series recientes de ese grupo. */
internal class MapZone(val muscle: MuscleGroup?, val shapes: List<MapShape>)

internal object MuscleMapShapes {
    val front: List<MapZone> = listOf(
        MapZone(null, listOf(MapShape.Oval(50f, 13f, 8.5f, 10.5f), MapShape.Rect(45f, 22f, 10f, 9f, 4f))),
        MapZone(null, listOf(MapShape.Rect(29f, 31f, 42f, 62f, 15f))),
        MapZone(null, listOf(MapShape.Rect(16f, 40f, 12f, 56f, 6f), MapShape.Rect(72f, 40f, 12f, 56f, 6f))),
        MapZone(null, listOf(MapShape.Oval(18.5f, 98f, 5.5f, 6.5f), MapShape.Oval(81.5f, 98f, 5.5f, 6.5f))),
        MapZone(null, listOf(MapShape.Rect(31f, 90f, 38f, 18f, 8f))),
        MapZone(null, listOf(MapShape.Rect(31f, 104f, 18f, 46f, 8f), MapShape.Rect(51f, 104f, 18f, 46f, 8f))),
        MapZone(null, listOf(MapShape.Rect(33f, 150f, 14f, 40f, 6f), MapShape.Rect(53f, 150f, 14f, 40f, 6f))),
        MapZone(null, listOf(MapShape.Oval(38f, 194f, 8f, 4.5f), MapShape.Oval(62f, 194f, 8f, 4.5f))),
        MapZone(MuscleGroup.SHOULDERS, listOf(MapShape.Oval(27f, 39f, 9f, 9f), MapShape.Oval(73f, 39f, 9f, 9f))),
        MapZone(MuscleGroup.CHEST, listOf(MapShape.Rect(33f, 41f, 16.5f, 15f, 7f), MapShape.Rect(50.5f, 41f, 16.5f, 15f, 7f))),
        MapZone(MuscleGroup.BICEPS, listOf(MapShape.Rect(17f, 47f, 10f, 21f, 5f), MapShape.Rect(73f, 47f, 10f, 21f, 5f))),
        MapZone(MuscleGroup.FOREARMS, listOf(MapShape.Rect(16f, 70f, 9.5f, 24f, 4.5f), MapShape.Rect(74.5f, 70f, 9.5f, 24f, 4.5f))),
        MapZone(MuscleGroup.CORE, listOf(MapShape.Rect(41.5f, 58f, 8f, 8f, 3f), MapShape.Rect(50.5f, 58f, 8f, 8f, 3f), MapShape.Rect(41.5f, 67f, 8f, 8f, 3f), MapShape.Rect(50.5f, 67f, 8f, 8f, 3f), MapShape.Rect(41.5f, 76f, 8f, 8f, 3f), MapShape.Rect(50.5f, 76f, 8f, 8f, 3f), MapShape.Rect(42.5f, 85f, 15f, 6f, 3f))),
        MapZone(MuscleGroup.QUADS, listOf(MapShape.Rect(32f, 106f, 16f, 42f, 8f), MapShape.Rect(52f, 106f, 16f, 42f, 8f))),
        MapZone(MuscleGroup.CALVES, listOf(MapShape.Rect(34f, 152f, 12f, 35f, 6f), MapShape.Rect(54f, 152f, 12f, 35f, 6f))),
    )

    val back: List<MapZone> = listOf(
        MapZone(null, listOf(MapShape.Oval(50f, 13f, 8.5f, 10.5f), MapShape.Rect(45f, 22f, 10f, 9f, 4f))),
        MapZone(null, listOf(MapShape.Rect(29f, 31f, 42f, 62f, 15f))),
        MapZone(null, listOf(MapShape.Rect(16f, 40f, 12f, 56f, 6f), MapShape.Rect(72f, 40f, 12f, 56f, 6f))),
        MapZone(null, listOf(MapShape.Oval(18.5f, 98f, 5.5f, 6.5f), MapShape.Oval(81.5f, 98f, 5.5f, 6.5f))),
        MapZone(null, listOf(MapShape.Rect(31f, 90f, 38f, 18f, 8f))),
        MapZone(null, listOf(MapShape.Rect(31f, 104f, 18f, 46f, 8f), MapShape.Rect(51f, 104f, 18f, 46f, 8f))),
        MapZone(null, listOf(MapShape.Rect(33f, 150f, 14f, 40f, 6f), MapShape.Rect(53f, 150f, 14f, 40f, 6f))),
        MapZone(null, listOf(MapShape.Oval(38f, 194f, 8f, 4.5f), MapShape.Oval(62f, 194f, 8f, 4.5f))),
        MapZone(MuscleGroup.SHOULDERS, listOf(MapShape.Oval(27f, 39f, 9f, 9f), MapShape.Oval(73f, 39f, 9f, 9f))),
        MapZone(MuscleGroup.BACK, listOf(MapShape.Rect(39f, 31f, 22f, 13f, 6f), MapShape.Rect(32f, 45f, 16f, 27f, 8f), MapShape.Rect(52f, 45f, 16f, 27f, 8f), MapShape.Rect(41f, 73f, 18f, 16f, 6f))),
        MapZone(MuscleGroup.TRICEPS, listOf(MapShape.Rect(17f, 47f, 10f, 21f, 5f), MapShape.Rect(73f, 47f, 10f, 21f, 5f))),
        MapZone(MuscleGroup.FOREARMS, listOf(MapShape.Rect(16f, 70f, 9.5f, 24f, 4.5f), MapShape.Rect(74.5f, 70f, 9.5f, 24f, 4.5f))),
        MapZone(MuscleGroup.GLUTES, listOf(MapShape.Oval(41f, 99f, 10.5f, 9.5f), MapShape.Oval(59f, 99f, 10.5f, 9.5f))),
        MapZone(MuscleGroup.HAMSTRINGS, listOf(MapShape.Rect(32f, 110f, 16f, 38f, 8f), MapShape.Rect(52f, 110f, 16f, 38f, 8f))),
        MapZone(MuscleGroup.CALVES, listOf(MapShape.Rect(34f, 152f, 12f, 35f, 6f), MapShape.Rect(54f, 152f, 12f, 35f, 6f))),
    )
}
