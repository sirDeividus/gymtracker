# GymTrack para iPhone (Flutter)

Versión nativa de iPhone de GymTrack escrita en Flutter/Dart (`gymtrack_flutter/`). Mismas funciones que la versión web
(rutinas, entrenamiento con descanso bloqueado, series W/D/F, discos, Tabata/ciclos, historial, progreso, fotos y peso,
copia JSON) más **avisos de fin de descanso con la pantalla bloqueada** (notificación local programada), voz y pitidos con
bajada de volumen de la música (audio ducking).

> Estado: la lógica y la interfaz se prueban en Linux (`flutter test`, 14 pruebas), pero **nadie la ha ejecutado aún en un
> iPhone**. La primera instalación servirá para detectar fallos propios de iOS.

## Cómo se compila sin Mac
Un servidor de GitHub con macOS compila la app (`.github/workflows/flutter-ios.yml`). Cada vez que se sube código a
`gymtrack_flutter/` se genera `GymTrack-unsigned.ipa` (pestaña **Actions** → ejecución → **Artifacts**).

## Opción A · Gratis (Apple ID normal): Sideloadly / AltStore / SideStore
Apple solo instala apps firmadas. Con un Apple ID gratuito la firma dura **7 días** (hay que reinstalar o renovar) y se
permiten 3 apps a la vez. Necesitas un ordenador (tu PC con Windows) al menos para la primera instalación.

1. Descarga el artefacto `GymTrack-ios-unsigned` de GitHub Actions y descomprime el `.ipa`.
2. En el PC instala **Sideloadly** (https://sideloadly.io) (o **AltStore**), con iTunes y iCloud de Apple (no de la Microsoft Store).
3. Conecta el iPhone, abre Sideloadly, arrastra el `.ipa`, escribe tu Apple ID y pulsa *Start*.
4. En el iPhone: Ajustes → General → VPN y gestión de dispositivos → confía en tu Apple ID. En iOS 16+: activa el **Modo desarrollador** (Ajustes → Privacidad y seguridad).
5. Cada ~6 días repite el paso 3 (AltStore/SideStore pueden renovarla solos si el PC está en la misma red; **SideStore** renueva en el propio iPhone tras la configuración inicial).

## Opción B · TestFlight (cuenta de pago, 99 USD/año)
Se instala desde el propio iPhone, sin PC, y cada compilación dura 90 días. Es solo para ti (tester interno, sin revisión de Apple).

Una sola vez:
1. Apúntate a **Apple Developer Program** (https://developer.apple.com/programs/).
2. En App Store Connect → *Apps* → **+** → Nueva app: elige un **Bundle ID único** (por ejemplo `com.tunombre.gymtrack`) y un nombre.
3. En *Usuarios y acceso → Integraciones → App Store Connect API* crea una clave con rol *Admin* o *App Manager*; descarga el `.p8` (solo se puede una vez) y apunta el **Key ID** y el **Issuer ID**.
4. Apunta tu **Team ID** (developer.apple.com → Membership).
5. En GitHub → *Settings → Secrets and variables → Actions* crea estos secretos:
   `APPSTORE_KEY_ID`, `APPSTORE_ISSUER_ID`, `APPSTORE_PRIVATE_KEY` (el contenido completo del `.p8`), `APPLE_TEAM_ID`, `APP_BUNDLE_ID`.

Cada vez que quieras una versión nueva: **Actions → Flutter TestFlight → Run workflow**. A los pocos minutos aparece en la app **TestFlight** del iPhone (añade tu Apple ID como tester interno).

> El flujo de TestFlight está escrito siguiendo la documentación de Apple pero **no se ha podido probar** desde el entorno de desarrollo; si falla, el registro del paso indicará qué falta.

## Datos y privacidad
Todo se guarda en el iPhone (archivo JSON en la carpeta de la app y fotos privadas). No hay cuenta ni servidor. Las fotos no entran
en la copia JSON. Si desinstalas la app, los datos se pierden: exporta copias desde Ajustes.

## Desarrollo
```
cd gymtrack_flutter
flutter pub get
flutter analyze
flutter test
```
El proyecto sale de `flutter create`; la carpeta `ios/` ya está lista (permisos de cámara/galería, notificaciones).
