package com.trustarx.pitgo.features.guincho.repository

import android.util.Log
import com.trustarx.pitgo.BuildConfig
import com.trustarx.pitgo.features.guincho.data.*
import com.trustarx.pitgo.features.guincho.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class GuinchoInspectionRepository(
    private val client: SupabaseClient,
    val files: GuinchoInspectionCaptureFiles,
) {
    val pending = GuinchoInspectionPendingStore(files)
    private val pipelines = ConcurrentHashMap<String, Mutex>()
    private val downloads = Semaphore(2)
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    fun accountId(): String = client.auth.currentUserOrNull()?.id ?: throw InspectionException(InspectionFailure.SESSION)
    suspend fun awaitSession() = call("session_ready") { client.auth.awaitInitialization() }
    private fun checkAccount(account: String, generation: Long) {
        if (accountId() != account || files.generation != generation) throw InspectionException(InspectionFailure.SESSION)
    }
    suspend fun document(id: String): GuinchoInspectionDocument = call("get_guincho_inspection") {
        client.postgrest.rpc("get_guincho_inspection_v2", buildJsonObject { put("p_appointment_id", id) })
            .decodeAs<GuinchoInspectionDocumentDto>().domain()
    }
    internal suspend fun photos(id: String, version: String?, order: Long? = null, afterId: String? = null): GuinchoInspectionPhotoPage = call("list_guincho_inspection_photos") {
        client.postgrest.rpc("list_guincho_inspection_photos_v2", buildJsonObject {
            put("p_appointment_id", id); version?.let { put("p_version_id", it) }
            order?.let { put("p_after_order", it) }; afterId?.let { put("p_after_id", it) }; put("p_limit", 20)
        }).decodeAs<GuinchoInspectionPhotoPage>().also { page -> page.items.forEach { it.validate() } }
    }
    internal suspend fun versions(id: String, before: Int? = null): GuinchoInspectionVersionPage = call("list_guincho_inspection_versions") {
        client.postgrest.rpc("list_guincho_inspection_versions", buildJsonObject {
            put("p_appointment_id", id); before?.let { put("p_before_version", it) }; put("p_limit", 10)
        }).decodeAs<GuinchoInspectionVersionPage>()
    }
    private fun command(doc: GuinchoInspectionDocument, operation: String) = buildJsonObject {
        put("p_appointment_id", doc.appointmentId); put("p_expected_revision", doc.revision); put("p_operation_id", operation)
    }
    private suspend fun mutate(name: String, body: JsonObject): GuinchoInspectionEnvelopeDto = call(name) {
        client.postgrest.rpc(name, body).decodeAs<GuinchoInspectionEnvelopeDto>().also {
            it.inspection.domain()
            if (it.outcome == "REVISION_CONFLICT") throw InspectionException(InspectionFailure.CONFLICT)
            require(it.outcome in listOf("APPLIED", "EXISTING"))
        }
    }
    internal suspend fun prepare(doc: GuinchoInspectionDocument, item: InspectionPendingCapture): GuinchoInspectionEnvelopeDto = mutate("prepare_guincho_inspection_photo_v2",
        JsonObject(command(doc, item.reserveOperationId) + buildJsonObject {
            put("p_version_id", item.versionId); put("p_point", item.point.name)
            put("p_caption", item.caption.trim()); put("p_captured_at", requireNotNull(item.capturedAt)); put("p_byte_size", item.originalSize)
            put("p_thumbnail_byte_size", item.thumbnailSize)
            item.detailPoint?.let { put("p_detail_point", it) }
            item.replacesPhotoId?.let { put("p_replaces_photo_id", it) }
        }))
    private suspend fun prepareLegacy(doc: GuinchoInspectionDocument, item: InspectionPendingCapture): GuinchoInspectionEnvelopeDto {
        if (item.photo?.reserveOperationId != item.reserveOperationId) throw InspectionException(InspectionFailure.CONTEXT)
        return mutate("prepare_guincho_inspection_photo", JsonObject(command(doc, item.reserveOperationId) + buildJsonObject {
            put("p_version_id", item.versionId); put("p_point", item.point.name); put("p_caption", item.caption)
            put("p_captured_at", requireNotNull(item.capturedAt)); put("p_byte_size", item.originalSize)
            item.replacesPhotoId?.let { put("p_replaces_photo_id", it) }
        }))
    }
    suspend fun upload(account: String, photo: GuinchoInspectionPhoto, file: File, thumbnail: Boolean = false) = call("upload") {
        val generation = files.generation
        val path = if (thumbnail) requireNotNull(photo.thumbnailPath) else photo.objectPath
        checkAccount(account, generation)
        withContext(Dispatchers.IO) {
            try {
                client.storage.from("guincho-inspections").upload(path, file.readBytes()) {
                    upsert = false; contentType = ContentType.Image.JPEG
                }
            } catch (error: RestException) {
                val duplicate = error.statusCode == 409 || error.error.lowercase() in setOf("duplicate", "resourcealreadyexists", "keyalreadyexists")
                if (!duplicate) throw error
                val remote = client.storage.from("guincho-inspections").downloadAuthenticated(path)
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(remote).joinToString("") { "%02x".format(it) }
                if (digest != files.hash(file)) throw InspectionException(InspectionFailure.IMAGE)
            }
        }
        checkAccount(account, generation)
    }
    suspend fun photoCommand(name: String, doc: GuinchoInspectionDocument, photoId: String, operation: String): GuinchoInspectionDocument =
        mutate(name, JsonObject(command(doc, operation) + buildJsonObject { put("p_photo_id", photoId) })).inspection.domain()
    suspend fun submit(doc: GuinchoInspectionDocument, operation: String, versionId: String): InspectionSubmitResult = call("submit_guincho_inspection") {
        val result = client.postgrest.rpc("submit_guincho_inspection", JsonObject(command(doc, operation) + buildJsonObject {
            put("p_version_id", versionId)
        })).decodeAs<GuinchoInspectionEnvelopeDto>()
        InspectionSubmitResult(InspectionCommandOutcome.valueOf(result.outcome), result.inspection.domain())
    }
    suspend fun decide(doc: GuinchoInspectionDocument, operation: String, approve: Boolean, reason: String): GuinchoInspectionDocument =
        mutate("decide_guincho_inspection", JsonObject(command(doc, operation) + buildJsonObject {
            put("p_version_id", requireNotNull(doc.version).id); put("p_decision", if (approve) "APPROVE" else "REQUEST_CORRECTION")
            if (!approve) put("p_reason", reason.trim())
        })).inspection.domain()
    suspend fun beginCorrection(doc: GuinchoInspectionDocument): GuinchoInspectionDocument = mutate("begin_guincho_inspection_correction", buildJsonObject {
        put("p_appointment_id", doc.appointmentId); put("p_expected_revision", doc.revision)
        put("p_parent_version_id", requireNotNull(doc.version).id)
    }).inspection.domain()
    suspend fun download(photo: GuinchoInspectionPhoto, account: String, thumbnail: Boolean = false): File {
        val generation = files.generation
        checkAccount(account, generation)
        return downloadLocks.getOrPut(files.cacheKey(account, photo.id, thumbnail && photo.thumbnailPath != null)) { Mutex() }.withLock { downloads.withPermit { call("download") {
        val useThumb = thumbnail && photo.thumbnailPath != null
        val path = if (useThumb) requireNotNull(photo.thumbnailPath) else photo.objectPath
        val expectedSize = if (useThumb) requireNotNull(photo.thumbnailByteSize) else photo.byteSize
        checkAccount(account, generation)
        // The caller holds the authorized document; each uncached read still passes Storage RLS.
        val file = files.downloadFile(account, photo.id, useThumb)
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() != expectedSize) {
                val bytes = client.storage.from("guincho-inspections").downloadAuthenticated(path)
                checkAccount(account, generation)
                if (bytes.size.toLong() != expectedSize) throw InspectionException(InspectionFailure.IMAGE)
                files.commitMutex.withLock {
                    currentCoroutineContext().ensureActive(); checkAccount(account, generation)
                    file.parentFile?.mkdirs()
                    val temp = File(file.path + ".part")
                    try { temp.writeBytes(bytes); check(temp.renameTo(file)) } finally { temp.delete() }
                }
            }
            checkAccount(account, generation)
            file.setLastModified(System.currentTimeMillis())
            files.trimDownloads(account, file)
        }
        file
    } } } }

    suspend fun captureResult(id: String, success: Boolean, elapsed: Long = android.os.SystemClock.elapsedRealtimeNanos()) = call("capture_result") {
        val item = pending.find(id) ?: return@call
        if (item.stage != InspectionCaptureStage.AWAITING_CAMERA) return@call
        val generation = files.generation
        val capturedAt = if (success) Instant.now().toString() else null
        // Android delivers ActivityResult before ON_START. Auth 3.2.6 switches to
        // Initializing in the background and restores its user on ON_START.
        // A temporarily null user here does not mean the camera session expired.
        client.auth.awaitInitialization()
        checkAccount(item.accountId, generation)
        if (pending.find(id)?.stage != InspectionCaptureStage.AWAITING_CAMERA) return@call
        pending.put(item.copy(stage = if (success) InspectionCaptureStage.RAW_CAPTURED else InspectionCaptureStage.DISCARDED,
            capturedAt = capturedAt,
            capturedElapsedNanos = if (success) elapsed else null, capturedBootCount = if (success) files.bootCount else null), generation)
        Unit
    }
    suspend fun retryCapture(id: String, discard: Boolean = false) {
        val appointment = pending.find(id)?.appointmentId ?: return
        val lock = pipelines.getOrPut(appointment) { Mutex() }
        if (!lock.tryLock()) return
        try {
            var item = pending.find(id)?.takeIf { it.pending } ?: return
            checkAccount(item.accountId, files.generation)
            if (!discard && !item.retryable) return
            if (!discard && item.capturedAt == null) {
                // Explicit recovery after a lost camera result; never infer acceptance automatically.
                val capturedTime = withContext(Dispatchers.IO) {
                    val raw = files.raw(item)
                    if (!raw.isFile || raw.length() == 0L || raw.lastModified() <= 0) throw InspectionException(InspectionFailure.IMAGE)
                    raw.lastModified()
                }
                item = item.copy(capturedAt = Instant.ofEpochMilli(capturedTime).toString())
            }
            val stage = if (item.originalHash != null) InspectionCaptureStage.PREPARED else InspectionCaptureStage.RAW_CAPTURED
            pending.put(item.copy(stage = stage, failure = null, retryAt = 0, discardRequested = discard), files.generation)
        } finally { lock.unlock() }
    }
    suspend fun wakePending(account: String) {
        val generation = files.generation
        for (item in pending.captures.value.filter { it.accountId == account && it.stage == InspectionCaptureStage.RETRY_WAIT }) {
            checkAccount(account, generation)
            pending.put(item.copy(retryAt = 0), generation)
        }
    }
    suspend fun syncPending(account: String) {
        val work = pending.captures.value.filter { it.accountId == account && it.pending && it.stage !in setOf(
            InspectionCaptureStage.AWAITING_CAMERA, InspectionCaptureStage.NEEDS_RECAPTURE, InspectionCaptureStage.BLOCKED_CONTEXT) && it.retryAt <= System.currentTimeMillis() }
        for (item in work) pipelines.getOrPut(item.appointmentId) { Mutex() }.withLock {
            val current = pending.find(item.captureId) ?: return@withLock
            if (current.pending) process(current)
        }
    }
    private suspend fun process(initial: InspectionPendingCapture) {
        val gen = files.generation
        var item = initial
        suspend fun save(next: InspectionPendingCapture) {
            checkAccount(item.accountId, gen); item = pending.put(next, gen)
            if (BuildConfig.DEBUG && next.stage == InspectionCaptureStage.SAVED && next.capturedBootCount == files.bootCount) {
                next.capturedElapsedNanos?.let { start ->
                    val elapsed = android.os.SystemClock.elapsedRealtimeNanos() - start
                    if (elapsed >= 0) Log.d("GuinchoInspection", "stage=saved duration_ms=${elapsed / 1_000_000}")
                }
            }
        }
        try {
            checkAccount(item.accountId, gen)
            // Publish ownership before the first network read so the UI cannot offer a competing retry.
            save(item.copy(stage = if (item.originalHash == null) InspectionCaptureStage.PREPARING else InspectionCaptureStage.RESERVING,
                resumeStage = item.stage, failure = null))
            var doc = document(item.appointmentId)
            // Replay before editability/file checks recovers READY even after the service advances.
            if (item.originalHash != null || item.photo != null) {
                val response = try {
                    if (item.photo?.thumbnailPath == null && item.photo != null) prepareLegacy(doc, item) else prepare(doc, item)
                } catch (error: InspectionException) {
                    if (error.failure == InspectionFailure.CONTEXT && item.discardRequested && item.photo == null &&
                        !doc.closed && doc.version?.id == item.versionId && doc.summary.actions.contains("CAPTURE")) {
                        save(item.copy(stage = InspectionCaptureStage.DISCARDED)); return
                    }
                    throw error
                }
                doc = response.inspection.domain()
                response.photo?.let { save(item.copy(photo = it.validate())) }
                if (item.photo?.state == InspectionPhotoState.READY) {
                    save(item.copy(stage = InspectionCaptureStage.SAVED, failure = null)); return
                }
                if (item.photo?.abandonedAt != null) {
                    val ownDiscard = item.photo?.discardOperationId == item.discardOperationId
                    if (ownDiscard && item.repairReservation) {
                        if (doc.closed || doc.version?.id != item.versionId || "CAPTURE" !in doc.summary.actions) throw InspectionException(InspectionFailure.CLOSED)
                        item = pending.restartRepairedReservation(item, gen)
                    } else {
                        if (ownDiscard || item.discardRequested && !doc.closed && doc.version?.id == item.versionId && doc.summary.actions.contains("CAPTURE")) {
                            save(item.copy(stage = InspectionCaptureStage.DISCARDED, failure = null)); return
                        }
                        throw InspectionException(InspectionFailure.CONTEXT)
                    }
                }
                if (item.photo != null && doc.pending.none { it.id == item.photo?.id }) {
                    val result = photoCommand(if (item.photo?.thumbnailPath != null) "confirm_guincho_inspection_photo_v2" else "confirm_guincho_inspection_photo", doc,
                        requireNotNull(item.photo).id, item.confirmOperationId)
                    if (result.revision > 0) { save(item.copy(stage = InspectionCaptureStage.SAVED, failure = null)); return }
                }
            }
            if (doc.closed || !doc.summary.actions.contains("CAPTURE") || doc.version?.id != item.versionId) throw InspectionException(InspectionFailure.CLOSED)
            if (item.discardRequested) {
                item.photo?.let { photoCommand("discard_guincho_inspection_photo", doc, it.id, item.discardOperationId) }
                save(item.copy(stage = InspectionCaptureStage.DISCARDED)); return
            }
            if (item.repairReservation || item.originalHash != null && !withContext(Dispatchers.IO) { files.assetsValid(item) } &&
                !files.restoreAssets(item, gen)) {
                save(item.copy(repairReservation = true))
                val oldPhoto = requireNotNull(item.photo)
                photoCommand("discard_guincho_inspection_photo", doc, oldPhoto.id, item.discardOperationId)
                doc = document(item.appointmentId)
                val replay = if (oldPhoto.thumbnailPath == null) prepareLegacy(doc, item) else prepare(doc, item)
                save(item.copy(photo = requireNotNull(replay.photo)))
                if (item.photo?.state == InspectionPhotoState.READY) { save(item.copy(stage = InspectionCaptureStage.SAVED)); return }
                if (item.photo?.abandonedAt == null || item.photo?.discardOperationId != item.discardOperationId) throw InspectionException(InspectionFailure.CONTEXT)
                item = pending.restartRepairedReservation(item, gen)
            }
            if (item.originalHash == null) {
                save(item.copy(stage = InspectionCaptureStage.PREPARING))
                files.prepare(item, gen)
                withContext(Dispatchers.IO) {
                    save(item.copy(stage = InspectionCaptureStage.PREPARED, originalSize = files.original(item).length(), thumbnailSize = files.thumbnail(item).length(),
                        originalHash = files.hash(files.original(item)), thumbnailHash = files.hash(files.thumbnail(item))))
                }
            }
            if (item.photo == null) {
                save(item.copy(stage = InspectionCaptureStage.RESERVING))
                val response = prepare(doc, item)
                save(item.copy(photo = requireNotNull(response.photo).validate()))
            }
            val photo = requireNotNull(item.photo)
            if (photo.state == InspectionPhotoState.READY) { save(item.copy(stage = InspectionCaptureStage.SAVED)); return }
            withContext(Dispatchers.IO) {
                if (!files.original(item).isFile || files.hash(files.original(item)) != item.originalHash) throw InspectionException(InspectionFailure.IMAGE)
            }
            save(item.copy(stage = InspectionCaptureStage.UPLOADING_ORIGINAL))
            upload(item.accountId, photo, files.original(item))
            if (photo.thumbnailPath != null) {
                withContext(Dispatchers.IO) {
                    if (!files.thumbnail(item).isFile || files.hash(files.thumbnail(item)) != item.thumbnailHash) throw InspectionException(InspectionFailure.IMAGE)
                }
                save(item.copy(stage = InspectionCaptureStage.UPLOADING_THUMBNAIL)); upload(item.accountId, photo, files.thumbnail(item), true)
            }
            save(item.copy(stage = InspectionCaptureStage.CONFIRMING))
            doc = document(item.appointmentId)
            photoCommand(if (photo.thumbnailPath == null) "confirm_guincho_inspection_photo" else "confirm_guincho_inspection_photo_v2", doc, photo.id, item.confirmOperationId)
            save(item.copy(stage = InspectionCaptureStage.SAVED, failure = null))
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) {
            val failure = (error as? InspectionException)?.failure ?: if (error is IOException && files.pendingRoot.usableSpace < 4L * 1024 * 1024) InspectionFailure.SPACE else InspectionFailure.UNKNOWN
            if (files.generation != gen || runCatching { accountId() }.getOrNull() != item.accountId) return
            val state = when (failure) {
                InspectionFailure.CLOSED, InspectionFailure.FORBIDDEN, InspectionFailure.CONTEXT -> InspectionCaptureStage.BLOCKED_CONTEXT
                InspectionFailure.IMAGE -> if (withContext(Dispatchers.IO) { files.hasRecoverableSource(item) }) InspectionCaptureStage.RETRY_WAIT else InspectionCaptureStage.NEEDS_RECAPTURE
                else -> InspectionCaptureStage.RETRY_WAIT
            }
            val repair = item.repairReservation || failure == InspectionFailure.IMAGE && item.photo != null &&
                item.stage in setOf(InspectionCaptureStage.UPLOADING_ORIGINAL, InspectionCaptureStage.UPLOADING_THUMBNAIL)
            save(item.copy(stage = state, resumeStage = item.stage, failure = failure, repairReservation = repair, attempts = item.attempts + 1,
                retryAt = System.currentTimeMillis() + minOf(30_000L, 1_000L shl minOf(item.attempts, 5))))
        }
    }
    private suspend fun <T> call(operation: String, block: suspend () -> T): T = try {
        withTimeout(60_000) {
            try { block() } catch (error: Exception) {
                if (BuildConfig.DEBUG && error !is CancellationException) Log.w("GuinchoInspection",
                    "operation=$operation exception=${error.javaClass.simpleName} http_status=${(error as? RestException)?.statusCode}")
                throw error
            }
        }
    } catch (error: InspectionException) { throw error
    } catch (_: TimeoutCancellationException) { throw InspectionException(InspectionFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: io.ktor.client.plugins.HttpRequestTimeoutException) { throw InspectionException(InspectionFailure.TIMEOUT)
    } catch (_: IOException) { throw InspectionException(InspectionFailure.OFFLINE)
    } catch (error: RestException) {
        val message = error.error + " " + error.description.orEmpty()
        throw InspectionException(when {
            error.statusCode == 401 -> InspectionFailure.SESSION
            error.statusCode == 403 || message.contains("PARTICIPANT_REQUIRED") -> InspectionFailure.FORBIDDEN
            message.contains("OPERATION_CONFLICT") || message.contains("VERSION_CONFLICT") -> InspectionFailure.CONTEXT
            message.contains("CONFLICT") -> InspectionFailure.CONFLICT
            message.contains("CLOSED") || message.contains("INVALID_TRANSITION") -> InspectionFailure.CLOSED
            message.contains("PHOTO_NOT_SAVED") || message.contains("POINTS_MISSING") || message.contains("UPLOAD_PENDING") -> InspectionFailure.INCOMPLETE
            message.contains("IMAGE") || message.contains("FILE_TOO_LARGE") -> InspectionFailure.IMAGE
            error.statusCode == 404 || message.contains("PGRST202") -> InspectionFailure.SERVICE
            else -> InspectionFailure.UNKNOWN
        })
    } catch (_: IllegalArgumentException) { throw InspectionException(InspectionFailure.UNKNOWN)
    } catch (_: Exception) { throw InspectionException(InspectionFailure.UNKNOWN) }
}
