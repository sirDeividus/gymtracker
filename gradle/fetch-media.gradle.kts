// Tarea `fetchExerciseMedia`: instala las imágenes y GIF de demostración en app/src/main/assets/media/
// SOLO para uso personal. Los medios son (c) Gym visual (https://gymvisual.com/), no están bajo licencia MIT
// y por eso no se versionan (la carpeta está en .gitignore). Ver THIRD_PARTY_NOTICES.md.
//
//   ./gradlew :app:fetchExerciseMedia                       # miniaturas + GIF (~137 MB)
//   ./gradlew :app:fetchExerciseMedia -PmediaGifs=false     # solo miniaturas (~9 MB)
//   ./gradlew :app:fetchExerciseMedia -PmediaSource=/ruta/exercises-dataset   # desde un clon local
//
// Sin -PmediaSource se descarga el ZIP del repositorio (≈170 MB) y se extraen solo los archivos necesarios.

import java.net.URL
import java.util.zip.ZipInputStream

val mediaGifs = providers.gradleProperty("mediaGifs").map { it != "false" }.orElse(true)
val mediaSource = providers.gradleProperty("mediaSource").orElse("")
val assetsDir = layout.projectDirectory.dir("src/main/assets")

tasks.register("fetchExerciseMedia") {
    group = "gymtrack"
    description = "Instala imágenes y GIF de ejercicios (© Gym visual) para uso personal"
    notCompatibleWithConfigurationCache("descarga y copia archivos")

    doLast {
        val index = assetsDir.file("media_index.json").asFile
        require(index.exists()) { "Falta ${index.path}" }
        @Suppress("UNCHECKED_CAST")
        val map = groovy.json.JsonSlurper().parseText(index.readText()) as Map<String, String>
        val wanted = map.values.toSet()
        val withGifs = mediaGifs.get()
        val out = assetsDir.dir("media").asFile
        val imagesOut = out.resolve("images").apply { mkdirs() }
        val videosOut = out.resolve("videos").apply { mkdirs() }
        var images = 0
        var gifs = 0

        val src = mediaSource.get()
        if (src.isNotBlank() && java.io.File(src).isDirectory) {
            val root = java.io.File(src)
            for (name in wanted) {
                val img = root.resolve("images/$name.jpg")
                if (img.exists()) { img.copyTo(imagesOut.resolve("$name.jpg"), overwrite = true); images++ }
                if (withGifs) {
                    val gif = root.resolve("videos/$name.gif")
                    if (gif.exists()) { gif.copyTo(videosOut.resolve("$name.gif"), overwrite = true); gifs++ }
                }
            }
        } else {
            val url = src.ifBlank { "https://codeload.github.com/hasaneyldrm/exercises-dataset/zip/refs/heads/main" }
            logger.lifecycle("Descargando $url (puede tardar unos minutos)…")
            ZipInputStream(URL(url).openStream().buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val parts = e.name.split('/')
                    // <raíz>/images/<archivo>.jpg  o  <raíz>/videos/<archivo>.gif
                    if (parts.size != 3) continue
                    val file = parts[2]
                    val base = file.substringBeforeLast('.')
                    if (base !in wanted) continue
                    when {
                        parts[1] == "images" && file.endsWith(".jpg") -> { imagesOut.resolve(file).outputStream().use { zip.copyTo(it) }; images++ }
                        withGifs && parts[1] == "videos" && file.endsWith(".gif") -> { videosOut.resolve(file).outputStream().use { zip.copyTo(it) }; gifs++ }
                    }
                }
            }
        }
        logger.lifecycle("Medios instalados: $images imágenes, $gifs GIF → ${out.path}")
        logger.lifecycle("Recuerda: © Gym visual (https://gymvisual.com/), solo uso personal y no comercial.")
        require(images > 0) { "No se instaló ninguna imagen. Revisa la conexión o usa -PmediaSource=/ruta/al/clon" }
    }
}
