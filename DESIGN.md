# Directrices de diseño — GymTrack

Minimalismo oscuro, tarjetas tipo Bento y un único acento de alta energía. Los valores viven en
`app/src/main/res/values/brand.xml` (tokens); no se escriben colores sueltos en las pantallas.

## 1. Paleta (fuerza y contraste)

| Token (`brand.xml`) | Valor | Uso |
|---|---|---|
| `brand_background` | `#09090B` | Fondo (negro zinc; ahorra batería en OLED) |
| `brand_surface` | `#18181B` | Tarjetas |
| `brand_outline` | `#27272A` | Bordes sutiles |
| `brand_silhouette` | `#2D2D30` | Silueta del mapa de músculos |
| `brand_surface_high` | `#1C1C1F` | Campos, diálogos, fondos de estado |
| `brand_primary` | `#FF4500` | **Acento**: solo acción principal, temporizadores y racha |
| `brand_on_primary` | `#09090B` | Texto sobre el acento (contraste 6,3:1; con blanco sería 3,2:1) |
| `brand_text` / `brand_text_secondary` | `#FAFAFA` / `#A1A1AA` | Texto (≥ 4,5:1 sobre tarjetas) |

Acento alternativo: **Volt `#CCFF00`** o el rojo `#FF1E46` — basta cambiar `brand_primary` (el texto oscuro encima sigue valiendo).
Sin degradados.

## 2. Tipografía y jerarquía
* Títulos de ejercicios y rutinas: sans-serif pesada (`FontWeight.Black`) y **en mayúsculas** (`ExerciseTitleStyle`).
  Se usa la fuente del sistema; para Montserrat/Inter hay que añadir los archivos de fuente a `res/font`.
* Números de series, repeticiones y peso: sans-serif limpia, grande y legible; cifras de temporizador con ancho
  tabular (`TimerStyle`) para que no "salten".

## 3. Patrones de layout
* **Bento Box**: tarjetas con esquinas de 16 dp (pequeñas) y 24 dp (destacadas), borde de 1 dp.
* Espaciado múltiplo de 4/8 dp. Zonas táctiles ≥ 48 dp.
* Una sola acción principal por pantalla; en Inicio es el **botón flotante** «Iniciar entrenamiento».

## 4. Microinteracciones (guía de Emil Kowalski, adaptada a Compose)
* Pulsar cualquier tarjeta o botón: escala 0,97 en ~130 ms con ease-out (`pressScale`); no mueve el layout.
* Completar una serie: el botón hace un pequeño "pop" (1 → 1,14 → 1 con muelle), cambia de color suavemente
  y vibra levemente (si la vibración está activa).
* Temporizadores: el número hace un fundido corto en vez de saltar, con cifras tabulares; la barra de progreso
  se desliza de forma continua.
* Nunca animar desde escala 0; las animaciones frecuentes se mantienen breves.
