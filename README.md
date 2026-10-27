# GymTrack

App Android nativa (Kotlin + Jetpack Compose + Room) para registrar entrenamientos de fuerza rápido y con datos
fiables. Funciona **sin conexión, sin cuenta y sin permisos innecesarios**.

> **Estado honesto de la verificación**
> * `domain/` (toda la lógica de entrenamiento): **compilado y probado — 111 pruebas JUnit pasando**.
> * `app/` (Android): el propietario confirmó que **compila y funciona en su dispositivo** con una versión anterior.
>   Los cambios posteriores de interfaz (rediseño, modo intervalos, mapa muscular, medios) solo se han revisado
>   estáticamente: su entorno de desarrollo no podía compilar Android (Google Maven bloqueado), así que puede
>   aparecer algún error de compilación o ajuste visual en la primera sincronización.
> * Las pruebas instrumentadas (persistencia, recuperación de sesión, migraciones, copia de seguridad) están escritas
>   pero **no se han ejecutado**. No hay APK publicado.

## Versión web para iPhone

La carpeta [`web/`](web/README.md) contiene una versión web instalable (PWA) que se añade a la pantalla de inicio del iPhone sin Mac ni cuenta de Apple.

## Abrir y ejecutar en Android Studio

1. Android Studio **Ladybug (2024.2)** o posterior, con JDK 17+ (el JBR incluido sirve) y Android SDK 35.
2. `File ▸ Open…` y elige la carpeta del proyecto. Deja que Gradle sincronice (descarga Gradle 8.10.2 vía wrapper).
3. Elige un dispositivo/emulador con Android 8.0 (API 26) o superior y pulsa **Run ▸ app**.

## Generar el APK

```bash
./gradlew :app:assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
```

**Lanzamiento firmado.** Crea un keystore (`keytool -genkey -v -keystore gymtrack.jks -alias gymtrack -keyalg RSA -keysize 2048 -validity 10000`)
y un archivo `keystore.properties` en la raíz (ignorado por git):

```
storeFile=gymtrack.jks
storePassword=…
keyAlias=gymtrack
keyPassword=…
```

```bash
./gradlew :app:assembleRelease    # APK firmado, con R8 + reducción de recursos
./gradlew :app:bundleRelease      # AAB para Play Store
```

Sin `keystore.properties`, `assembleRelease` genera un APK sin firmar.

## Pruebas

```bash
cd domain && ../gradlew test                  # lógica de entrenamiento (111 pruebas; no necesita Android)
./gradlew :app:connectedDebugAndroidTest      # persistencia, recuperación de sesión, migraciones y copia
                                              # de seguridad con Room real (necesita dispositivo/emulador)
```

## Personalizar la marca

Todo en un solo sitio: `app/src/main/res/values/brand.xml` (nombre, lema y paleta —por defecto negro con acento rojo eléctrico `#FF1E46`; ver `DESIGN.md`—; la UI de Compose lee estos
recursos) y `res/drawable/ic_launcher_foreground.xml` + `brand_icon_background` (icono). El `applicationId` está en
`app/build.gradle.kts`.

## Funcionalidades implementadas

* **Rutinas**: crear, editar, duplicar y eliminar; días de entrenamiento; añadir/quitar ejercicios; **reordenar arrastrando**
  (y con menú Subir/Bajar para accesibilidad); series, rango de repeticiones, duración objetivo, peso inicial, descanso y
  notas; copiar ejercicios de otra rutina; totales de ejercicios y series; **autoguardado**; plantillas editables
  (Push, Pull, Piernas, Torso, Full Body). Eliminar una rutina no toca el historial.
* **Biblioteca**: 92 ejercicios revisados + **biblioteca ampliada opcional de 1.324** (datos MIT de
  [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset), instrucciones en español);
  búsqueda instantánea (sin acentos), filtros por 12 grupos musculares y 7 equipamientos, orden por relevancia
  (según tu uso real) / grupo / equipamiento / nombre, ejercicios personalizados, métodos de registro (peso y reps,
  peso corporal, duración, distancia/tiempo) y unilaterales por lado.
* **Entrenamiento activo**: tabla `SERIE | ANTERIOR | PESO | REPS | ✓`; el número de series coincide con la
  rutina; completar con un toque (valores precargados de la última sesión); editar series completadas; añadir,
  quitar y repetir serie; RPE opcional; saltar/añadir/quitar ejercicio; notas; marca de dolor o técnica;
  temporizador de descanso (pausa, reinicio, ±15 s, saltar, vibración/sonido opcionales, aviso en segundo plano);
  récords en vivo sin interrumpir; pantalla siempre encendida; **la sesión se recupera tras cerrar la app o reiniciar**.
* **Intervalos / Tabata**: en cualquier ejercicio por duración, «Modo intervalos» con cuenta atrás automática
  (preparación de 5 s, trabajo, descanso, rondas), aviso por vibración/sonido en cada cambio, pausa, y cada ronda
  completa una serie. Preajustes Tabata 20/10, 30/30 y 40/20. La plantilla **«Tabata + cardio»** (programada para el
  miércoles) combina «Tabata de abdominales» (8 × 20 s, descanso 10 s) y «Correr en caminadora».
* **Cardio**: distancia en **km** y tiempo en **minutos**, con el ritmo (min/km) calculado.
* **Peso corporal** sin kilos obligatorios (peso corporal / + lastre / − asistencia).
* **Progresión sugerida** con motivo y editable (ver `docs/ARCHITECTURE.md`), incrementos configurables, kg/lb.
* **Resumen** al finalizar: duración, ejercicios, series, volumen (marcado como orientativo), récords, comparación con la
  sesión anterior de la rutina, notas. Descartar ≠ finalizar, con confirmación.
* **Historial**: calendario mensual, lista, detalle exacto de cada sesión, evolución por ejercicio.
* **Progreso**: evolución (carga, 1RM estimado, repeticiones, volumen, duración) por ejercicio y periodo, frecuencia
  semanal, series por grupo muscular, volumen semanal y récords personales, con estados vacíos útiles.
* **Mapa corporal** de músculos recientes (frente y espalda) dibujado con formas propias —gratis, sin recursos de terceros—, en Inicio y Progreso, con la intensidad en el color de marca.
* **Inicio**: saludo, ENTRENAR / continuar, rutina de hoy o recomendada, resumen semanal, último entrenamiento con
  variación de volumen, "Tu próximo objetivo" (solo con historial real), músculos recientes.
* **Copia de seguridad** JSON exportable/importable con validación y confirmación antes de sobrescribir (transacción
  atómica: si falla, no se modifica nada).
* **Tipos de serie** (normal, calentamiento, descendente, al fallo): los calentamientos no cuentan en volumen, récords ni progresión.
  Toca ANTERIOR para copiar el peso/reps de la sesión pasada; calculadora de discos y generador de calentamiento.
* **Pantalla de entrenamiento** al estilo Hevy: cronómetro, «Terminar», volumen/series/mapa corporal, filas completadas en verde,
  descanso por ejercicio. **Bloqueo durante el descanso**: la pantalla ignora toques (bolsillo) hasta que mantienes pulsado el candado.
* **Descanso al cambiar de ejercicio** (Ajustes): al completar la última serie de un ejercicio se usa este tiempo, distinto al descanso entre series.
* **Modo intervalos/Tabata** configurable: trabajo, descanso, rondas por ciclo, ciclos, descanso entre ciclos y cuenta atrás de inicio.
* **Añadir ejercicio**: silueta corporal tocable (Girar frente/espalda) → cuadrícula de ejercicios del músculo elegido, con filtros.
* **Editar ejercicio** de rutina a pantalla completa con una tarjeta por serie (todas comparten objetivo).
* **Demostración a pantalla completa**: el GIF se anima en la miniatura del entrenamiento y, al tocarlo, se abre ajustado a la pantalla.
  En ejercicios personalizados puedes subir tu propio video: se reduce solo (480 px, sin audio, máx. 10 s, ≈1 MB, Media3 Transformer)
  y queda en el almacenamiento privado (no entra en la copia JSON).
* **Fotos de progreso y peso corporal** (Progreso → Fotos y peso): cámara o galería, pose, nota, comparación antes/después
  y gráfica de peso. Se guardan en el almacenamiento privado de la app; no se suben a ningún sitio.

## Imágenes y GIF de ejercicios (opcional, uso personal)

Las imágenes y animaciones del dataset son **© Gym visual** y no están bajo la licencia MIT, por lo que **no se
incluyen en este repositorio** (que es público). La app las muestra automáticamente, con su atribución, si las copias
tú en local:

En Android Studio: panel **Gradle ▸ app ▸ gymtrack ▸ fetchExerciseMedia** (o por terminal) y vuelve a ejecutar la app:

```bash
./gradlew :app:fetchExerciseMedia                      # miniaturas + GIF (≈137 MB de APK)
./gradlew :app:fetchExerciseMedia -PmediaGifs=false    # solo miniaturas (≈9 MB)
./gradlew :app:fetchExerciseMedia -PmediaSource=/ruta/exercises-dataset   # desde un clon local, sin descargar
```

Con las imágenes instaladas se ven miniaturas en la biblioteca, el editor de rutinas, el entrenamiento y el historial,
y el GIF animado en la ficha del ejercicio. (`tools/media.py` sigue disponible como alternativa y para regenerar el índice.)

Si distribuyes la app o el uso no es personal, consulta antes sus
[términos](https://gymvisual.com/content/3-terms-and-conditions-of-use). Detalles en `THIRD_PARTY_NOTICES.md`.

## Limitaciones y pendiente

* **Parte Android sin verificar tras los últimos cambios** (ver arriba): falta confirmar compilación, ejecutar las pruebas
  instrumentadas, probar el arrastre para reordenar en un dispositivo y revisar el diseño en distintos tamaños.
* El modo intervalos avisa (vibración/sonido) solo con la app abierta; si bloqueas la pantalla el reloj sigue, pero el aviso puede no sonar.
* Solo tema oscuro (decisión de producto); no hay tema claro.
* Los nombres de la biblioteca ampliada están en inglés (el dataset no los trae en español); las instrucciones sí.
  Su clasificación (movimiento, registro, unilateral) es heurística y puede requerir ajustes puntuales.
* Los medios solo existen para los ejercicios con equivalente exacto en el dataset (76 de los 92 básicos + toda la ampliada).
* El aviso de fin de descanso en segundo plano usa una alarma inexacta (respeta el ahorro de batería): puede
  retrasarse unos segundos o minutos con optimización agresiva.
* Las fotos de progreso NO van en la copia JSON ni en la auto-copia de Android (solo en el teléfono): si desinstalas, se pierden.
* La base de datos pasó a la versión 2 (migración v1→v2 sin probar en dispositivo); haz commit de `app/schemas` tras compilar.
* La copia de seguridad incluye sesiones finalizadas, no la sesión en curso ni las descartadas.
* Sin pruebas de interfaz (Compose UI tests) ni internacionalización (solo español).
* Auto-copia de seguridad de Android activada (Room + ajustes); si no la quieres, ponla a `false` en el manifiesto.
