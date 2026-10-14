package com.trustarx.pitgo.features.guincho.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil3.SingletonImageLoader
import com.trustarx.pitgo.core.environment.EnvironmentConfig
import com.trustarx.pitgo.features.guincho.model.InspectionException
import com.trustarx.pitgo.features.guincho.model.InspectionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class GuinchoInspectionCaptureFiles(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.cacheDir, "guincho-inspections")
    private val environment = MessageDigest.getInstance("SHA-256")
        .digest(EnvironmentConfig.fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
    @Volatile var generation: Long = 0; private set

    private fun directory(account: String, kind: String): File {
        require(account.matches(Regex("[a-fA-F0-9-]{36}")))
        return File(root, "$environment/$account/$kind").apply { mkdirs() }
    }
    fun newCapture(account: String): Pair<String, String> {
        val file = File(directory(account, "capture"), "${UUID.randomUUID()}.jpg").apply { createNewFile() }
        return file.absolutePath to FileProvider.getUriForFile(app, "${app.packageName}.inspection-files", file).toString()
    }
    fun capture(account: String, path: String): File {
        val file = File(path).canonicalFile
        require(file.path.startsWith(directory(account, "capture").canonicalPath + File.separator))
        return file
    }
    suspend fun normalize(account: String, path: String): File = withContext(Dispatchers.IO) {
        val file = capture(account, path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw InspectionException(InspectionFailure.IMAGE)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 5120) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: throw InspectionException(InspectionFailure.IMAGE)
        val matrix = Matrix()
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        val scale = minOf(1f, 2560f / maxOf(bitmap.width, bitmap.height))
        matrix.postScale(scale, scale)
        val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val output = File(file.parentFile, "${UUID.randomUUID()}.jpg")
        try {
            for (quality in listOf(90, 85, 80)) {
                output.outputStream().use { normalized.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                if (output.length() in 1..5_242_880) break
            }
            if (output.length() !in 1..5_242_880) throw InspectionException(InspectionFailure.IMAGE)
            check(output.renameTo(file))
            file
        } finally {
            output.delete()
            if (normalized !== bitmap) normalized.recycle()
            bitmap.recycle()
        }
    }
    fun downloadFile(account: String, photoId: String): File {
        require(photoId.matches(Regex("[a-fA-F0-9-]{36}")))
        return File(directory(account, "download"), "$photoId.jpg")
    }
    fun cacheKey(account: String, photoId: String) = "guincho-inspection:$environment:$account:$photoId"
    fun trimDownloads(account: String, keep: File) {
        val files = directory(account, "download").listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        var size = files.sumOf { it.length() }
        for (file in files) if (size > 128L * 1024 * 1024 && file != keep) {
            val bytes = file.length(); if (file.delete()) size -= bytes
        }
    }
    fun clear() {
        generation++
        root.deleteRecursively()
        SingletonImageLoader.get(app).memoryCache?.let { cache ->
            cache.keys.filter { it.key.startsWith("guincho-inspection:") }.forEach(cache::remove)
        }
    }
}
