#!/usr/bin/env python3
"""Medios de ejercicios (imágenes y GIF) para USO PERSONAL.

Las imágenes y animaciones de hasaneyldrm/exercises-dataset son (c) Gym visual (https://gymvisual.com/),
NO están cubiertas por la licencia MIT y se rigen por sus Términos y Condiciones. Por eso:
  * este repositorio NO incluye los archivos multimedia (app/src/main/assets/media/ está en .gitignore);
  * solo se versiona el índice ejercicio -> nombre de archivo (assets/media_index.json);
  * la app muestra siempre la atribución "(c) Gym visual - https://gymvisual.com/".

Uso:
  git clone --depth 1 https://github.com/hasaneyldrm/exercises-dataset /ruta/exercises-dataset
  python3 tools/media.py index /ruta/exercises-dataset     # regenera el índice (se versiona)
  python3 tools/media.py copy  /ruta/exercises-dataset     # copia imágenes + GIF al proyecto (NO se versiona)
  python3 tools/media.py copy  /ruta/exercises-dataset --no-gifs   # solo miniaturas (~9 MB en vez de ~137 MB)
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX = os.path.join(ROOT, "app/src/main/assets/media_index.json")
MEDIA = os.path.join(ROOT, "app/src/main/assets/media")

# Ejercicios de la biblioteca básica (ids "ex_*") -> nombre exacto en el dataset.
# Sin entrada = no hay un equivalente exacto, y es mejor no mostrar nada que una demostración equivocada.
CURATED = {
    "bench_press": "barbell bench press", "incline_bench_press": "barbell incline bench press",
    "dumbbell_bench_press": "dumbbell bench press", "incline_dumbbell_press": "dumbbell incline bench press",
    "machine_chest_press": "lever chest press", "dumbbell_fly": "dumbbell fly",
    "cable_crossover": "cable standing up straight crossovers", "pec_deck": "lever seated fly",
    "push_up": "push-up", "incline_push_up": "incline push-up", "decline_push_up": "decline push-up",
    "chest_dip": "chest dip", "pull_up": "pull-up", "chin_up": "chin-up", "assisted_pull_up": "assisted pull-up",
    "lat_pulldown": "cable lat pulldown full range of motion", "barbell_row": "barbell bent over row",
    "dumbbell_row": "dumbbell one arm bent-over row", "seated_cable_row": "cable seated row",
    "machine_row": "lever seated row", "tbar_row": "lever t bar row", "straight_arm_pulldown": "cable straight arm pulldown",
    "deadlift": "barbell deadlift", "back_extension": "hyperextension",
    "dumbbell_shoulder_press": "dumbbell seated shoulder press", "machine_shoulder_press": "lever shoulder press",
    "lateral_raise": "dumbbell lateral raise", "cable_lateral_raise": "cable one arm lateral raise",
    "rear_delt_fly": "dumbbell rear lateral raise", "front_raise": "dumbbell front raise", "upright_row": "barbell upright row",
    "barbell_curl": "barbell curl", "ez_bar_curl": "ez barbell curl", "dumbbell_curl": "dumbbell biceps curl",
    "hammer_curl": "dumbbell hammer curl", "incline_dumbbell_curl": "dumbbell incline curl",
    "preacher_curl": "barbell preacher curl", "cable_curl": "cable curl", "close_grip_bench": "barbell close-grip bench press",
    "triceps_pushdown": "cable pushdown (with rope attachment)",
    "skullcrusher": "barbell lying triceps extension skull crusher",
    "overhead_triceps_extension": "dumbbell standing triceps extension", "triceps_dip": "triceps dip",
    "wrist_curl": "barbell palms up wrist curl over a bench", "reverse_wrist_curl": "dumbbell reverse wrist curl",
    "farmer_carry": "farmers walk", "back_squat": "barbell full squat", "front_squat": "barbell front squat",
    "goblet_squat": "dumbbell goblet squat", "leg_press": "sled 45° leg press (side pov)",
    "hack_squat": "sled hack squat", "leg_extension": "lever leg extension", "walking_lunge": "dumbbell lunge",
    "bulgarian_split_squat": "dumbbell single leg split squat", "romanian_deadlift": "barbell romanian deadlift",
    "dumbbell_rdl": "dumbbell romanian deadlift", "lying_leg_curl": "lever lying leg curl",
    "seated_leg_curl": "lever seated leg curl", "good_morning": "barbell good morning", "hip_thrust": "barbell glute bridge",
    "glute_bridge": "low glute bridge on floor", "cable_kickback": "cable kickback", "step_up": "dumbbell step-up",
    "sumo_deadlift": "barbell sumo deadlift", "hip_abduction": "lever seated hip abduction",
    "standing_calf_raise": "lever standing calf raise", "seated_calf_raise": "lever seated calf raise",
    "crunch": "crunch floor", "hanging_leg_raise": "hanging straight leg raise", "cable_crunch": "cable kneeling crunch",
    "ab_wheel": "wheel rollerout", "russian_twist": "russian twist", "dead_bug": "dead bug",
    "kettlebell_swing": "kettlebell swing", "burpee": "burpee", "running": "run", "treadmill_run": "run",
}


def load(dataset_dir):
    with open(os.path.join(dataset_dir, "data/exercises.json"), encoding="utf-8") as f:
        return json.load(f)


def base(entry):
    return os.path.splitext(os.path.basename(entry["image"]))[0]


def cmd_index(dataset_dir):
    data = load(dataset_dir)
    by_name = {e["name"]: e for e in data}
    index = {"ds_" + e["id"]: base(e) for e in data}
    missing = []
    for key, name in CURATED.items():
        e = by_name.get(name)
        if e is None:
            missing.append((key, name))
        else:
            index["ex_" + key] = base(e)
    if missing:
        sys.exit("Nombres que no existen en el dataset: %s" % missing)
    with open(INDEX, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    print("%d ejercicios con medio (%d básicos) -> %s" % (len(index), len(CURATED), INDEX))


def cmd_copy(dataset_dir, gifs):
    with open(INDEX, encoding="utf-8") as f:
        index = json.load(f)
    names = sorted(set(index.values()))
    for sub in ("images", "videos"):
        os.makedirs(os.path.join(MEDIA, sub), exist_ok=True)
    n_img = n_gif = 0
    for b in names:
        src = os.path.join(dataset_dir, "images", b + ".jpg")
        if os.path.exists(src):
            shutil.copyfile(src, os.path.join(MEDIA, "images", b + ".jpg"))
            n_img += 1
        if gifs:
            src = os.path.join(dataset_dir, "videos", b + ".gif")
            if os.path.exists(src):
                shutil.copyfile(src, os.path.join(MEDIA, "videos", b + ".gif"))
                n_gif += 1
    print("Copiados %d imágenes y %d GIF a %s (no se versionan)" % (n_img, n_gif, MEDIA))


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2 or args[0] not in ("index", "copy"):
        sys.exit(__doc__)
    if args[0] == "index":
        cmd_index(args[1])
    else:
        cmd_copy(args[1], "--no-gifs" not in sys.argv)
