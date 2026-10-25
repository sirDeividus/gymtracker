pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "GymTrack"

// Lógica de negocio pura (Kotlin/JVM): progresión, récords, volumen, copia de seguridad.
// Es un build independiente (se puede probar con `cd domain && ../gradlew test`)
// y se sustituye automáticamente por la dependencia com.gymtrack:domain.
includeBuild("domain")

include(":app")
