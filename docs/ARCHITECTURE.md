# Arquitectura de GymTrack

## Capas

```
┌──────────────────────────── :app (Android) ─────────────────────────────┐
│ ui/        Compose + Material 3. Pantallas sin lógica de negocio.        │
│            ViewModels → StateFlow<UiState> (MVVM)                        │
│ data/      Room (entidades, DAOs), repositorios, DataStore (ajustes),    │
│            MediaRepository (imágenes opcionales), BackupRepository       │
│ notify/    Aviso de fin de descanso (AlarmManager inexacta + canal)      │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ dependencia  com.gymtrack:domain
┌────────────────────────────────▼─────────────────────────────────────────┐
│ domain/  (Kotlin puro, build independiente, probado con JUnit)           │
│  ProgressionEngine · ExerciseBests/PersonalRecords · SessionPlanner      │
│  SetRules · RestTimerState · Stats · RoutineRecommender · Backup*        │
│  ExerciseLibrary (92 ejercicios revisados) · ExerciseDataset (mapper)    │
└──────────────────────────────────────────────────────────────────────────┘
```

Decisión clave: **toda la lógica que decide algo** (qué peso sugerir, si hay récord, qué cuenta como
volumen, cuántas series crear, cómo se valida una serie, estado del temporizador, validación de copias)
vive en `domain`, sin dependencias de Android, para poder probarla de verdad. Los repositorios solo
orquestan Room + dominio, y la UI solo pinta estado.

Inyección de dependencias manual (`AppContainer`): explícita, sin generación de código.

## Modelo de datos (Room, `GymDatabase` v1)

```
exercises ──────────────(sin FK; nunca se borra un ejercicio usado: se archiva)
routines 1──* routine_exercises            ← DEFINICIÓN (editable)
─────────────────────────────────────────────────────────────────
sessions 1──* session_exercises 1──* set_records   ← RESULTADOS (histórico)
   (sin FK a routines)   (instantánea del ejercicio)   (con instantánea "anterior")
```

* **Separación definición/resultado**: `sessions` no tiene clave foránea a `routines`. Editar o borrar una rutina
  no toca el historial (`routineName` es una instantánea).
* **Instantáneas**: `session_exercises` copia nombre, músculo, equipamiento, método de registro, objetivos y la
  sugerencia calculada al empezar. `set_records` copia la serie equivalente anterior (`prev*`), así la columna
  ANTERIOR es estable y siempre procede del **mismo ejercicio por id**, nunca de uno de nombre parecido.
* **Cascadas**: borrar una sesión borra sus ejercicios y series (transacción de Room).
* **Estados** de sesión: `IN_PROGRESS` (como máximo una), `FINISHED`, `DISCARDED`.
* **Temporizador**: marcas de tiempo absolutas (`restEndsAt`, `restPausedRemainingMs`) en la fila de la sesión.
  Sobrevive a que Android mate el proceso.
* **Pesos** siempre en kg; la unidad (kg/lb) solo afecta a cómo se muestra y se introduce.
* **Ajustes** (`UserSettings`) en DataStore. Sin `fallbackToDestructiveMigration`: ver `Migrations.kt`.

### Persistencia y recuperación
Cada toque es una escritura en Room (no hay "guardar"). La sesión en curso se crea al pulsar ENTRENAR con todas
sus series; al volver a abrir la app se observa `status = IN_PROGRESS` y se retoma exactamente donde estaba.

## Flujo de navegación

```
Barra inferior:  Inicio · Rutinas · Historial · Progreso
                    │        │          │          │
Inicio ──ENTRENAR──►│◄───────┘          │          └─► Ejercicio (evolución)
   │  ▲  (diálogo: rutina recomendada / otra / libre)
   │  └── banner "CONTINUAR" en todas las pestañas si hay sesión en curso
   ▼
Entrenamiento activo ──Finalizar──► Resumen ──► Detalle de sesión ──► Ejercicio
   │ (descartar con confirmación)
Rutinas ──► Editar rutina (autoguardado, arrastrar para reordenar, copiar de otra)
Inicio ──► Ajustes ──► Biblioteca de ejercicios / copia de seguridad / licencias
```

## Progresión

`ProgressionEngine.suggest` usa solo el último rendimiento real del ejercicio:

| Situación | Sugerencia |
|---|---|
| Sin historial | "Primera vez" (nada inventado) |
| Todas las series en el tope del rango y esfuerzo moderado | Subir: `paso × ⌈2 % del peso / paso⌉` (mínimo un paso) |
| Dentro del rango pero no todas en el tope | Mantener y buscar el tope |
| Alguna serie bajo el mínimo | Mantener o valorar si es demasiado exigente |
| RPE medio ≥ 9 (≥ 9,5 siempre), dolor o técnica deteriorada | Mantener, aunque se hayan hecho las reps |
| Peso corporal | Más reps / variante / reducir asistencia / lastre |
| Cronometrado | +5 s |

La sugerencia **nunca** se aplica sola: el usuario pulsa "Aplicar" o la ignora. Siempre lleva su motivo.
