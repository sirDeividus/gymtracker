#!/usr/bin/env python3
"""Genera app/src/main/assets/exercise_dataset.json a partir de hasaneyldrm/exercises-dataset.

Solo se extraen DATOS (licencia MIT). Las imágenes y GIFs son © Gym visual y NO se incluyen.

Uso:
  git clone --depth 1 https://github.com/hasaneyldrm/exercises-dataset /ruta/exercises-dataset
  python3 tools/build_dataset_asset.py /ruta/exercises-dataset/data/exercises.json app/src/main/assets/exercise_dataset.json
"""
import json
import sys

src, dst = sys.argv[1], sys.argv[2]
data = json.load(open(src, encoding="utf-8"))
out = []
for e in data:
    steps = (e.get("instruction_steps") or {}).get("es") or []
    if not steps:
        text = (e.get("instructions") or {}).get("es")
        steps = [text] if text else []
    out.append({
        "id": e["id"],
        "name": e["name"],
        "target": e.get("target", ""),
        "equipment": e.get("equipment", ""),
        "secondary": e.get("secondary_muscles") or [],
        "steps": steps,
    })
doc = {
    "source": "https://github.com/hasaneyldrm/exercises-dataset",
    "license": "MIT",
    "copyright": "Copyright (c) 2026 Hasan Emir Yıldırım",
    "mediaNotice": "Las imágenes y animaciones del dataset original son (c) Gym visual y no se incluyen en esta aplicación.",
    "version": 1,
    "exercises": out,
}
with open(dst, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
print(f"{len(out)} ejercicios -> {dst}")
