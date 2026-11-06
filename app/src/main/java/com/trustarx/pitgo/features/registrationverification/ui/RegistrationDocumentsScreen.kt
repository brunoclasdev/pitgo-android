package com.trustarx.pitgo.features.registrationverification.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.registrationverification.data.*
import com.trustarx.pitgo.features.registrationverification.model.*
import com.trustarx.pitgo.features.registrationverification.viewmodel.*
import kotlinx.serialization.json.*
import java.io.File

@Composable
fun RegistrationDocumentsScreen(viewModel:RegistrationVerificationViewModel,files:RegistrationDocumentCaptureFiles,
 userId:String,onCompleteData:()->Unit,onRefreshSession:()->Unit,onBack:()->Unit,onLogout:()->Unit,showBack:Boolean=true) {
 val state by viewModel.state.collectAsStateWithLifecycle()
 androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
 val context=androidx.compose.ui.platform.LocalContext.current
 val previewLoader=remember(context){coil3.ImageLoader.Builder(context).diskCache(null).memoryCache(null).build()}
 DisposableEffect(previewLoader){onDispose{previewLoader.shutdown()}}
 var capture by remember {mutableStateOf<Triple<File,DocumentKind,String?>?>(null)}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->
  capture?.let {(file,kind,vehicle)->if(success)viewModel.captured(file,kind,vehicle) else file.delete()};capture=null
 }
 val case=state.case
 Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(stringResource(R.string.registration_title),style=MaterialTheme.typography.headlineSmall)
  Text(if(case?.accountType=="PROVIDER")stringResource(R.string.registration_provider) else stringResource(R.string.registration_client))
  if(state.busy)CircularProgressIndicator()
  state.error?.let {Text(stringResource(when(it){
   RegistrationFailure.OFFLINE->R.string.registration_offline
   RegistrationFailure.TIMEOUT->R.string.registration_timeout
   RegistrationFailure.SESSION->R.string.registration_session_error
   RegistrationFailure.CONFLICT->R.string.registration_conflict
   RegistrationFailure.INVALID_INPUT->R.string.registration_invalid
   else->R.string.registration_service_error
  }),color=MaterialTheme.colorScheme.error)
   TextButton(onClick=viewModel::refresh,enabled=!state.busy){Text(stringResource(R.string.registration_refresh))}
   TextButton(onClick=viewModel::retryUpload,enabled=!state.busy){Text(stringResource(R.string.registration_retry_upload))}
  }
  if(case!=null) {
   RegistrationStatusScreen(case.status,case.reviews.firstOrNull()?.reason,case.reviews.firstOrNull()?.items.orEmpty())
   if(case.pendingItems.isNotEmpty()) {
    Text(stringResource(R.string.registration_pending_title))
    case.pendingItems.distinct().forEach {item-> Text(stringResource(when(item){
     "ADDRESS"->R.string.registration_address_pending;"PROVIDER"->R.string.registration_company_pending
     "CNH_FRONT"->R.string.registration_cnh_front;"CNH_BACK"->R.string.registration_cnh_back
     "SELFIE"->R.string.registration_selfie;"SELFIE_WITH_DOCUMENT"->R.string.registration_selfie_document
     else->R.string.registration_vehicle_pending
    }))}
   }
   val editable=case.submissionState==RegistrationSubmissionState.DRAFT && (case.status==RegistrationStatus.INCOMPLETE || case.status==RegistrationStatus.CORRECTION_REQUESTED)
   if(case.status==RegistrationStatus.CORRECTION_REQUESTED && case.submissionState!=RegistrationSubmissionState.DRAFT) {
    Button(onClick=viewModel::correct,enabled=!state.busy){Text(stringResource(R.string.registration_correct))}
   }
   if(editable) {
    OutlinedButton(onClick=onCompleteData,enabled=!state.busy){Text(stringResource(R.string.registration_complete_data))}
    Text(stringResource(R.string.registration_photo_guidance))
    val kinds=listOf(DocumentKind.CNH_FRONT,DocumentKind.CNH_BACK,DocumentKind.SELFIE,DocumentKind.SELFIE_WITH_DOCUMENT)
    val vehicles=case.data["vehicles"]?.jsonArray.orEmpty()
    val slots=kinds.map{it to null} + vehicles.map{DocumentKind.VEHICLE_DOCUMENT to it.jsonObject["id"]?.jsonPrimitive?.content}
    slots.forEach {(kind,vehicle)->
     val photo=case.evidence.firstOrNull {it.kind==kind && it.vehicleId==vehicle}
     val title=when(kind){DocumentKind.CNH_FRONT->R.string.registration_cnh_front;DocumentKind.CNH_BACK->R.string.registration_cnh_back;DocumentKind.SELFIE->R.string.registration_selfie;DocumentKind.SELFIE_WITH_DOCUMENT->R.string.registration_selfie_document;DocumentKind.VEHICLE_DOCUMENT->R.string.registration_vehicle_document}
     OutlinedButton(enabled=!state.busy,onClick={
      val (file,uri)=files.create(userId);capture=Triple(file,kind,vehicle);launcher.launch(uri)
     },modifier=Modifier.fillMaxWidth()) {
      Text(stringResource(title)+(if(vehicle!=null)" — "+vehicles.firstOrNull{it.jsonObject["id"]?.jsonPrimitive?.content==vehicle}?.jsonObject?.get("plate")?.jsonPrimitive?.content.orEmpty() else "")+if(photo?.uploadState==RegistrationUploadState.RECEIVED)" ✓" else "")
     }
     if(photo?.uploadState==RegistrationUploadState.RECEIVED)TextButton(onClick={viewModel.preview(photo.path)},enabled=!state.busy){Text(stringResource(R.string.registration_view_photo))}
    }
    if(case.accountType=="PROVIDER")RegistrationVehiclesForm(case.data,!state.busy,viewModel::save)
    Button(onClick=viewModel::submit,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.registration_submit))}
   }
   OutlinedButton(onClick={viewModel.refresh();onRefreshSession()},enabled=!state.busy){Text(stringResource(R.string.registration_refresh))}
  }
  if(showBack)TextButton(onClick=onBack){Text(stringResource(R.string.registration_back))}
  TextButton(onClick=onLogout){Text(stringResource(R.string.registration_logout))}
 }
 state.preview?.let {bytes->
  AlertDialog(onDismissRequest=viewModel::closePreview,confirmButton={TextButton(onClick={if(state.capturePending)viewModel.confirmCapture() else viewModel.closePreview()},enabled=!state.busy){Text(stringResource(if(state.capturePending)R.string.registration_confirm_photo else android.R.string.ok))}},dismissButton={TextButton(onClick=viewModel::closePreview){Text(stringResource(R.string.registration_back))}},text={
   coil3.compose.AsyncImage(model=bytes,contentDescription=stringResource(R.string.registration_view_photo),modifier=Modifier.fillMaxWidth().heightIn(max=420.dp),imageLoader=previewLoader)
  })
 }
}
