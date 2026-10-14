package com.trustarx.pitgo.features.guincho.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.environment.EnvironmentConfig
import com.trustarx.pitgo.BuildConfig
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
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
    val loaded: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
    val imageGeneration: Long = 0,
    val cameraUri: String? = null,
    val cameraLaunched: Boolean = false,
    val preview: File? = null,
    val capturePoint: InspectionPoint? = null,
    val caption: String = "",
    val detailCaption: String = "",
    val reason: String = "",
    val historical: Boolean = false,
    val allPhotos: Boolean = false,
) {
    val busy get() = operation != InspectionOperation.IDLE
    val canApprove get() = !busy && document?.summary?.actions?.contains("APPROVE") == true &&
        selectedVersion == document.summary.versionId && !hasMorePhotos && photos.size == document.summary.photoCount &&
        photos.isNotEmpty() && photos.all { it.id in loaded && it.id !in failed }
    val canCapture get() = !historical && !busy && preview == null && cameraUri == null && document?.isProvider == true &&
        document.pending.isEmpty() &&
        document.summary.actions.contains("CAPTURE")
    val canEditPhotos get() = !historical && !busy && document?.isProvider == true && document.summary.actions.contains("CAPTURE")
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
    private val imageJobs = mutableMapOf<String, Job>()
    private var photoCursor: Pair<Long?, String?> = null to null
    private var versionCursor: Int? = null

    init {
        if (saved.get<String>("account") != account || saved.get<String>("environment") != EnvironmentConfig.fingerprint) {
            clearCapture(); saved.remove<String>("detailCaption")
        }
        saved["account"] = account; saved["environment"] = EnvironmentConfig.fingerprint
        val point = saved.get<String>("point")?.let { runCatching { InspectionPoint.valueOf(it) }.getOrNull() }
        val preview = saved.get<String>("preview")?.let { runCatching { repository.files.capture(account, it) }.getOrNull() }?.takeIf { it.exists() }
        _state.value = _state.value.copy(cameraUri = saved["cameraUri"], cameraLaunched = saved["cameraLaunched"] ?: false,
            preview = preview, capturePoint = point, caption = saved["caption"] ?: "", detailCaption = saved["detailCaption"] ?: "")
    }
    fun onVisible() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (true) {
                if (!_state.value.busy) refresh()
                delay(2_000)
            }
        }
    }
    fun onHidden() { poll?.cancel(); poll = null }
    fun retry() = action(InspectionOperation.LOADING) { refresh(force = true) }
    private suspend fun refresh(force: Boolean = false) {
        try {
            checkSession()
            val doc = repository.document(appointmentId)
            val old = _state.value
            val readOnly = old.historical || doc.closed
            val selected = if (readOnly && old.allPhotos) null else if (readOnly) old.selectedVersion ?: doc.summary.versionId else doc.summary.versionId
            _state.value = old.copy(document = doc, historical = readOnly)
            if (force || old.document?.revision != doc.revision || selected != old.selectedVersion) {
                imageJobs.values.forEach { it.cancel() }; imageJobs.clear()
                val page = repository.photos(appointmentId, selected)
                photoCursor = page.nextOrder to page.nextId
                val versions = if (readOnly) repository.versions(appointmentId) else null
                versionCursor = versions?.nextVersion
                _state.value = _state.value.copy(photos = page.items, selectedVersion = selected, hasMorePhotos = page.hasMore,
                    loaded = emptySet(), failed = emptySet(), files = emptyMap(),
                    imageGeneration = _state.value.imageGeneration + 1,
                    versions = versions?.items.orEmpty(), hasMoreVersions = versions?.hasMore ?: false, error = null)
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: InspectionException) { _state.value = _state.value.copy(error = error.failure) }
    }
    private fun checkSession() {
        if (repository.accountId() != account || saved.get<String>("environment") != EnvironmentConfig.fingerprint)
            throw InspectionException(InspectionFailure.SESSION)
    }
    private fun action(operation: InspectionOperation, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(operation = operation, error = null)
        viewModelScope.launch {
            try { checkSession(); block()
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: InspectionException) {
                if (error.failure in setOf(InspectionFailure.CONFLICT, InspectionFailure.CLOSED)) refresh(force = true)
                _state.value = _state.value.copy(error = error.failure)
            } catch (error: Exception) {
                diagnostic(operation.name, error)
                _state.value = _state.value.copy(error = InspectionFailure.UNKNOWN)
            } finally { _state.value = _state.value.copy(operation = InspectionOperation.IDLE) }
        }
    }
    fun capture(point: InspectionPoint, replacing: String? = null) {
        if (!_state.value.canCapture) return
        try {
            checkSession()
            val caption = if (point == InspectionPoint.DETAIL) _state.value.detailCaption.trim() else ""
            val version = requireNotNull(_state.value.document?.version).id
            clearCapture()
            val (path, uri) = repository.files.newCapture(account)
            saved["raw"] = path; saved["cameraUri"] = uri; saved["point"] = point.name
            saved["captureVersion"] = version; saved["caption"] = caption
            saved["replaces"] = replacing; saved["cameraLaunched"] = false
            _state.value = _state.value.copy(cameraUri = uri, cameraLaunched = false, capturePoint = point,
                operation = InspectionOperation.CAPTURING, preview = null, caption = caption, error = null)
            trace("camera_requested")
        } catch (_: Exception) { _state.value = _state.value.copy(error = InspectionFailure.CAMERA) }
    }
    fun cameraLaunched() { saved["cameraLaunched"] = true; _state.value = _state.value.copy(cameraLaunched = true) }
    fun cameraResult(success: Boolean) {
        if (saved.get<String>("raw") == null || _state.value.operation in setOf(InspectionOperation.PROCESSING, InspectionOperation.SAVING)) return
        trace(if (success) "camera_return_ok" else "camera_return_cancelled")
        _state.value = _state.value.copy(cameraUri = null, operation = InspectionOperation.PROCESSING)
        saved["cameraUri"] = null
        viewModelScope.launch {
            try {
                checkSession()
                if (!success) { clearCapture(); return@launch }
                val file = repository.files.normalize(account, requireNotNull(saved.get<String>("raw")))
                saved["preview"] = file.path; saved["capturedAt"] = Instant.now().toString()
                _state.value = _state.value.copy(preview = file, operation = InspectionOperation.SAVING)
                trace("image_processed")
                persistPhoto()
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: InspectionException) {
                if (_state.value.preview == null) clearCapture()
                if (error.failure in setOf(InspectionFailure.CONFLICT, InspectionFailure.CLOSED)) refresh(force = true)
                _state.value = _state.value.copy(error = error.failure)
            } catch (error: Exception) {
                diagnostic(_state.value.operation.name, error)
                val processing = _state.value.preview == null
                if (processing) clearCapture()
                _state.value = _state.value.copy(error = if (processing) InspectionFailure.CAMERA else InspectionFailure.UNKNOWN)
            } finally { _state.value = _state.value.copy(operation = InspectionOperation.IDLE) }
        }
    }
    fun detailCaption(value: String) {
        if (!_state.value.canCapture) return
        saved["detailCaption"] = value.take(1000)
        _state.value = _state.value.copy(detailCaption = value.take(1000))
    }
    fun reason(value: String) { _state.value = _state.value.copy(reason = value.take(1000)) }
    fun discardPreview() { if (!_state.value.busy) clearCapture() }
    private fun diagnostic(operation: String, error: Exception) {
        if (BuildConfig.DEBUG) Log.w("GuinchoInspection", "operation=$operation exception=${error.javaClass.simpleName}")
    }
    private fun trace(stage: String) {
        if (BuildConfig.DEBUG) Log.d("GuinchoInspection", "stage=$stage")
    }
    private fun clearCapture() {
        listOf("raw", "preview").mapNotNull { saved.get<String>(it) }.distinct().forEach {
            runCatching { repository.files.capture(account, it).delete() }
        }
        for (key in listOf("raw","preview","cameraUri","cameraLaunched","point","replaces","capturedAt","captureVersion","reserveOperation","confirmOperation","reservedPhotoId","caption")) saved.remove<Any>(key)
        _state.value = _state.value.copy(cameraUri = null, cameraLaunched = false, preview = null, capturePoint = null, caption = "")
    }
    private fun operation(key: String): String = saved.get<String>(key) ?: UUID.randomUUID().toString().also { saved[key] = it }
    fun savePhoto() = action(InspectionOperation.SAVING) { persistPhoto() }
    private suspend fun persistPhoto() {
        checkSession()
        val before = repository.document(appointmentId)
        _state.value = _state.value.copy(document = before)
        if (before.closed || !before.summary.actions.contains("CAPTURE")) throw InspectionException(InspectionFailure.CLOSED)
        if (before.version?.id != saved.get<String>("captureVersion")) throw InspectionException(InspectionFailure.CONFLICT)
        val file = requireNotNull(_state.value.preview)
        trace("reserve_begin")
        val response = repository.prepare(before, requireNotNull(_state.value.capturePoint), _state.value.caption,
            requireNotNull(saved.get<String>("capturedAt")), file, operation("reserveOperation"), saved["replaces"])
        var doc = response.inspection.domain()
        _state.value = _state.value.copy(document = doc)
        val photo = requireNotNull(response.photo).validate()
        saved["reservedPhotoId"] = photo.id
        if (photo.state != InspectionPhotoState.READY) {
            trace("upload_begin")
            repository.upload(account, photo, file)
            trace("confirm_begin")
            doc = repository.photoCommand("confirm_guincho_inspection_photo", doc, photo.id, operation("confirmOperation"))
        }
        _state.value = _state.value.copy(document = doc)
        if (_state.value.capturePoint == InspectionPoint.DETAIL) {
            saved.remove<String>("detailCaption")
            _state.value = _state.value.copy(detailCaption = "")
        }
        trace("photo_saved")
        clearCapture(); refresh(force = true)
    }
    fun submit() = action(InspectionOperation.SUBMITTING) {
        repository.submit(requireNotNull(_state.value.document), operation("submitOperation"))
        saved.remove<String>("submitOperation"); refresh(force = true)
    }
    fun decide(approve: Boolean) {
        if (approve && !_state.value.canApprove) return
        if (!approve && _state.value.reason.trim().isEmpty()) return
        action(InspectionOperation.DECIDING) {
            val doc = requireNotNull(_state.value.document)
            val payload = "${doc.version?.id}:$approve:${if (approve) "" else _state.value.reason.trim()}"
            if (saved.get<String>("decisionPayload") != payload) { saved["decisionPayload"] = payload; saved.remove<String>("decisionOperation") }
            val result = repository.decide(doc, operation("decisionOperation"), approve, _state.value.reason)
            if (approve && (result.version?.id != doc.version?.id || result.version?.state != InspectionVersionState.APPROVED)) {
                throw InspectionException(InspectionFailure.CONFLICT)
            }
            saved.remove<String>("decisionOperation")
            _state.value = _state.value.copy(document = result, reason = "")
            if (approve) approvalEvents.send(Unit) else refresh(force = true)
        }
    }
    fun beginCorrection() = action(InspectionOperation.SAVING) {
        repository.beginCorrection(requireNotNull(_state.value.document)); refresh(force = true)
    }
    fun pending(photo: GuinchoInspectionPhoto, discard: Boolean) {
        val local = saved.get<String>("reservedPhotoId") == photo.id
        if (!discard && local && _state.value.preview != null) { savePhoto(); return }
        action(InspectionOperation.SAVING) {
            repository.photoCommand(if (discard) "discard_guincho_inspection_photo" else "confirm_guincho_inspection_photo",
                requireNotNull(_state.value.document), photo.id, operation(if (!discard && local) "confirmOperation" else "pending:${photo.id}:$discard"))
            if (discard || local) clearCapture()
            refresh(force = true)
        }
    }
    fun unselect(photo: GuinchoInspectionPhoto) = action(InspectionOperation.SAVING) {
        repository.photoCommand("unselect_guincho_inspection_photo", requireNotNull(_state.value.document), photo.id, operation("unselect:${photo.id}"))
        refresh(force = true)
    }
    fun morePhotos() = action(InspectionOperation.LOADING) {
        val page = repository.photos(appointmentId, _state.value.selectedVersion, photoCursor.first, photoCursor.second)
        photoCursor = page.nextOrder to page.nextId
        _state.value = _state.value.copy(photos = (_state.value.photos + page.items).distinctBy { it.id }, hasMorePhotos = page.hasMore)
    }
    fun moreVersions() = action(InspectionOperation.LOADING) {
        val page = repository.versions(appointmentId, versionCursor); versionCursor = page.nextVersion
        _state.value = _state.value.copy(versions = (_state.value.versions + page.items).distinctBy { it.id }, hasMoreVersions = page.hasMore)
    }
    fun selectVersion(id: String?) = action(InspectionOperation.LOADING) {
        imageJobs.values.forEach { it.cancel() }; imageJobs.clear()
        _state.value = _state.value.copy(selectedVersion = id, allPhotos = id == null)
        val page = repository.photos(appointmentId, id)
        photoCursor = page.nextOrder to page.nextId
        _state.value = _state.value.copy(photos = page.items, hasMorePhotos = page.hasMore, files = emptyMap(), loaded = emptySet(), failed = emptySet(), imageGeneration = _state.value.imageGeneration + 1)
    }
    fun loadPhoto(photo: GuinchoInspectionPhoto, retry: Boolean = false) {
        if (imageJobs[photo.id]?.isActive == true || (!retry && photo.id in _state.value.files)) return
        if (retry) {
            _state.value.files[photo.id]?.delete()
            _state.value = _state.value.copy(files = _state.value.files - photo.id, loaded = _state.value.loaded - photo.id, failed = _state.value.failed - photo.id)
        }
        imageJobs[photo.id] = viewModelScope.launch {
            try {
                checkSession(); val file = repository.download(photo, account)
                _state.value = _state.value.copy(files = _state.value.files + (photo.id to file), failed = _state.value.failed - photo.id)
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) { imageResult(photo.id, false) }
        }
    }
    fun imageResult(id: String, success: Boolean) {
        _state.value = if (success) _state.value.copy(loaded = _state.value.loaded + id, failed = _state.value.failed - id)
        else _state.value.copy(loaded = _state.value.loaded - id, failed = _state.value.failed + id)
    }
    fun cacheKey(id: String) = repository.files.cacheKey(account, id)
}
