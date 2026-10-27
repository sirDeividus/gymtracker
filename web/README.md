# GymTrack Web (iPhone / cualquier navegador)

Versión web instalable (PWA) de GymTrack: funciona sin conexión, sin cuenta y guarda todo en el propio dispositivo
(IndexedDB). No necesita compilar nada: son archivos estáticos (HTML + JS + CSS).

## Instalar en el iPhone (gratis, desde Windows)
1. Publica la carpeta `web/` en una URL **https** (cualquiera de estas opciones):
   * **GitHub Pages**: tras fusionar el PR, en el repositorio → *Settings → Pages → Source: GitHub Actions*; el flujo
     `.github/workflows/pages.yml` la publica en `https://<usuario>.github.io/gymtracker/`.
   * **Netlify Drop** (https://app.netlify.com/drop): arrastra la carpeta `web` y te da una URL https al instante.
2. Abre esa URL en **Safari** (tiene que ser Safari) → botón *Compartir* → **Añadir a pantalla de inicio**.
3. Ábrela desde el icono: se ve a pantalla completa como una app y funciona sin internet.

## Qué incluye
Rutinas y plantillas, biblioteca de 94 ejercicios, entrenamiento (SERIE | ANTERIOR | PESO | REPS | ✓, tipos de serie,
calentamientos, discos, descanso por ejercicio con bloqueo anti-bolsillo, sugerencia de progresión nunca automática),
modo intervalos/Tabata (trabajo, descanso, rondas, ciclos, descanso entre ciclos y cuenta atrás de inicio), historial, progreso con gráficas y récords, fotos de progreso y peso corporal (solo en el teléfono), copia de seguridad JSON.

## Limitaciones en iPhone (de la plataforma, no de la app)
* La web **no puede vibrar ni avisar con la pantalla bloqueada**: el sonido de fin de descanso suena con la app abierta.
* Los datos viven en el navegador: si borras los datos del sitio o desinstalas el icono, se pierden → **exporta copias** (Ajustes).
* Las fotos no entran en la copia JSON.
* No incluye (todavía) los GIF/imágenes de ejercicios, el mapa corporal tocable ni los videos propios de la app Android.

## Desarrollo
`python3 -m http.server 8000` dentro de `web/` y abre http://localhost:8000. Al publicar cambios, sube `VERSION` en `sw.js`
para que los dispositivos descarguen la versión nueva.
