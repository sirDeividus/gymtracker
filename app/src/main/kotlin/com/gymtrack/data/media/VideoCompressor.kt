package com.gymtrack.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Resultado de reducir un video: el archivo temporal y los tamaños antes/después. */
class CompressedVideo(val file: File, val originalBytes: Long, val finalBytes: Long)

/**
 * Reduce un video para que pese poco: lado corto 480 px, ~0,8 Mbps, sin audio y máximo 10 s
 * (≈1 MB). Usa Media3 Transformer (codificación por hardware); debe llamarse desde el hilo principal.
 */
object VideoCompressor {
    const val MAX_SECONDS = 10
    private const val BITRATE = 800_000
    private const val SHORT_SIDE = 480

    fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    suspend fun compress(context: Context, source: Uri): Result<CompressedVideo> = suspendCancellableCoroutine { cont ->
        val out = File(context.cacheDir, "exercise_video_${System.currentTimeMillis()}.mp4").also { it.parentFile?.mkdirs() }
        val original = sizeOf(context, source)
        val item = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(MAX_SECONDS * 1000L).build())
            .build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(SHORT_SIDE))))
            .build()
        val encoder = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(BITRATE).build())
            .build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoder)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Result.success(CompressedVideo(out, original, out.length())))
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    out.delete()
                    if (cont.isActive) cont.resume(Result.failure(exportException))
                }
            })
            .build()
        cont.invokeOnCancellation { transformer.cancel(); out.delete() }
        transformer.start(edited, out.absolutePath)
    }
}
