package com.gymtrack.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.gymtrack.data.db.BodyWeightEntity
import com.gymtrack.data.db.GymDatabase
import com.gymtrack.data.db.ProgressPhotoEntity
import com.gymtrack.domain.BodyWeightStats
import com.gymtrack.domain.PhotoPose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fotos de progreso y peso corporal. Las fotos se guardan SOLO en el almacenamiento privado de la app
 * (filesDir/progress_photos): no aparecen en la galería, no se suben a ningún sitio y se excluyen de la
 * copia automática de Android.
 */
class BodyRepository(
    private val context: Context,
    db: GymDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.bodyDao()

    val photos: Flow<List<ProgressPhotoEntity>> = dao.observePhotos()
    val weights: Flow<List<BodyWeightEntity>> = dao.observeWeights()

    private val dir: File get() = File(context.filesDir, "progress_photos").apply { mkdirs() }

    fun fileOf(photo: ProgressPhotoEntity): File = File(dir, photo.fileName)

    /**
     * Importa una imagen (cámara o galería): la reduce a 1600 px, corrige la orientación EXIF y la guarda como JPEG.
     * @param open abre el flujo de la imagen; se llama varias veces (medidas, decodificación, EXIF).
     */
    suspend fun addPhoto(
        open: () -> InputStream?,
        pose: PhotoPose,
        weightKg: Double?,
        note: String,
        takenAt: Long = now(),
    ): Result<ProgressPhotoEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = decodeScaled(open) ?: error("No se pudo leer la imagen")
            val bitmap = applyExifRotation(open, decoded)
            val id = UUID.randomUUID().toString()
            val name = "$id.jpg"
            File(dir, name).outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            val entity = ProgressPhotoEntity(
                id = id, takenAt = takenAt, pose = pose.name,
                weightKg = weightKg?.takeIf { BodyWeightStats.isValid(it) },
                note = note.trim().take(300), fileName = name,
            )
            dao.upsertPhoto(entity)
            entity
        }
    }

    suspend fun deletePhoto(id: String) {
        val p = dao.getPhoto(id) ?: return
        dao.deletePhoto(id)
        withContext(Dispatchers.IO) { fileOf(p).delete() }
    }

    suspend fun addWeight(kg: Double, at: Long = now()): Boolean {
        if (!BodyWeightStats.isValid(kg)) return false
        dao.upsertWeight(BodyWeightEntity(UUID.randomUUID().toString(), at, kg))
        return true
    }

    suspend fun deleteWeight(id: String) = dao.deleteWeight(id)

    private fun decodeScaled(open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
        } else bmp
    }

    private fun applyExifRotation(open: () -> InputStream?, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
