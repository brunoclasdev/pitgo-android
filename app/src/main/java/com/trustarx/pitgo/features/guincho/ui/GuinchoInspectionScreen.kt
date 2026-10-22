package com.trustarx.pitgo.features.guincho.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.viewmodel.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import java.io.File

internal fun inspectionPointLabel(point: InspectionPoint) = when (point) {
    InspectionPoint.FRONT -> R.string.inspection_front
    InspectionPoint.REAR -> R.string.inspection_rear
    InspectionPoint.LEFT -> R.string.inspection_left
    InspectionPoint.RIGHT -> R.string.inspection_right
    InspectionPoint.DETAIL -> R.string.inspection_detail
}
private fun slotLabel(slot: InspectionSlot) = when (slot.detail) {
    "TRUNK_INTERIOR" -> R.string.inspection_trunk_interior
    "INSTRUMENT_PANEL" -> R.string.inspection_instrument_panel
    "FRONT_SEATS" -> R.string.inspection_front_seats
    "REAR_SEATS" -> R.string.inspection_rear_seats
    "FULL_DASHBOARD" -> R.string.inspection_full_dashboard
    "TOOL_KIT" -> R.string.inspection_tool_kit
    "SPARE_TIRE" -> R.string.inspection_spare_tire
    "FRONT_LEFT_TIRE" -> R.string.inspection_front_left_tire
    "FRONT_RIGHT_TIRE" -> R.string.inspection_front_right_tire
    "REAR_RIGHT_TIRE" -> R.string.inspection_rear_right_tire
    "REAR_LEFT_TIRE" -> R.string.inspection_rear_left_tire
    "ENGINE" -> R.string.inspection_engine
    "BATTERY" -> R.string.inspection_battery
    else -> inspectionPointLabel(slot.point)
}
internal fun inspectionStateLabel(state: InspectionVersionState?) = when (state) {
    InspectionVersionState.DRAFT -> R.string.inspection_pending
    InspectionVersionState.SUBMITTED -> R.string.inspection_waiting
    InspectionVersionState.CORRECTION_REQUESTED -> R.string.inspection_correction
    InspectionVersionState.APPROVED -> R.string.inspection_approved
    null -> R.string.inspection_absent
}
private fun failureLabel(failure: InspectionFailure) = when (failure) {
    InspectionFailure.OFFLINE, InspectionFailure.TIMEOUT -> R.string.inspection_error_network
    InspectionFailure.SESSION, InspectionFailure.FORBIDDEN -> R.string.inspection_error_access
    InspectionFailure.CONFLICT, InspectionFailure.CONTEXT -> R.string.inspection_error_conflict
    InspectionFailure.CLOSED -> R.string.inspection_error_closed
    InspectionFailure.INCOMPLETE -> R.string.inspection_error_incomplete
    InspectionFailure.IMAGE -> R.string.inspection_error_image
    InspectionFailure.CAMERA -> R.string.inspection_error_camera
    InspectionFailure.SPACE -> R.string.inspection_error_space
    InspectionFailure.INTERRUPTED -> R.string.inspection_error_interrupted
    InspectionFailure.SERVICE -> R.string.inspection_error_service
    InspectionFailure.UNKNOWN -> R.string.inspection_error_unknown
}
@Composable private fun readableTime(value: String): String {
    val context = LocalContext.current
    return remember(value, context) { runCatching {
        val date = Date.from(Instant.parse(value))
        "${DateFormat.getMediumDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
    }.getOrDefault(value) }
}
private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuinchoInspectionRoute(viewModel: GuinchoInspectionViewModel, onBack: () -> Unit, onSessionExpired: () -> Unit, onApprovalConfirmed: () -> Unit, sessionVerified: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel, sessionVerified) {
        if (sessionVerified) viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val activity = context.activity()
    val phone = config.smallestScreenWidthDp < 600
    DisposableEffect(activity, phone) {
        val old = activity?.requestedOrientation
        if (phone) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (phone && old != null) activity?.requestedOrientation = old }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::cameraResult)
    LaunchedEffect(state.cameraUri, state.cameraLaunched, sessionVerified) {
        if (sessionVerified && state.cameraUri != null && !state.cameraLaunched) {
            viewModel.cameraLaunched()
            try { camera.launch(Uri.parse(state.cameraUri)) } catch (_: Exception) { viewModel.cameraResult(false) }
        }
    }
    LaunchedEffect(state.error, sessionVerified) {
        if (sessionVerified && viewModel.consumeSessionRefresh()) onSessionExpired()
    }
    // Keep the ActivityResult registration alive during session verification. Only album
    // content and network work are gated; removing the launcher loses the camera result.
    if (!sessionVerified) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LaunchedEffect(viewModel) { viewModel.approvalConfirmed.collect { onApprovalConfirmed() } }
    LaunchedEffect(state.submissionNavigation) {
        state.submissionNavigation?.let { operation ->
            onApprovalConfirmed()
            viewModel.submissionNavigationConsumed(operation)
        }
    }
    var enlarged by remember { mutableStateOf<GuinchoInspectionPhoto?>(null) }
    var localEnlarged by remember { mutableStateOf<File?>(null) }
    val doc = state.document
    val capturing = doc != null && !state.historical && doc.isProvider && doc.version?.state == InspectionVersionState.DRAFT
    val columns = if (config.fontScale >= 1.3f || config.screenWidthDp < 360) 1 else if (config.screenWidthDp >= 720) 4 else 2
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.operation, enlarged, localEnlarged) { withFrameNanos { }; viewModel.actionFrame() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.inspection_title)) }, navigationIcon = {
        TextButton(onClick = onBack) { Text(stringResource(R.string.guincho_back)) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
            item {
                doc?.let { Text(it.vehicle, style = MaterialTheme.typography.titleMedium); Text(stringResource(inspectionStateLabel(it.summary.state))); it.summary.correctionReason?.let { reason -> Text(reason) } }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(stringResource(failureLabel(it)), color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.inspection_retry)) } }
                if (doc == null) Text(stringResource(R.string.inspection_loading))
            }
            if (state.historical) {
                item { TextButton(onClick = { viewModel.selectVersion(null) }) { Text(stringResource(R.string.inspection_all)) } }
                items(state.versions, key = { "version:${it.id}" }) { version ->
                    OutlinedButton(onClick = { viewModel.selectVersion(version.id) }, enabled = !state.busy) {
                        Text("${stringResource(R.string.inspection_version, version.number)} — ${stringResource(inspectionStateLabel(version.state))}")
                    }
                }
                if (state.hasMoreVersions) item { TextButton(onClick = viewModel::moreVersions) { Text(stringResource(R.string.inspection_more_versions)) } }
            }
            (state.versions.firstOrNull { it.id == state.selectedVersion } ?: doc?.version?.takeIf { it.id == state.selectedVersion })?.let { version -> item {
                Text(stringResource(R.string.inspection_version, version.number), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(inspectionStateLabel(version.state)))
                version.correctionReason?.let { Text(it) }
                version.decidedAt?.let { Text(stringResource(R.string.inspection_decided_at, readableTime(it))) }
                version.decidedByName?.let { Text(stringResource(R.string.inspection_decided_by, it)) }
            } }
            if (capturing) {
                item { Text(stringResource(R.string.inspection_capture_hint)); Text(stringResource(R.string.inspection_progress, doc.summary.readyPoints)) }
                items(INSPECTION_SLOTS.chunked(columns), key = { "slots:${it.first().key}" }) { slots ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                        slots.forEach { slot ->
                            val photo = (doc.namedPhotos + state.photos + doc.requiredPhotos).firstOrNull { it.slot == slot }
                            val pending = state.pending.lastOrNull { it.pending && it.versionId == doc.version?.id && it.slot == slot }
                            val preview = pending?.let(viewModel::pendingPreview)
                            photo?.let { LaunchedEffect(it.id) { viewModel.loadPhoto(it) } }
                            PhotoTile(stringResource(slotLabel(slot)), stringResource(if (slot.required) R.string.inspection_required else R.string.inspection_optional),
                                preview ?: photo?.let { state.thumbnails[it.id] },
                                pending?.let { pendingStatus(it.stage) } ?: stringResource(if (photo == null) R.string.inspection_point_pending else R.string.inspection_saved),
                                photo?.let { it.id in state.thumbnailFailed } == true,
                                { if (preview != null) localEnlarged = preview else if (photo != null) enlarged = photo else viewModel.capture(slot) },
                                { success -> if (pending != null && success) scope.launch { withFrameNanos { }; viewModel.captureFrame(pending.captureId) }
                                    else if (photo != null) {
                                        viewModel.imageResult(photo.id, success)
                                        if (success) scope.launch { withFrameNanos { }; viewModel.imageFrame(photo.id, true) }
                                    } }, Modifier.weight(1f), loading = photo != null && preview == null && photo.id !in state.thumbnails && photo.id !in state.thumbnailFailed) {
                                if (pending != null) PendingActions(pending, viewModel, state.canEditPhotos, state.canRetrySave(pending), state.canRecapture(pending))
                                else {
                                    if (state.canCapture) TextButton(onClick = { viewModel.capture(slot, photo?.id) }) { Text(stringResource(if (photo == null) R.string.inspection_take_photo else R.string.inspection_retake)) }
                                    if (photo != null && !slot.required && state.canEditPhotos) TextButton(onClick = { viewModel.unselect(photo) }) { Text(stringResource(R.string.inspection_remove)) }
                                    if (photo?.id in state.thumbnailFailed) TextButton(onClick = { photo?.let { viewModel.loadPhoto(it, true) } }) { Text(stringResource(R.string.inspection_retry)) }
                                }
                            }
                        }
                        repeat(columns - slots.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item {
                    OutlinedTextField(state.detailCaption, viewModel::detailCaption, label = { Text(stringResource(R.string.inspection_detail_caption)) }, modifier = Modifier.fillMaxWidth(), enabled = state.canCapture)
                    TextButton(onClick = { viewModel.capture(InspectionSlot(InspectionPoint.DETAIL)) }, enabled = state.canCapture) { Text(stringResource(R.string.inspection_detail)) }
                }
                items(state.pending.filter { it.pending && it.versionId == doc.version?.id && it.slot.key == "DETAIL" }, key = { "local:${it.captureId}" }) { pending ->
                    PhotoTile(stringResource(R.string.inspection_detail), pending.caption, viewModel.pendingPreview(pending), pendingStatus(pending.stage), false,
                        { localEnlarged = viewModel.pendingPreview(pending) }, { ok -> if (ok) scope.launch { withFrameNanos { }; viewModel.captureFrame(pending.captureId) } }) { PendingActions(pending, viewModel, state.canEditPhotos, state.canRetrySave(pending), state.canRecapture(pending)) }
                }
                items(doc.pending.filter { photo -> state.pending.none { it.pending && it.photo?.id == photo.id } }, key = { "remote:${it.id}" }) { photo ->
                    Text("${stringResource(slotLabel(photo.slot))}: ${stringResource(R.string.inspection_pending_upload)}")
                    Row { TextButton(onClick = { viewModel.pending(photo, false) }) { Text(stringResource(R.string.inspection_confirm_upload)) }
                        TextButton(onClick = { viewModel.pending(photo, true) }) { Text(stringResource(R.string.inspection_discard)) } }
                }
                item { Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.inspection_submit)) } }
                items(state.pending.filter { it.pending && it.versionId != doc.version?.id }, key = { "previous:${it.captureId}" }) { previous ->
                    Text(stringResource(R.string.inspection_previous_pending), style = MaterialTheme.typography.bodySmall)
                    PhotoTile(stringResource(slotLabel(previous.slot)), previous.caption, viewModel.pendingPreview(previous), pendingStatus(previous.stage), false,
                        { localEnlarged = viewModel.pendingPreview(previous) }, {})
                }
            }
            val gallery = if (capturing) state.photos.filter { it.slot !in INSPECTION_SLOTS } else state.photos
            items(gallery.chunked(columns), key = { "gallery:${state.selectedVersion}:${it.first().id}" }) { photos ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                    photos.forEach { photo ->
                        LaunchedEffect(photo.id) { viewModel.loadPhoto(photo) }
                        PhotoTile(stringResource(slotLabel(photo.slot)), photo.caption.orEmpty(), state.thumbnails[photo.id],
                            stringResource(if (state.historical && state.selectedVersion == null && !photo.sent) R.string.inspection_unsent else R.string.inspection_saved),
                            photo.id in state.thumbnailFailed, { viewModel.actionStart(); enlarged = photo }, { success ->
                                viewModel.imageResult(photo.id, success)
                                if (success) scope.launch { withFrameNanos { }; viewModel.imageFrame(photo.id, true) }
                            }, Modifier.weight(1f), loading = photo.id !in state.thumbnails && photo.id !in state.thumbnailFailed) {
                            if (photo.id in state.thumbnailFailed) TextButton(onClick = { viewModel.loadPhoto(photo, true) }) { Text(stringResource(R.string.inspection_retry)) }
                            if (state.canCapture) {
                                TextButton(onClick = { viewModel.capture(photo.slot, photo.id) }) { Text(stringResource(R.string.inspection_retake)) }
                                TextButton(onClick = { viewModel.unselect(photo) }) { Text(stringResource(R.string.inspection_remove)) }
                            }
                        }
                    }
                    repeat(columns - photos.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (!capturing && doc?.isProvider == true) {
                items(state.pending.filter { it.pending }, key = { "unshared:${it.captureId}" }) { pending ->
                    Text(stringResource(R.string.inspection_unshared_pending), style = MaterialTheme.typography.bodySmall)
                    PhotoTile(stringResource(slotLabel(pending.slot)), pending.caption, viewModel.pendingPreview(pending), pendingStatus(pending.stage), false,
                        { localEnlarged = viewModel.pendingPreview(pending) }, { ok -> if (ok) scope.launch { withFrameNanos { }; viewModel.captureFrame(pending.captureId) } }) {
                        pending.failure?.let { Text(stringResource(failureLabel(it)), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (state.hasMorePhotos) item { TextButton(onClick = viewModel::morePhotos) { Text(stringResource(R.string.inspection_more)) } }
            if (!state.historical && doc?.summary?.actions?.contains("BEGIN_CORRECTION") == true) item {
                Button(onClick = viewModel::beginCorrection, enabled = !state.busy) { Text(stringResource(R.string.inspection_begin_correction)) }
            }
            if (!state.historical && doc?.isProvider == false && doc.summary.actions.contains("APPROVE")) item {
                Text(stringResource(R.string.inspection_review_hint))
                Text(stringResource(R.string.inspection_review_progress, state.loaded.size, state.photos.size))
                state.photos.filter { it.id in state.failed }.forEach { photo ->
                    TextButton(onClick = { viewModel.loadPhoto(photo, true, false) }) { Text("${stringResource(R.string.inspection_retry)}: ${stringResource(slotLabel(photo.slot))}") }
                }
                Button(onClick = { viewModel.decide(true) }, enabled = state.canApprove, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.inspection_approve)) }
                OutlinedTextField(state.reason, viewModel::reason, label = { Text(stringResource(R.string.inspection_reason)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { viewModel.decide(false) }, enabled = !state.busy && state.reason.trim().isNotEmpty()) { Text(stringResource(R.string.inspection_request_correction)) }
            }
        }
    }
    enlarged?.let { photo -> LaunchedEffect(photo.id) { viewModel.loadPhoto(photo, thumbnail = false) } }
    if (enlarged != null || localEnlarged != null) Dialog(onDismissRequest = { enlarged = null; localEnlarged = null }) {
        Surface { Column(Modifier.fillMaxWidth().padding(PitGoSpacing.SmallMedium)) {
            val photo = enlarged
            val file = if (photo == null) localEnlarged else state.files[photo.id]
            if (file != null) AsyncImage(ImageRequest.Builder(context).data(file).memoryCacheKey("guincho-inspection:${file.path}").build(), stringResource(R.string.inspection_open_photo), modifier = Modifier.fillMaxWidth().heightIn(max = (config.screenHeightDp * 0.6f).dp), contentScale = ContentScale.Fit,
                onSuccess = { photo?.let { scope.launch { withFrameNanos { }; viewModel.imageFrame(it.id, false) } } },
                onError = { photo?.let { viewModel.imageResult(it.id, false, false) } })
            else if (photo?.id in state.failed) TextButton(onClick = { photo?.let { viewModel.loadPhoto(it, true, false) } }) { Text(stringResource(R.string.inspection_retry)) }
            else CircularProgressIndicator()
            photo?.let {
                Text(stringResource(slotLabel(it.slot))); it.caption?.let { caption -> Text(caption) }
                Text(stringResource(R.string.inspection_author, it.capturedByName))
                Text(stringResource(R.string.inspection_captured_at, readableTime(it.capturedAt)))
                it.savedAt?.let { time -> Text(stringResource(R.string.inspection_saved_at, readableTime(time))) }
            }
            TextButton(onClick = { enlarged = null; localEnlarged = null }) { Text(stringResource(R.string.inspection_close_photo)) }
        } }
    }
}
@Composable private fun pendingStatus(stage: InspectionCaptureStage) = stringResource(when (stage) {
    InspectionCaptureStage.RAW_CAPTURED, InspectionCaptureStage.PREPARING -> R.string.inspection_preparing
    InspectionCaptureStage.RETRY_WAIT, InspectionCaptureStage.NEEDS_RECAPTURE, InspectionCaptureStage.BLOCKED_CONTEXT -> R.string.inspection_failed
    InspectionCaptureStage.SAVED -> R.string.inspection_saved
    else -> R.string.inspection_saving
})
@Composable private fun PendingActions(item: InspectionPendingCapture, vm: GuinchoInspectionViewModel, editableVersion: Boolean, canRetrySave: Boolean, canRecapture: Boolean) {
    item.failure?.let { Text(stringResource(failureLabel(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (canRetrySave) {
        TextButton(onClick = { vm.retryPending(item.captureId) }) { Text(stringResource(R.string.inspection_retry_save)) }
    } else if (canRecapture) {
        TextButton(onClick = { vm.recapturePending(item.captureId) }) { Text(stringResource(R.string.inspection_recapture)) }
    }
    if (editableVersion && item.stage in setOf(InspectionCaptureStage.RETRY_WAIT, InspectionCaptureStage.NEEDS_RECAPTURE, InspectionCaptureStage.BLOCKED_CONTEXT) && !item.discardRequested) {
        TextButton(onClick = { vm.retryPending(item.captureId, true) }) { Text(stringResource(R.string.inspection_discard)) }
    }
}
@Composable private fun PhotoTile(label: String, hint: String, file: File?, status: String, failed: Boolean, onClick: () -> Unit,
    onImageResult: (Boolean) -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, actions: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clickable(onClickLabel = stringResource(R.string.inspection_open_photo), onClick = onClick), contentAlignment = Alignment.Center) {
            if (file != null) AsyncImage(ImageRequest.Builder(LocalContext.current).data(file).memoryCacheKey("guincho-inspection:${file.path}").build(), "$label — $status",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onSuccess = { onImageResult(true) }, onError = { onImageResult(false) })
            else if (loading) CircularProgressIndicator(Modifier.size(28.dp))
            else Text(stringResource(if (failed) R.string.inspection_error_image else R.string.inspection_take_photo), modifier = Modifier.padding(PitGoSpacing.SmallMedium), style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.padding(PitGoSpacing.SmallMedium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            actions()
        }
    }
}
