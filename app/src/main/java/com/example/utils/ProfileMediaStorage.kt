package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MediaCropTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    fun encode(): String = "$scale,$offsetX,$offsetY"

    companion object {
        fun decode(raw: String?): MediaCropTransform {
            if (raw.isNullOrBlank()) return MediaCropTransform()
            val parts = raw.split(",")
            return MediaCropTransform(
                scale = parts.getOrNull(0)?.toFloatOrNull()?.coerceIn(1f, 3f) ?: 1f,
                offsetX = parts.getOrNull(1)?.toFloatOrNull()?.coerceIn(-1f, 1f) ?: 0f,
                offsetY = parts.getOrNull(2)?.toFloatOrNull()?.coerceIn(-1f, 1f) ?: 0f
            )
        }
    }
}

object ProfileMediaStorage {

    private const val MEDIA_DIR = "dashboard_media"

    fun detectIsVideo(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.startsWith("video/", ignoreCase = true)) return true
        if (mime.startsWith("image/", ignoreCase = true)) return false
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
        return ext in setOf("mp4", "mkv", "webm", "3gp", "mov", "m4v")
    }

    fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = 1440): Bitmap? {
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return@runCatching null

            var sampleSize = 1
            while ((rawW / sampleSize) > maxDim * 2 || (rawH / sampleSize) > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotationDegrees == 0f) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
        }.getOrNull()
    }

    fun cropAndSaveImage(
        context: Context,
        sourceBitmap: Bitmap,
        crop: MediaCropTransform,
        outputSizePx: Int = 720
    ): String? {
        return runCatching {
            val w = sourceBitmap.width.toFloat()
            val h = sourceBitmap.height.toFloat()
            if (w <= 0f || h <= 0f) return@runCatching null

            val viewport = 1000f
            val baseScale = max(viewport / w, viewport / h)
            val totalScale = baseScale * crop.scale.coerceIn(1f, 3f)
            val drawnW = w * totalScale
            val drawnH = h * totalScale
            val maxShiftX = ((drawnW - viewport) / 2f).coerceAtLeast(0f)
            val maxShiftY = ((drawnH - viewport) / 2f).coerceAtLeast(0f)

            val tx = (crop.offsetX * viewport).coerceIn(-maxShiftX, maxShiftX)
            val ty = (crop.offsetY * viewport).coerceIn(-maxShiftY, maxShiftY)

            val cropLeft = ((drawnW / 2f - viewport / 2f - tx) / totalScale)
                .roundToInt()
                .coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
            val cropTop = ((drawnH / 2f - viewport / 2f - ty) / totalScale)
                .roundToInt()
                .coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))

            val maxSide = min(sourceBitmap.width - cropLeft, sourceBitmap.height - cropTop).coerceAtLeast(1)
            val desiredSide = (viewport / totalScale).roundToInt().coerceIn(1, maxSide)

            val squareCrop = Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, desiredSide, desiredSide)
            val finalBitmap = if (squareCrop.width != outputSizePx || squareCrop.height != outputSizePx) {
                Bitmap.createScaledBitmap(squareCrop, outputSizePx, outputSizePx, true).also {
                    if (it !== squareCrop) squareCrop.recycle()
                }
            } else {
                squareCrop
            }

            val dir = mediaDir(context)
            clearOldFiles(dir)
            val outFile = File(dir, "hero_image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            if (finalBitmap !== sourceBitmap) {
                finalBitmap.recycle()
            }
            Uri.fromFile(outFile).toString()
        }.getOrNull()
    }

    fun saveVideoToInternalStorage(context: Context, sourceUri: Uri): String? {
        return runCatching {
            val dir = mediaDir(context)
            clearOldFiles(dir)
            val outFile = File(dir, "hero_video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            if (outFile.length() <= 0L) {
                outFile.delete()
                return@runCatching null
            }
            Uri.fromFile(outFile).toString()
        }.getOrNull()
    }

    fun clearSavedMedia(context: Context) {
        runCatching {
            clearOldFiles(mediaDir(context))
        }
    }

    private fun mediaDir(context: Context): File {
        return File(context.filesDir, MEDIA_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun clearOldFiles(dir: File) {
        dir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }
}
