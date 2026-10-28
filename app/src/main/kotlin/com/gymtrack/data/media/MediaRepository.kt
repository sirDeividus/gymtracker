package com.gymtrack.data.media

import android.content.Context
import org.json.JSONObject

/** Rutas (URI de assets) de la miniatura y el GIF de un ejercicio, solo si los archivos existen. */
data class MediaRef(val thumbUri: String?, val gifUri: String?, val video: java.io.File? = null)

/**
 * Imágenes y GIF de ejercicios (© Gym visual, uso personal).
 *
 * El índice ejercicio → archivo se versiona (assets/media_index.json) pero los archivos multimedia NO:
 * se copian con `tools/media.py copy`. Si no están en assets/media/, la app funciona igual sin ellos.
 */
class MediaRepository(private val context: Context) {
    private val index: Map<String, String> by lazy {
        runCatching {
            val text = context.assets.open("media_index.json").bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            buildMap { json.keys().forEach { k -> put(k, json.getString(k)) } }
        }.getOrDefault(emptyMap())
    }
    private val images: Set<String> by lazy { listNames("media/images", ".jpg") }
    private val gifs: Set<String> by lazy { listNames("media/videos", ".gif") }

    private fun listNames(dir: String, ext: String): Set<String> =
        runCatching { context.assets.list(dir)?.filter { it.endsWith(ext) }?.map { it.removeSuffix(ext) }?.toSet() }
            .getOrNull().orEmpty()

    /** Hay al menos un archivo multimedia instalado. */
    val available: Boolean get() = images.isNotEmpty() || gifs.isNotEmpty()

    // ---- Videos propios (ejercicios personalizados): se guardan ya reducidos en el almacenamiento privado.
    private val videoDir = java.io.File(context.filesDir, "exercise_videos")

    /** Sube cada vez que se añade o quita un video, para que la interfaz vuelva a consultar. */
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun videoFile(exerciseId: String): java.io.File? =
        java.io.File(videoDir, "${exerciseId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.mp4").takeIf { it.isFile && it.length() > 0 }

    /** Mueve el video ya reducido (archivo temporal) al ejercicio. */
    fun attachVideo(exerciseId: String, temp: java.io.File): Boolean {
        videoDir.mkdirs()
        val safe = exerciseId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        val target = java.io.File(videoDir, "$safe.mp4")
        val ok = runCatching { temp.copyTo(target, overwrite = true); temp.delete(); true }.getOrDefault(false)
        version.value++
        return ok
    }

    fun removeVideo(exerciseId: String) {
        videoFile(exerciseId)?.delete()
        version.value++
    }

    fun refFor(exerciseId: String): MediaRef? {
        videoFile(exerciseId)?.let { return MediaRef(null, null, it) }
        val base = index[exerciseId] ?: return null
        val thumb = if (base in images) "file:///android_asset/media/images/$base.jpg" else null
        val gif = if (base in gifs) "file:///android_asset/media/videos/$base.gif" else null
        return if (thumb == null && gif == null) null else MediaRef(thumb, gif)
    }
}
