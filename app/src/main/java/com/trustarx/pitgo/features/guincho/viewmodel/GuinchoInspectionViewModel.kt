package com.trustarx.pitgo.features.guincho.viewmodel

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.BuildConfig
import com.trustarx.pitgo.core.environment.EnvironmentConfig
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

enum class InspectionOperation { IDLE, LOADING, CAPTURING, PROCESSING, SAVING, SUBMITTING, DECIDING }
data class GuinchoInspectionUiState(
    val document: GuinchoInspectionDocument? = null,
    val operation: InspectionOperation = InspectionOperation.IDLE,
    val error: InspectionFailure? = null,
    val photos: List<GuinchoInspectionPhoto> = emptyList(),
    val versions: List<GuinchoInspectionVersion> = emptyList(),
    val selectedVersion: String? = null,
    val hasMorePhotos: Boolean = false,
    val hasMoreVersions: Boolean = false,
    val files: Map<String, File> = emptyMap(),
    val thumbnails: Map<String, File> = emptyMap(),
    val loaded: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
    val thumbnailFailed: Set<String> = emptySet(),
    val pending: List<InspectionPendingCapture> = emptyList(),
    val cameraUri: String? = null,
    val cameraLaunched: Boolean = false,
    val captureId: String? = null,
    val detailCaption: String = "",
    val reason: String = "",
    val historical: Boolean = false,
    val allPhotos: Boolean = false,
    val recoverableCaptures: Set<String> = emptySet(),
    val submissionNavigation: String? = null,
) {
    val busy get() = operation != InspectionOperation.IDLE
    val canApprove get() = !busy && document?.summary?.actions?.contains("APPROVE") == true &&
        selectedVersion == document.summary.versionId && !hasMorePhotos && photos.size == document.summary.photoCount &&
        photos.isNotEmpty() && photos.all { it.id in loaded && it.id !in failed }
    val canCapture get() = !historical && !busy && cameraUri == null && document?.isProvider == true && document.summary.actions.contains("CAPTURE")
    val canEditPhotos get() = !historical && !busy && document?.isProvider == true && document.summary.actions.contains("CAPTURE")
    val canSubmit get() = canEditPhotos && document?.summary?.readyPoints == 4 && document.pending.isEmpty() && pending.none { it.pending && it.versionId == document.version?.id }
    fun canRetrySave(item: InspectionPendingCapture) = canEditPhotos && item.versionId == document?.version?.id &&
        item.retryable && item.captureId in recoverableCaptures
    fun canRecapture(item: InspectionPendingCapture) = canEditPhotos && item.versionId == document?.version?.id &&
        item.stage == InspectionCaptureStage.NEEDS_RECAPTURE && item.captureId !in recoverableCaptures
}

class GuinchoInspectionViewModel(
    private val appointmentId: String,
    private val account: String,
    private val repository: GuinchoInspectionRepository,
    private val saved: SavedStateHandle,
    historical: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(GuinchoInspectionUiState(historical = historical))
    val state = _state.asStateFlow()
    private val approvalEvents = Channel<Unit>(Channel.BUFFERED)
    val approvalConfirmed = approvalEvents.receiveAsFlow()
    private var poll: Job? = null
    private var preload: Job? = null
    private val imageJobs = mutableMapOf<String, Job>()
    private var cursor: Pair<Long?, String?> = null to null
    private var versionCursor: Int? = null
    private val measurement = mutableMapOf<String, Long>()
    private var pendingIds = emptySet<String>()
    private var allowCameraLaunch = false
    private var sessionRefreshRequested = false
    private var resolvingCameraId: String? = null
    init {
        if (saved.get<String>("account") != account || saved.get<String>("environment") != EnvironmentConfig.fingerprint) {
            listOf("captureId", "cameraLaunched", "raw", "preview", "cameraUri", "submitVersion", "submitOperation", "submitConfirmed").forEach { saved.remove<Any>(it) }
        }
        saved["account"] = account; saved["environment"] = EnvironmentConfig.fingerprint
        _state.update { it.copy(submissionNavigation = saved["submitConfirmed"]) }
        viewModelScope.launch {
            repository.pending.load(account)
            importLegacyCapture()
            // A task restored without its ActivityResult cannot assume the raw file is accepted.
            repository.pending.captures.collect { all ->
                val items = all.filter { it.accountId == account && it.appointmentId == appointmentId }
                val justSaved = items.any { it.captureId in pendingIds && it.stage == InspectionCaptureStage.SAVED }
                pendingIds = items.filter { it.pending }.map { it.captureId }.toSet()
                val active = items.firstOrNull { it.captureId == saved.get<String>("captureId") && it.stage == InspectionCaptureStage.AWAITING_CAMERA }
                val recoverable = withContext(Dispatchers.IO) { items.filter { it.retryable && repository.files.hasRecoverableSource(it) }.map { it.captureId }.toSet() }
                _state.update { it.copy(pending = items, captureId = active?.captureId, cameraUri = active?.let(repository.files::uri),
                    recoverableCaptures = recoverable, cameraLaunched = active != null && (!allowCameraLaunch || (saved["cameraLaunched"] ?: false))) }
                if (justSaved) viewModelScope.launch { refresh(true) }
            }
        }
    }
    private suspend fun importLegacyCapture() {
        val legacyPath = saved.get<String>("preview") ?: saved.get<String>("raw") ?: return
        val version = saved.get<String>("captureVersion") ?: return
        val point = saved.get<String>("point")?.let { runCatching { InspectionPoint.valueOf(it) }.getOrNull() } ?: return
        val reserve = operation("reserveOperation")
        if (repository.pending.captures.value.any { it.reserveOperationId == reserve }) return
        try {
            checkSession()
            val doc = repository.document(appointmentId)
            val remoteId = saved.get<String>("reservedPhotoId")
            var remote = (doc.pending + doc.requiredPhotos).firstOrNull { it.id == remoteId }
            if (remoteId != null && remote == null) {
                var after: Pair<Long?, String?> = null to null
                do {
                    val page = repository.photos(appointmentId, version, after.first, after.second)
                    remote = page.items.firstOrNull { it.id == remoteId }
                    after = page.nextOrder to page.nextId
                } while (remote == null && page.hasMore)
            }
            if (remoteId != null && remote == null) throw InspectionException(InspectionFailure.CONFLICT)
            if (remote != null && (remote.point != point || remote.reservedVersionId != version || remote.capturedBy != account)) throw InspectionException(InspectionFailure.CONFLICT)
            val original = remote
            val item = repository.pending.create(account, appointmentId, version, InspectionSlot(point, original?.detailPoint), saved["caption"] ?: "", saved["replaces"])
                .copy(reserveOperationId = original?.reserveOperationId ?: reserve, confirmOperationId = operation("confirmOperation"),
                    capturedAt = original?.capturedAt ?: saved["capturedAt"], photo = original, originalSize = original?.byteSize ?: 0)
            val recovered = withContext(Dispatchers.IO) {
                val old = repository.files.capture(account, legacyPath)
                if (original?.state == InspectionPhotoState.READY) item.copy(stage = InspectionCaptureStage.SAVED)
                else if (!old.isFile || old.length() == 0L || item.capturedAt == null) item.copy(stage = InspectionCaptureStage.NEEDS_RECAPTURE, failure = InspectionFailure.INTERRUPTED)
                else {
                    old.copyTo(repository.files.raw(item), overwrite = false)
                    if (original != null) {
                        if (old.length() != original.byteSize) throw InspectionException(InspectionFailure.IMAGE)
                        old.copyTo(repository.files.original(item), overwrite = false)
                        item.copy(stage = InspectionCaptureStage.PREPARED, originalSize = old.length(), originalHash = repository.files.hash(old))
                    } else item.copy(stage = InspectionCaptureStage.RAW_CAPTURED)
                }
            }
            repository.pending.put(recovered, repository.files.generation)
            listOf("raw", "preview", "point", "captureVersion", "caption", "capturedAt", "reservedPhotoId", "reserveOperation", "confirmOperation", "replaces").forEach { saved.remove<Any>(it) }
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) { _state.update { it.copy(error = (error as? InspectionException)?.failure ?: InspectionFailure.IMAGE) } }
    }
    fun onVisible() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            refresh()
            if (saved.get<String>("submitVersion") != null && saved.get<String>("submitConfirmed") == null) submit()
            delay(1_500)
            repository.pending.captures.value.filter { it.appointmentId == appointmentId && it.stage == InspectionCaptureStage.AWAITING_CAMERA && it.captureId != resolvingCameraId }.forEach { item ->
                if (_state.value.cameraLaunched || item.captureId != saved.get<String>("captureId")) {
                    try {
                        repository.pending.put(item.copy(stage = InspectionCaptureStage.NEEDS_RECAPTURE, failure = InspectionFailure.INTERRUPTED), repository.files.generation)
                    } catch (error: InspectionException) { _state.update { it.copy(error = error.failure) } }
                    saved.remove<String>("captureId")
                }
            }
            while (isActive) { refresh(); delay(2_000) }
        }
    }
    fun onHidden() { poll?.cancel(); poll = null; preload?.cancel() }
    fun retry() {
        sessionRefreshRequested = false
        if (saved.get<String>("submitVersion") != null) { submit(); return }
        action(InspectionOperation.LOADING) { refresh(true) }
    }
    fun consumeSessionRefresh(): Boolean {
        if (_state.value.error != InspectionFailure.SESSION || sessionRefreshRequested) return false
        sessionRefreshRequested = true
        _state.update { it.copy(error = null) }
        if (BuildConfig.DEBUG) Log.d("GuinchoInspection", "stage=session_refresh_requested")
        return true
    }
    private suspend fun refresh(force: Boolean = false) {
        try {
            checkSession()
            val doc = repository.document(appointmentId)
            sessionRefreshRequested = false
            val old = _state.value
            if (doc.revision < (old.document?.revision ?: 0)) return
            val readOnly = old.historical || doc.closed
            val selected = if (readOnly && old.allPhotos) null else if (readOnly) old.selectedVersion ?: doc.summary.versionId else doc.summary.versionId
            _state.update { it.copy(document = doc, historical = readOnly, error = null) }
            if (force || old.document?.revision != doc.revision || selected != old.selectedVersion) {
                var page = repository.photos(appointmentId, selected)
                val items = page.items.toMutableList()
                if (selected == old.selectedVersion) while (page.hasMore && items.size < old.photos.size) {
                    page = repository.photos(appointmentId, selected, page.nextOrder, page.nextId)
                    items.addAll(page.items)
                }
                cursor = page.nextOrder to page.nextId
                val ids = (items + doc.namedPhotos).map { it.id }.toSet()
                val versions = if (readOnly) repository.versions(appointmentId) else null
                versionCursor = versions?.nextVersion
                _state.update { it.copy(photos = items, selectedVersion = selected, hasMorePhotos = page.hasMore,
                    files = it.files.filterKeys(ids::contains), thumbnails = it.thumbnails.filterKeys(ids::contains),
                    loaded = it.loaded.intersect(ids), failed = it.failed.intersect(ids), thumbnailFailed = it.thumbnailFailed.intersect(ids),
                    versions = versions?.items.orEmpty(), hasMoreVersions = versions?.hasMore ?: false) }
                imageJobs.filterKeys { it.substringBefore(':') !in ids }.values.forEach { it.cancel() }
            }
            preloadOriginals()
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: InspectionException) {
            if (error.failure in setOf(InspectionFailure.SESSION, InspectionFailure.FORBIDDEN)) {
                imageJobs.values.forEach { it.cancel() }; preload?.cancel()
                _state.update { it.copy(files = emptyMap(), thumbnails = emptyMap(), loaded = emptySet()) }
            }
            _state.update { it.copy(error = error.failure) }
        }
    }
    private suspend fun checkSession() {
        repository.awaitSession()
        if (repository.accountId() != account || saved.get<String>("environment") != EnvironmentConfig.fingerprint) throw InspectionException(InspectionFailure.SESSION)
    }
    private fun action(operation: InspectionOperation, block: suspend () -> Unit) {
        if (_state.value.busy) return
        actionStart()
        _state.update { it.copy(operation = operation, error = null) }
        viewModelScope.launch {
            try { checkSession(); block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: InspectionException) { _state.update { it.copy(error = error.failure) } }
            catch (_: Exception) { _state.update { it.copy(error = InspectionFailure.UNKNOWN) } }
            finally { _state.update { it.copy(operation = InspectionOperation.IDLE) } }
        }
    }
    fun capture(slot: InspectionSlot, replacing: String? = null) {
        if (!_state.value.canCapture || _state.value.pending.any { it.pending && it.versionId == _state.value.document?.version?.id && it.slot == slot && slot.key != "DETAIL" }) return
        action(InspectionOperation.CAPTURING) {
            allowCameraLaunch = true
            val doc = requireNotNull(_state.value.document)
            val item = repository.pending.create(account, appointmentId, requireNotNull(doc.version).id, slot,
                if (slot.point == InspectionPoint.DETAIL && slot.detail == null) _state.value.detailCaption.trim() else "", replacing)
            saved["captureId"] = item.captureId; saved["cameraLaunched"] = false
            _state.update { it.copy(captureId = item.captureId, cameraUri = repository.files.uri(item), cameraLaunched = false) }
        }
    }
    fun cameraLaunched() { saved["cameraLaunched"] = true; _state.update { it.copy(cameraLaunched = true) } }
    fun cameraResult(success: Boolean) {
        if (BuildConfig.DEBUG) Log.d("GuinchoInspection", "stage=camera_result accepted=$success pending_capture=${saved.get<String>("captureId") != null}")
        val id = saved.get<String>("captureId") ?: return
        resolvingCameraId = id
        val elapsed = SystemClock.elapsedRealtimeNanos()
        if (success) {
            measurement[id] = elapsed
            if (BuildConfig.DEBUG && android.os.Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection("inspection_capture_to_frame", id.hashCode())
        }
        saved.remove<String>("captureId"); saved.remove<Boolean>("cameraLaunched")
        _state.update { it.copy(cameraUri = null, captureId = null, cameraLaunched = false) }
        viewModelScope.launch {
            try { repository.captureResult(id, success, elapsed) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: InspectionException) { _state.update { it.copy(error = error.failure) } }
            catch (_: Exception) { _state.update { it.copy(error = InspectionFailure.IMAGE) } }
            finally { if (resolvingCameraId == id) resolvingCameraId = null }
        }
    }
    fun pendingPreview(item: InspectionPendingCapture) = repository.files.preview(item)
    fun captureFrame(id: String) {
        val start = measurement.remove(id) ?: return
        if (BuildConfig.DEBUG) {
            if (android.os.Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection("inspection_capture_to_frame", id.hashCode())
            Log.d("GuinchoInspection", "stage=album_frame duration_ms=${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000}")
        }
    }
    fun actionStart() { measurement["action"] = SystemClock.elapsedRealtimeNanos() }
    fun actionFrame() = duration("action", "action_frame")
    fun imageFrame(id: String, thumbnail: Boolean) = duration("$id:$thumbnail", if (thumbnail) "thumbnail_frame" else "original_frame")
    private fun duration(key: String, stage: String) {
        val start = measurement.remove(key) ?: return
        if (BuildConfig.DEBUG) Log.d("GuinchoInspection", "stage=$stage duration_ms=${(SystemClock.elapsedRealtimeNanos()-start)/1_000_000}")
    }
    fun retryPending(id: String, discard: Boolean = false) {
        val item = _state.value.pending.firstOrNull { it.captureId == id } ?: return
        if (!_state.value.canEditPhotos || item.versionId != _state.value.document?.version?.id || !discard && !_state.value.canRetrySave(item)) return
        action(InspectionOperation.SAVING) { repository.retryCapture(id, discard) }
    }
    fun recapturePending(id: String) {
        val item = _state.value.pending.firstOrNull { it.captureId == id } ?: return
        if (!_state.value.canRecapture(item)) return
        action(InspectionOperation.CAPTURING) {
            repository.retryCapture(id, discard = true)
            repository.syncPending(account)
            if (repository.pending.find(id)?.stage != InspectionCaptureStage.DISCARDED) throw InspectionException(InspectionFailure.CONFLICT)
            val next = repository.pending.create(account, appointmentId, item.versionId, item.slot, item.caption, item.replacesPhotoId)
            allowCameraLaunch = true
            saved["captureId"] = next.captureId; saved["cameraLaunched"] = false
            _state.update { it.copy(captureId = next.captureId, cameraUri = repository.files.uri(next), cameraLaunched = false) }
        }
    }
    fun detailCaption(value: String) { _state.update { it.copy(detailCaption = value.take(1000)) } }
    fun reason(value: String) { _state.update { it.copy(reason = value.take(1000)) } }
    private fun operation(key: String): String = saved.get<String>(key) ?: UUID.randomUUID().toString().also { saved[key] = it }
    fun submit() {
        val pendingVersion = saved.get<String>("submitVersion")
        if (pendingVersion == null && !_state.value.canSubmit || saved.get<String>("submitConfirmed") != null) return
        action(InspectionOperation.SUBMITTING) {
            val doc = _state.value.document ?: repository.document(appointmentId)
            val version = pendingVersion ?: requireNotNull(doc.version).id.also { saved["submitVersion"] = it }
            val op = operation("submitOperation")
            val result = repository.submit(doc, op, version)
            if (result.outcome == InspectionCommandOutcome.REVISION_CONFLICT) {
                _state.update { it.copy(document = result.document) }
                throw InspectionException(InspectionFailure.CONFLICT)
            }
            saved["submitConfirmed"] = op
            _state.update { it.copy(document = result.document, submissionNavigation = op) }
        }
    }
    fun submissionNavigationConsumed(operation: String) {
        if (saved.get<String>("submitConfirmed") != operation) return
        listOf("submitVersion", "submitOperation", "submitConfirmed").forEach { saved.remove<String>(it) }
        _state.update { it.copy(submissionNavigation = null) }
    }
    fun decide(approve: Boolean) {
        if (approve && !_state.value.canApprove || !approve && _state.value.reason.trim().isEmpty()) return
        action(InspectionOperation.DECIDING) {
            val doc = requireNotNull(_state.value.document)
            val payload = "${doc.version?.id}:$approve:${if (approve) "" else _state.value.reason.trim()}"
            if (saved.get<String>("decisionPayload") != payload) { saved["decisionPayload"] = payload; saved.remove<String>("decisionOperation") }
            val result = repository.decide(doc, operation("decisionOperation"), approve, _state.value.reason)
            if (approve && (result.version?.id != doc.version?.id || result.version?.state != InspectionVersionState.APPROVED)) throw InspectionException(InspectionFailure.CONFLICT)
            saved.remove<String>("decisionOperation")
            _state.update { it.copy(document = result, reason = "") }
            if (approve) approvalEvents.send(Unit) else refresh(true)
        }
    }
    fun beginCorrection() = action(InspectionOperation.SAVING) { repository.beginCorrection(requireNotNull(_state.value.document)); refresh(true) }
    fun pending(photo: GuinchoInspectionPhoto, discard: Boolean) = action(InspectionOperation.SAVING) {
        repository.photoCommand(if (discard) "discard_guincho_inspection_photo" else if (photo.thumbnailPath == null) "confirm_guincho_inspection_photo" else "confirm_guincho_inspection_photo_v2",
            requireNotNull(_state.value.document), photo.id, operation("pending:${photo.id}:$discard")); refresh(true)
    }
    fun unselect(photo: GuinchoInspectionPhoto) = action(InspectionOperation.SAVING) {
        repository.photoCommand("unselect_guincho_inspection_photo", requireNotNull(_state.value.document), photo.id, operation("unselect:${photo.id}")); refresh(true)
    }
    fun morePhotos() = action(InspectionOperation.LOADING) {
        val page = repository.photos(appointmentId, _state.value.selectedVersion, cursor.first, cursor.second)
        cursor = page.nextOrder to page.nextId
        _state.update { it.copy(photos = (it.photos + page.items).distinctBy { p -> p.id }, hasMorePhotos = page.hasMore) }
        preloadOriginals()
    }
    fun moreVersions() = action(InspectionOperation.LOADING) {
        val page = repository.versions(appointmentId, versionCursor); versionCursor = page.nextVersion
        _state.update { it.copy(versions = (it.versions + page.items).distinctBy { v -> v.id }, hasMoreVersions = page.hasMore) }
    }
    fun selectVersion(id: String?) = action(InspectionOperation.LOADING) {
        imageJobs.values.forEach { it.cancel() }; imageJobs.clear(); preload?.cancel()
        val page = repository.photos(appointmentId, id); cursor = page.nextOrder to page.nextId
        _state.update { it.copy(selectedVersion = id, allPhotos = id == null, photos = page.items, hasMorePhotos = page.hasMore,
            files = emptyMap(), thumbnails = emptyMap(), loaded = emptySet(), failed = emptySet(), thumbnailFailed = emptySet()) }
    }
    private fun preloadOriginals() {
        if (_state.value.document?.isProvider != false || !_state.value.document!!.summary.actions.contains("APPROVE") || preload?.isActive == true) return
        preload = viewModelScope.launch {
            for (photo in _state.value.photos) if (photo.id !in _state.value.loaded && photo.id !in _state.value.failed) fetch(photo, false)
        }
    }
    fun loadPhoto(photo: GuinchoInspectionPhoto, retry: Boolean = false, thumbnail: Boolean = true) {
        val key = "${photo.id}:$thumbnail"
        if (!thumbnail) actionStart()
        measurement[key] = SystemClock.elapsedRealtimeNanos()
        if (imageJobs[key]?.isActive == true || (!retry && (if (thumbnail) photo.id in _state.value.thumbnails else photo.id in _state.value.loaded))) return
        if (!thumbnail) preload?.cancel()
        imageJobs[key] = viewModelScope.launch { fetch(photo, thumbnail, retry) }
    }
    private suspend fun fetch(photo: GuinchoInspectionPhoto, thumbnail: Boolean, retry: Boolean = false) {
        val version = _state.value.selectedVersion
        try {
            checkSession()
            if (retry) withContext(Dispatchers.IO) { repository.files.downloadFile(account, photo.id, thumbnail && photo.thumbnailPath != null).delete() }
            val file = repository.download(photo, account, thumbnail)
            if (!thumbnail) withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bitmap = BitmapFactory.decodeFile(file.path, options) ?: throw InspectionException(InspectionFailure.IMAGE)
                bitmap.recycle()
            }
            checkSession()
            if (_state.value.selectedVersion != version || (_state.value.photos + _state.value.document?.namedPhotos.orEmpty()).none { it.id == photo.id }) return
            _state.update { if (thumbnail) it.copy(thumbnails = it.thumbnails + (photo.id to file), thumbnailFailed = it.thumbnailFailed - photo.id)
                else it.copy(files = it.files + (photo.id to file), loaded = it.loaded + photo.id, failed = it.failed - photo.id) }
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { imageResult(photo.id, false, thumbnail) }
    }
    fun imageResult(id: String, success: Boolean, thumbnail: Boolean = true) {
        if (!success) _state.update { if (thumbnail) it.copy(thumbnailFailed = it.thumbnailFailed + id)
            else it.copy(loaded = it.loaded - id, failed = it.failed + id) }
    }
    fun cacheKey(id: String, thumbnail: Boolean = true) = repository.files.cacheKey(account, id, thumbnail)
}
