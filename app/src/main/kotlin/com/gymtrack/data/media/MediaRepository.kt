package com.gymtrack.data.media

import android.content.Context
import org.json.JSONObject

/** Rutas (URI de assets) de la miniatura y el GIF de un ejercicio, solo si los archivos existen. */
data class MediaRef(val thumbUri: String?, val gifUri: String?)

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

    fun refFor(exerciseId: String): MediaRef? {
        val base = index[exerciseId] ?: return null
        val thumb = if (base in images) "file:///android_asset/media/images/$base.jpg" else null
        val gif = if (base in gifs) "file:///android_asset/media/videos/$base.gif" else null
        return if (thumb == null && gif == null) null else MediaRef(thumb, gif)
    }
}
