#!/usr/bin/env python3
"""Genera MuscleMapShapes.kt (mapa corporal propio, sin recursos de terceros) y una vista previa PNG.

El dibujo es original: formas geométricas sencillas en un lienzo de 100 x 200. Cada zona se asocia a un
grupo muscular de la app; la app las colorea según las series recientes.

  python3 tools/gen_muscle_map.py <salida.png>      # además escribe app/.../ui/components/MuscleMapShapes.kt
"""
import os
import sys

# Formas: ("o", cx, cy, rx, ry) óvalo · ("r", x, y, w, h, radio) rectángulo redondeado.
# Grupo muscular None = silueta base (no se colorea).
FRONT = [
    (None, [("o", 50, 13, 8.5, 10.5), ("r", 45, 22, 10, 9, 4)]),                                    # cabeza y cuello
    (None, [("r", 29, 31, 42, 62, 15)]),                                                              # tronco
    (None, [("r", 16, 40, 12, 56, 6), ("r", 72, 40, 12, 56, 6)]),                                     # brazos
    (None, [("o", 18.5, 98, 5.5, 6.5), ("o", 81.5, 98, 5.5, 6.5)]),                                   # manos
    (None, [("r", 31, 90, 38, 18, 8)]),                                                               # pelvis
    (None, [("r", 31, 104, 18, 46, 8), ("r", 51, 104, 18, 46, 8)]),                                   # muslos base
    (None, [("r", 33, 150, 14, 40, 6), ("r", 53, 150, 14, 40, 6)]),                                   # piernas base
    (None, [("o", 38, 194, 8, 4.5), ("o", 62, 194, 8, 4.5)]),                                         # pies
    ("SHOULDERS", [("o", 27, 39, 9, 9), ("o", 73, 39, 9, 9)]),
    ("CHEST", [("r", 33, 41, 16.5, 15, 7), ("r", 50.5, 41, 16.5, 15, 7)]),
    ("BICEPS", [("r", 17, 47, 10, 21, 5), ("r", 73, 47, 10, 21, 5)]),
    ("FOREARMS", [("r", 16, 70, 9.5, 24, 4.5), ("r", 74.5, 70, 9.5, 24, 4.5)]),
    ("CORE", [("r", 41.5, 58, 8, 8, 3), ("r", 50.5, 58, 8, 8, 3), ("r", 41.5, 67, 8, 8, 3), ("r", 50.5, 67, 8, 8, 3),
              ("r", 41.5, 76, 8, 8, 3), ("r", 50.5, 76, 8, 8, 3), ("r", 42.5, 85, 15, 6, 3)]),
    ("QUADS", [("r", 32, 106, 16, 42, 8), ("r", 52, 106, 16, 42, 8)]),
    ("CALVES", [("r", 34, 152, 12, 35, 6), ("r", 54, 152, 12, 35, 6)]),
]
BACK = [
    (None, [("o", 50, 13, 8.5, 10.5), ("r", 45, 22, 10, 9, 4)]),
    (None, [("r", 29, 31, 42, 62, 15)]),
    (None, [("r", 16, 40, 12, 56, 6), ("r", 72, 40, 12, 56, 6)]),
    (None, [("o", 18.5, 98, 5.5, 6.5), ("o", 81.5, 98, 5.5, 6.5)]),
    (None, [("r", 31, 90, 38, 18, 8)]),
    (None, [("r", 31, 104, 18, 46, 8), ("r", 51, 104, 18, 46, 8)]),
    (None, [("r", 33, 150, 14, 40, 6), ("r", 53, 150, 14, 40, 6)]),
    (None, [("o", 38, 194, 8, 4.5), ("o", 62, 194, 8, 4.5)]),
    ("SHOULDERS", [("o", 27, 39, 9, 9), ("o", 73, 39, 9, 9)]),
    ("BACK", [("r", 39, 31, 22, 13, 6), ("r", 32, 45, 16, 27, 8), ("r", 52, 45, 16, 27, 8), ("r", 41, 73, 18, 16, 6)]),
    ("TRICEPS", [("r", 17, 47, 10, 21, 5), ("r", 73, 47, 10, 21, 5)]),
    ("FOREARMS", [("r", 16, 70, 9.5, 24, 4.5), ("r", 74.5, 70, 9.5, 24, 4.5)]),
    ("GLUTES", [("o", 41, 99, 10.5, 9.5), ("o", 59, 99, 10.5, 9.5)]),
    ("HAMSTRINGS", [("r", 32, 110, 16, 38, 8), ("r", 52, 110, 16, 38, 8)]),
    ("CALVES", [("r", 34, 152, 12, 35, 6), ("r", 54, 152, 12, 35, 6)]),
]


def kotlin_shape(s):
    if s[0] == "o":
        return "MapShape.Oval(%sf, %sf, %sf, %sf)" % s[1:]
    return "MapShape.Rect(%sf, %sf, %sf, %sf, %sf)" % s[1:]


def kotlin_list(name, zones):
    out = ["    val %s: List<MapZone> = listOf(" % name]
    for muscle, shapes in zones:
        m = "null" if muscle is None else "MuscleGroup." + muscle
        out.append("        MapZone(%s, listOf(%s))," % (m, ", ".join(kotlin_shape(s) for s in shapes)))
    out.append("    )")
    return "\n".join(out)


def write_kotlin(path):
    src = """package com.gymtrack.ui.components

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
%s

%s
}
""" % (kotlin_list("front", FRONT), kotlin_list("back", BACK))
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)


def preview(path):
    from PIL import Image, ImageDraw
    S = 4
    bg, base, outline, red = (17, 15, 15), (38, 33, 33), (52, 46, 46), (255, 59, 59)
    levels = {"CHEST": 1.0, "SHOULDERS": 0.7, "BICEPS": 0.5, "TRICEPS": 0.4, "CORE": 0.85, "QUADS": 0.6,
              "BACK": 0.9, "GLUTES": 0.3, "HAMSTRINGS": 0.2, "CALVES": 0.0, "FOREARMS": 0.15}
    img = Image.new("RGB", (2 * 100 * S + 60, 200 * S + 40), bg)
    d = ImageDraw.Draw(img)
    for vi, zones in enumerate((FRONT, BACK)):
        ox = 20 + vi * (100 * S + 20)
        for muscle, shapes in zones:
            t = levels.get(muscle, 0) if muscle else 0
            if muscle is None:
                col = base
            elif t <= 0:
                col = outline
            else:
                a = 0.25 + 0.75 * t
                col = tuple(int(outline[i] * (1 - a) + red[i] * a) for i in range(3))
            for s in shapes:
                if s[0] == "o":
                    _, cx, cy, rx, ry = s
                    d.ellipse([ox + (cx - rx) * S, 20 + (cy - ry) * S, ox + (cx + rx) * S, 20 + (cy + ry) * S], fill=col)
                else:
                    _, x, y, w, h, r = s
                    d.rounded_rectangle([ox + x * S, 20 + y * S, ox + (x + w) * S, 20 + (y + h) * S], radius=r * S, fill=col)
    img.save(path)


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    write_kotlin(os.path.join(root, "app/src/main/kotlin/com/gymtrack/ui/components/MuscleMapShapes.kt"))
    if len(sys.argv) > 1:
        preview(sys.argv[1])
    print("ok")
