package com.trustarx.pitgo.features.guincho.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil3.SingletonImageLoader
import com.trustarx.pitgo.core.environment.EnvironmentConfig
import com.trustarx.pitgo.features.guincho.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class GuinchoInspectionCaptureFiles(context: Context) {
    private val app = context.applicationContext
    private val cacheRoot = File(app.cacheDir, "guincho-inspections")
    val environment = MessageDigest.getInstance("SHA-256").digest(EnvironmentConfig.fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
    val pendingRoot = File(app.filesDir, "guincho-inspections/pending/$environment")
    val commitMutex = Mutex()
    val bootCount: Int get() = android.provider.Settings.Global.getInt(app.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)
    private val normalizer = Mutex()
    @Volatile var generation: Long = 0; private set
    private fun uuid(value: String): String = UUID.fromString(value).toString().also { require(it == value.lowercase()) }
    fun directory(account: String, appointment: String, capture: String): File = File(pendingRoot, "${uuid(account)}/${uuid(appointment)}/${uuid(capture)}")
    fun raw(item: InspectionPendingCapture) = File(directory(item.accountId, item.appointmentId, item.captureId), "raw.jpg")
    fun original(item: InspectionPendingCapture) = File(raw(item).parentFile, "normalized.jpg")
    fun thumbnail(item: InspectionPendingCapture) = File(raw(item).parentFile, "thumbnail.jpg")
    fun uri(item: InspectionPendingCapture): String = FileProvider.getUriForFile(app, "${app.packageName}.inspection-files", raw(item)).toString()
    fun preview(item: InspectionPendingCapture): File? = listOf(thumbnail(item), original(item), raw(item)).firstOrNull { it.isFile && it.length() > 0 }
    fun capture(account: String, path: String): File {
        val file = File(path).canonicalFile
        val legacy = File(cacheRoot, "$environment/${uuid(account)}/capture").canonicalPath + File.separator
        val durable = File(pendingRoot, uuid(account)).canonicalPath + File.separator
        require(file.path.startsWith(legacy) || file.path.startsWith(durable))
        return file
    }
    fun hasRecoverableSource(item: InspectionPendingCapture): Boolean = listOf(raw(item), original(item)).any(::readableImage)
    private fun readableImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 64 }) ?: return false
        bitmap.recycle()
        return true
    }
    fun assetsValid(item: InspectionPendingCapture): Boolean = runCatching {
        original(item).length() == item.originalSize && hash(original(item)) == item.originalHash &&
            (item.photo?.let { it.thumbnailPath == null } == true ||
                thumbnail(item).length() == item.thumbnailSize && hash(thumbnail(item)) == item.thumbnailHash)
    }.getOrDefault(false)
    /** Regenerate separately: a reservation's byte manifest must never change silently. */
    suspend fun restoreAssets(item: InspectionPendingCapture, gen: Long): Boolean {
        prepare(item, gen, repair = true)
        return withContext(Dispatchers.IO) {
            val original = File(original(item).path + ".repair")
            val thumb = File(thumbnail(item).path + ".repair")
            try {
                val matches = original.length() == item.originalSize && hash(original) == item.originalHash &&
                    (item.photo?.let { it.thumbnailPath == null } == true || thumb.length() == item.thumbnailSize && hash(thumb) == item.thumbnailHash)
                if (matches) commitMutex.withLock {
                    if (generation != gen) throw InspectionException(InspectionFailure.SESSION)
                    check(original.renameTo(original(item)))
                    if (item.thumbnailHash != null) check(thumb.renameTo(thumbnail(item)))
                }
                matches
            } finally { original.delete(); thumb.delete() }
        }
    }
    suspend fun prepare(item: InspectionPendingCapture, expectedGeneration: Long, repair: Boolean = false) = normalizer.withLock {
        withContext(Dispatchers.IO) {
            val source = listOf(raw(item), original(item)).firstOrNull(::readableImage)
                ?: throw InspectionException(InspectionFailure.IMAGE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw InspectionException(InspectionFailure.IMAGE)
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2560) options.inSampleSize *= 2
            currentCoroutineContext().ensureActive()
            val bitmap = BitmapFactory.decodeFile(source.path, options) ?: throw InspectionException(InspectionFailure.IMAGE)
            val matrix = Matrix()
            when (ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            }
            val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val scale = minOf(1f, 480f / maxOf(normalized.width, normalized.height))
            val thumb = Bitmap.createScaledBitmap(normalized, maxOf(1, (normalized.width * scale).toInt()), maxOf(1, (normalized.height * scale).toInt()), true)
            try {
                writeJpeg(normalized, if (repair) File(original(item).path + ".repair") else original(item), 5_242_880, expectedGeneration)
                writeJpeg(thumb, if (repair) File(thumbnail(item).path + ".repair") else thumbnail(item), 262_144, expectedGeneration)
            } finally {
                if (thumb !== normalized) thumb.recycle()
                if (normalized !== bitmap) normalized.recycle()
                bitmap.recycle()
            }
        }
    }
    private suspend fun writeJpeg(bitmap: Bitmap, target: File, max: Long, gen: Long) {
        val temp = File(target.path + ".part")
        try {
            for (quality in listOf(90, 80, 65, 50)) {
                currentCoroutineContext().ensureActive()
                FileOutputStream(temp).use { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)); out.fd.sync() }
                if (temp.length() in 1..max) break
            }
            if (temp.length() !in 1..max) throw InspectionException(InspectionFailure.IMAGE)
            commitMutex.withLock {
                currentCoroutineContext().ensureActive()
                if (generation != gen) throw InspectionException(InspectionFailure.SESSION)
                check(temp.renameTo(target))
            }
        } catch (error: java.io.IOException) {
            throw InspectionException(if (pendingRoot.usableSpace < 4L * 1024 * 1024) InspectionFailure.SPACE else InspectionFailure.INTERRUPTED)
        } finally { temp.delete() }
    }
    fun downloadFile(account: String, photoId: String, thumbnail: Boolean = false): File =
        File(cacheRoot, "$environment/${uuid(account)}/download/${uuid(photoId)}${if (thumbnail) ".thumb" else ""}.jpg")
    fun cacheKey(account: String, photoId: String, thumbnail: Boolean = false) = "guincho-inspection:$environment:$account:$photoId:${if (thumbnail) "thumb" else "original"}"
    fun trimDownloads(account: String, keep: File) {
        val files = downloadFile(account, UUID.randomUUID().toString()).parentFile?.listFiles()?.filter { !it.name.endsWith(".part") }?.sortedBy { it.lastModified() }.orEmpty()
        var size = files.sumOf { it.length() }
        for (file in files) if (size > 128L * 1024 * 1024 && file != keep) { val bytes = file.length(); if (file.delete()) size -= bytes }
    }
    /** Invalidation never deletes accepted pending originals. */
    fun clear() {
        generation++
        SingletonImageLoader.get(app).memoryCache?.let { cache -> cache.keys.filter { it.key.startsWith("guincho-inspection:") }.forEach(cache::remove) }
    }
    suspend fun clearReadCache(account: String? = null) = withContext(Dispatchers.IO) {
        commitMutex.withLock {
            // Old accepted captures may still need import from SavedStateHandle after an upgrade.
            cacheRoot.walkTopDown().filter { it.isDirectory && it.name == "download" && (account == null || it.parentFile?.name == account) }.toList().forEach { it.deleteRecursively() }
        }; Unit
    }
    fun hash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(16_384)
        while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
