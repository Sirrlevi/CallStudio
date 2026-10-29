package com.callstudio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Copies chosen images into app-private storage (downscaled), so a caller's photo keeps working after
 * restarts even when the picker only granted temporary access to the original.
 */
class PhotoStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String?): File? = name?.let { File(dir, it) }?.takeIf { it.exists() }

    fun delete(name: String?) {
        if (name != null) runCatching { File(dir, name).delete() }
    }

    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            val matrix = Matrix().apply {
                if (rotation != 0f) postRotate(rotation)
                if (scale < 1f) postScale(scale, scale)
            }
            if (!matrix.isIdentity) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }
            val name = "p_${UUID.randomUUID()}.jpg"
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            name
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_SIDE = 768
    }
}
