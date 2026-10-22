package com.trustarx.pitgo.features.guincho.data

import android.util.AtomicFile
import com.trustarx.pitgo.features.guincho.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class GuinchoInspectionPendingStore(val files: GuinchoInspectionCaptureFiles) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _captures = MutableStateFlow<List<InspectionPendingCapture>>(emptyList())
    val captures = _captures.asStateFlow()
    @Volatile private var visibleAccount: String? = null
    suspend fun load(account: String) = withContext(Dispatchers.IO) {
        val gen = files.generation
        files.commitMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (files.generation != gen) return@withLock
            visibleAccount = account
            val root = File(files.pendingRoot, UUID.fromString(account).toString())
            _captures.value = root.walkTopDown().filter { it.isFile && it.name in setOf("journal.json", "journal.json.bak", "journal.json.new") }
                .map { File(it.parentFile, "journal.json") }.distinct().mapNotNull { file ->
                runCatching { json.decodeFromString<InspectionPendingCapture>(AtomicFile(file).openRead().bufferedReader().use { it.readText() }) }
                    .getOrNull()?.takeIf { it.accountId == account && it.schemaVersion == 1 }
            }.toList()
        }
    }
    fun hide() { visibleAccount = null; _captures.value = emptyList() }
    suspend fun create(account: String, appointment: String, version: String, slot: InspectionSlot, caption: String, replacing: String?): InspectionPendingCapture {
        val uuid = { UUID.randomUUID().toString() }
        val item = InspectionPendingCapture(captureId = uuid(), accountId = account, appointmentId = appointment, versionId = version,
            point = slot.point, detailPoint = slot.detail, caption = caption, replacesPhotoId = replacing,
            reserveOperationId = uuid(), confirmOperationId = uuid(), discardOperationId = uuid())
        withContext(Dispatchers.IO) {
            files.pendingRoot.mkdirs()
            val size = files.pendingRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (size + 64L * 1024 * 1024 > 512L * 1024 * 1024 || files.pendingRoot.usableSpace < 64L * 1024 * 1024) throw InspectionException(InspectionFailure.SPACE)
        }
        put(item, files.generation)
        return item
    }
    suspend fun put(item: InspectionPendingCapture, gen: Long): InspectionPendingCapture = withContext(Dispatchers.IO) {
        files.commitMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (files.generation != gen || visibleAccount != item.accountId) throw InspectionException(InspectionFailure.SESSION)
            val dir = files.directory(item.accountId, item.appointmentId, item.captureId).apply { mkdirs() }
            val atomic = AtomicFile(File(dir, "journal.json"))
            val out = try { atomic.startWrite() } catch (_: java.io.IOException) { journalFailure(item, gen) }
            try { out.write(json.encodeToString(item).toByteArray()); atomic.finishWrite(out) }
            catch (error: Exception) {
                atomic.failWrite(out)
                if (error is java.io.IOException) journalFailure(item, gen)
                throw error
            }
            if (item.stage in setOf(InspectionCaptureStage.SAVED, InspectionCaptureStage.DISCARDED)) {
                listOf("raw.jpg", "normalized.jpg", "thumbnail.jpg").forEach { File(dir, it).delete() }
            }
            _captures.value = (_captures.value.filterNot { it.captureId == item.captureId } + item)
            item
        }
    }
    fun find(id: String) = _captures.value.firstOrNull { it.captureId == id }
    suspend fun restartRepairedReservation(item: InspectionPendingCapture, gen: Long): InspectionPendingCapture {
        require(item.repairReservation && item.photo?.abandonedAt != null)
        return put(item.copy(photo = null, originalSize = 0, thumbnailSize = 0, originalHash = null, thumbnailHash = null,
            reserveOperationId = UUID.randomUUID().toString(), confirmOperationId = UUID.randomUUID().toString(),
            discardOperationId = UUID.randomUUID().toString(), repairReservation = false, failure = null,
            stage = InspectionCaptureStage.RAW_CAPTURED, resumeStage = null, retryAt = 0), gen)
    }
    private fun journalFailure(item: InspectionPendingCapture, gen: Long): Nothing {
        val failure = if (files.pendingRoot.usableSpace < 4L * 1024 * 1024) InspectionFailure.SPACE else InspectionFailure.IMAGE
        if (files.generation == gen && visibleAccount == item.accountId && _captures.value.any { it.captureId == item.captureId }) {
            val retry = item.copy(stage = InspectionCaptureStage.RETRY_WAIT, resumeStage = item.stage, failure = failure, retryAt = System.currentTimeMillis() + 3000)
            _captures.value = _captures.value.filterNot { it.captureId == item.captureId } + retry
        }
        throw InspectionException(failure)
    }
}
