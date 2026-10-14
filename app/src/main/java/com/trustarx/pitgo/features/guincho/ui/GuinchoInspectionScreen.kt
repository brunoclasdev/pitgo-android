package com.trustarx.pitgo.features.guincho.ui

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.viewmodel.*
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
    InspectionFailure.CONFLICT -> R.string.inspection_error_conflict
    InspectionFailure.CLOSED -> R.string.inspection_error_closed
    InspectionFailure.INCOMPLETE -> R.string.inspection_error_incomplete
    InspectionFailure.IMAGE -> R.string.inspection_error_image
    InspectionFailure.CAMERA -> R.string.inspection_error_camera
    InspectionFailure.UNKNOWN -> R.string.inspection_error_unknown
}
@Composable
private fun readableTime(value: String): String {
    val context = LocalContext.current
    return remember(value, context) {
        runCatching { val date = Date.from(Instant.parse(value))
            "${DateFormat.getMediumDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
        }.getOrDefault(value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuinchoInspectionRoute(viewModel: GuinchoInspectionViewModel, onBack: () -> Unit, onSessionExpired: () -> Unit,
    onApprovalConfirmed: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) { viewModel.onVisible(); onStopOrDispose { viewModel.onHidden() } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::cameraResult)
    LaunchedEffect(state.cameraUri, state.cameraLaunched) {
        if (state.cameraUri != null && !state.cameraLaunched) {
            viewModel.cameraLaunched()
            try { camera.launch(Uri.parse(state.cameraUri)) } catch (_: Exception) { viewModel.cameraResult(false) }
        }
    }
    LaunchedEffect(state.error) { if (state.error == InspectionFailure.SESSION) onSessionExpired() }
    LaunchedEffect(viewModel) { viewModel.approvalConfirmed.collect { onApprovalConfirmed() } }
    var enlarged by remember(state.selectedVersion) { mutableStateOf<GuinchoInspectionPhoto?>(null) }
    var previewEnlarged by remember { mutableStateOf(false) }
    val doc = state.document
    val capturing = doc != null && !state.historical && doc.isProvider && doc.version?.state == InspectionVersionState.DRAFT
    val configuration = LocalConfiguration.current
    val columns = if (configuration.fontScale >= 1.3f || configuration.screenWidthDp < 360) 1 else if (configuration.screenWidthDp >= 720) 4 else 2
    val galleryPhotos = if (capturing) state.photos.filter { it.point == InspectionPoint.DETAIL } else state.photos
    val activeVersion = state.versions.firstOrNull { it.id == state.selectedVersion } ?: doc?.version?.takeIf { it.id == state.selectedVersion }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.inspection_title)) }, navigationIcon = {
        TextButton(onClick = onBack) { Text(stringResource(R.string.guincho_back)) }
    }) }, bottomBar = {
        if (capturing && state.preview != null) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(PitGoSpacing.SmallMedium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                Text(stringResource(R.string.inspection_preview_point, stringResource(inspectionPointLabel(state.capturePoint ?: InspectionPoint.DETAIL))), style = MaterialTheme.typography.labelLarge)
                state.error?.let { Text(stringResource(failureLabel(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                    Button(onClick = viewModel::savePhoto, enabled = state.canEditPhotos, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (state.operation == InspectionOperation.SAVING) R.string.inspection_saving else R.string.inspection_save))
                    }
                    TextButton(onClick = viewModel::discardPreview, enabled = !state.busy && doc?.pending?.isEmpty() == true, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.inspection_discard))
                    }
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
            item {
                doc?.let {
                    Text(it.vehicle, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(inspectionStateLabel(it.summary.state)))
                    it.summary.correctionReason?.let { reason -> Text(reason) }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(stringResource(failureLabel(it)), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry, enabled = !state.busy) { Text(stringResource(R.string.inspection_retry)) }
                }
            }
            if (doc == null) item { Text(stringResource(R.string.inspection_loading)) }
            if (doc != null && state.historical) {
                item { TextButton(onClick = { viewModel.selectVersion(null) }, enabled = !state.busy) { Text(stringResource(R.string.inspection_all)) } }
                items(state.versions, key = { "version:${it.id}" }) { version ->
                    OutlinedButton(onClick = { viewModel.selectVersion(version.id) }, enabled = !state.busy) {
                        Text("${stringResource(R.string.inspection_version, version.number)} — ${stringResource(inspectionStateLabel(version.state))}")
                    }
                }
                if (state.hasMoreVersions) item { TextButton(onClick = viewModel::moreVersions, enabled = !state.busy) { Text(stringResource(R.string.inspection_more_versions)) } }
            }
            activeVersion?.let { version -> item {
                Text(stringResource(R.string.inspection_version, version.number), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(inspectionStateLabel(version.state)))
                version.correctionReason?.let { Text(it) }
                version.decidedAt?.let { Text(stringResource(R.string.inspection_decided_at, readableTime(it))) }
                version.decidedByName?.let { Text(stringResource(R.string.inspection_decided_by, it)) }
            } }
            if (doc != null && capturing) {
                item {
                    Text(stringResource(R.string.inspection_capture_hint))
                    Text(stringResource(R.string.inspection_progress, doc.summary.readyPoints))
                }
                items(InspectionPoint.entries.filter { it != InspectionPoint.DETAIL }.chunked(columns), key = { "points:${it.first()}" }) { points ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                        points.forEach { point ->
                            val photo = doc.requiredPhotos.firstOrNull { it.point == point }
                            val preview = state.preview.takeIf { state.capturePoint == point }
                            photo?.let { LaunchedEffect(it.id, state.imageGeneration) { viewModel.loadPhoto(it) } }
                            val file = preview ?: photo?.let { state.files[it.id] }
                            val pending = doc.pending.any { it.point == point }
                            InspectionPhotoTile(
                                label = stringResource(inspectionPointLabel(point)),
                                status = stringResource(when {
                                    preview != null && state.operation == InspectionOperation.SAVING -> R.string.inspection_saving
                                    preview != null -> R.string.inspection_local_preview
                                    pending -> R.string.inspection_pending_upload
                                    photo != null -> R.string.inspection_saved
                                    else -> R.string.inspection_point_pending
                                }),
                                file = file, cacheKey = if (preview == null && photo != null) viewModel.cacheKey(photo.id) else null,
                                loading = photo != null && file == null && photo.id !in state.failed,
                                failed = preview == null && photo?.let { it.id in state.failed } == true,
                                onClick = when {
                                    preview != null -> ({ previewEnlarged = true })
                                    photo != null && file != null -> ({ enlarged = photo })
                                    state.canCapture -> ({ viewModel.capture(point, photo?.id) })
                                    else -> null
                                },
                                onImageResult = { success -> if (preview == null) photo?.let { viewModel.imageResult(it.id, success) } },
                                onRetry = { photo?.let { viewModel.loadPhoto(it, true) } }, modifier = Modifier.weight(1f),
                            ) {
                                if (photo != null && state.canCapture) TextButton(onClick = { viewModel.capture(point, photo.id) }) {
                                    Text(stringResource(R.string.inspection_retake))
                                }
                            }
                        }
                        repeat(columns - points.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item {
                    OutlinedTextField(state.detailCaption, viewModel::detailCaption, label = { Text(stringResource(R.string.inspection_detail_caption)) }, modifier = Modifier.fillMaxWidth(), enabled = state.canCapture)
                    TextButton(onClick = { viewModel.capture(InspectionPoint.DETAIL) }, enabled = state.canCapture) { Text(stringResource(R.string.inspection_detail)) }
                }
                state.preview?.takeIf { state.capturePoint == InspectionPoint.DETAIL }?.let { preview -> item {
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PitGoSpacing.SmallMedium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                        Text(stringResource(R.string.inspection_preview_point, stringResource(inspectionPointLabel(state.capturePoint ?: InspectionPoint.DETAIL))), style = MaterialTheme.typography.titleSmall)
                        if (state.capturePoint == InspectionPoint.DETAIL) AsyncImage(preview, stringResource(R.string.inspection_preview), contentScale = ContentScale.Crop,
                            modifier = Modifier.size(112.dp).clickable { previewEnlarged = true })
                        if (state.caption.isNotBlank()) Text(state.caption)
                    } }
                } }
                items(doc.pending, key = { "pending:${it.id}" }) { photo ->
                    Text("${stringResource(inspectionPointLabel(photo.point))}: ${stringResource(R.string.inspection_pending_upload)}")
                    TextButton(onClick = { viewModel.pending(photo, false) }, enabled = !state.busy) { Text(stringResource(R.string.inspection_confirm_upload)) }
                    TextButton(onClick = { viewModel.pending(photo, true) }, enabled = !state.busy) { Text(stringResource(R.string.inspection_discard)) }
                }
                item { Button(onClick = viewModel::submit, enabled = !state.busy && doc.summary.readyPoints == 4 && doc.pending.isEmpty() && state.preview == null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inspection_submit))
                } }
            }
            if (doc != null && !state.historical && doc.summary.actions.contains("BEGIN_CORRECTION")) item {
                Button(onClick = viewModel::beginCorrection, enabled = !state.busy) { Text(stringResource(R.string.inspection_begin_correction)) }
            }
            if (doc != null && !doc.isProvider && !state.historical && doc.summary.actions.contains("APPROVE")) item {
                Text(stringResource(R.string.inspection_review_hint))
            }
            items(galleryPhotos.chunked(columns), key = { "${state.selectedVersion}:${it.first().id}" }) { photos ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                    photos.forEach { photo ->
                        LaunchedEffect(photo.id, state.imageGeneration) { viewModel.loadPhoto(photo) }
                        InspectionPhotoTile(
                            label = stringResource(inspectionPointLabel(photo.point)),
                            status = stringResource(if (state.historical && state.selectedVersion == null && !photo.sent) R.string.inspection_unsent else R.string.inspection_saved),
                            file = state.files[photo.id], cacheKey = viewModel.cacheKey(photo.id),
                            loading = photo.id !in state.files && photo.id !in state.failed, failed = photo.id in state.failed,
                            onClick = if (photo.id in state.files) ({ enlarged = photo }) else null,
                            onImageResult = { viewModel.imageResult(photo.id, it) }, onRetry = { viewModel.loadPhoto(photo, true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            photo.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            if (state.canCapture) {
                                TextButton(onClick = { viewModel.capture(photo.point, photo.id) }) { Text(stringResource(R.string.inspection_retake)) }
                                TextButton(onClick = { viewModel.unselect(photo) }) { Text(stringResource(R.string.inspection_remove)) }
                            }
                        }
                    }
                    repeat(columns - photos.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.hasMorePhotos) item { TextButton(onClick = viewModel::morePhotos, enabled = !state.busy) { Text(stringResource(R.string.inspection_more)) } }
            if (doc != null && !state.historical && !doc.isProvider && doc.summary.actions.contains("APPROVE")) item {
                Button(onClick = { viewModel.decide(true) }, enabled = state.canApprove, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.inspection_approve)) }
                OutlinedTextField(state.reason, viewModel::reason, label = { Text(stringResource(R.string.inspection_reason)) }, minLines = 2, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { viewModel.decide(false) }, enabled = !state.busy && state.reason.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.inspection_request_correction)) }
            }
        }
    }
    val enlargedFile = enlarged?.let { state.files[it.id] } ?: state.preview.takeIf { previewEnlarged }
    enlargedFile?.let { file -> Dialog(onDismissRequest = { enlarged = null; previewEnlarged = false }) {
        Surface { Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp).padding(PitGoSpacing.SmallMedium)) {
            AsyncImage(file, stringResource(R.string.inspection_open_photo), modifier = Modifier.fillMaxWidth().weight(1f, fill = false), onError = { enlarged?.let { viewModel.imageResult(it.id, false) } })
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                enlarged?.let { photo ->
                    Text(stringResource(inspectionPointLabel(photo.point)), style = MaterialTheme.typography.titleSmall)
                    photo.caption?.let { Text(it) }
                    Text(stringResource(R.string.inspection_author, photo.capturedByName))
                    Text(stringResource(R.string.inspection_captured_at, readableTime(photo.capturedAt)))
                    photo.savedAt?.let { Text(stringResource(R.string.inspection_saved_at, readableTime(it))) }
                }
            }
            TextButton(onClick = { enlarged = null; previewEnlarged = false }) { Text(stringResource(R.string.inspection_close_photo)) }
        } }
    } }
}

@Composable
private fun InspectionPhotoTile(
    label: String, status: String, file: File?, cacheKey: String?, loading: Boolean, failed: Boolean,
    onClick: (() -> Unit)?, onImageResult: (Boolean) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit = {},
) {
    OutlinedCard(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clickable(enabled = onClick != null, onClickLabel = if (file == null) stringResource(R.string.inspection_take_photo) else stringResource(R.string.inspection_open_photo)) { onClick?.invoke() }, contentAlignment = Alignment.Center) {
            if (file != null) AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file).apply { cacheKey?.let { memoryCacheKey(it) } }.build(),
                contentDescription = "$label — $status", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { onImageResult(true) }, onError = { onImageResult(false) },
            ) else if (loading) CircularProgressIndicator(Modifier.size(28.dp))
            else Text(stringResource(if (failed) R.string.inspection_error_image else R.string.inspection_take_photo),
                modifier = Modifier.padding(PitGoSpacing.SmallMedium), style = MaterialTheme.typography.labelLarge,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(PitGoSpacing.SmallMedium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.inspection_retry)) }
            actions()
        }
    }
}
