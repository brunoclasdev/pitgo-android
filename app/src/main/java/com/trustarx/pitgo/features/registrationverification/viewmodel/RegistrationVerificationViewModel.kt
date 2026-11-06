package com.trustarx.pitgo.features.registrationverification.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.registrationverification.data.*
import com.trustarx.pitgo.features.registrationverification.model.*
import com.trustarx.pitgo.features.registrationverification.repository.RegistrationVerificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

data class RegistrationUiState(val case: RegistrationCase?=null,val busy:Boolean=false,val error:RegistrationFailure?=null,val preview:ByteArray?=null,val capturePending:Boolean=false)
class RegistrationVerificationViewModel(
 private val mode:String, private val repository:RegistrationVerificationRepository,
 private val files:RegistrationDocumentCaptureFiles,
):ViewModel() {
 private val owner=repository.userId()
 private val mutable=MutableStateFlow(RegistrationUiState())
 val state=mutable.asStateFlow()
 private var operation:String?=null
 private var capture:Triple<ByteArray,DocumentKind,String?>?=null
 private var pending:Triple<EvidenceReservation,ByteArray,String>?=null
 init { refresh() }
 private fun run(block:suspend ()->Unit) {
  if(mutable.value.busy)return
  mutable.update { it.copy(busy=true,error=null) }
  viewModelScope.launch { try {
   check(repository.userId()==owner);block();check(repository.userId()==owner)
  } catch(c:CancellationException){throw c} catch(e:Exception) {
   val failure=when(e){
    is io.ktor.client.plugins.HttpRequestTimeoutException -> RegistrationFailure.TIMEOUT
    is io.github.jan.supabase.exceptions.HttpRequestException -> RegistrationFailure.OFFLINE
    is io.github.jan.supabase.postgrest.exception.PostgrestRestException -> if(e.code=="40001") RegistrationFailure.CONFLICT else if(e.statusCode in 401..403) RegistrationFailure.SESSION else if(e.statusCode>=500) RegistrationFailure.SERVICE else RegistrationFailure.INVALID_INPUT
    is io.github.jan.supabase.exceptions.RestException -> when(e.statusCode){401,403->RegistrationFailure.SESSION;409->RegistrationFailure.CONFLICT;in 500..599->RegistrationFailure.SERVICE;else->RegistrationFailure.INVALID_INPUT}
    else->RegistrationFailure.UNKNOWN
   }
   mutable.update { it.copy(error=failure) }
  }finally{mutable.update { it.copy(busy=false) }} }
 }
 fun refresh()=run { val next=repository.current(mode);mutable.update { it.copy(case=next) };operation=null }
 fun save(data:JsonObject)=run {
  val id=operation?:UUID.randomUUID().toString().also { operation=it }
  val next=repository.save(requireNotNull(mutable.value.case),data,id)
  mutable.update { it.copy(case=next) };operation=null
 }
 fun submit()=run {
  val id=operation?:UUID.randomUUID().toString().also {operation=it}
  val next=repository.submit(requireNotNull(mutable.value.case),id)
  mutable.update { it.copy(case=next) };operation=null
 }
 fun correct()=run {
  val id=operation?:UUID.randomUUID().toString().also {operation=it}
  val next=repository.correct(requireNotNull(mutable.value.case),id)
  mutable.update {it.copy(case=next)};operation=null
 }
 fun captured(file:File,kind:DocumentKind,vehicle:String?)=run {
  val bytes=files.normalized(file)
  capture=Triple(bytes,kind,vehicle)
  mutable.update {it.copy(preview=bytes,capturePending=true)}
 }
 fun confirmCapture()=run {
  val (bytes,kind,vehicle)=requireNotNull(capture)
  val case=requireNotNull(mutable.value.case)
  val reservation=repository.reserve(case,kind,vehicle,bytes.size.toLong(),UUID.randomUUID().toString())
  pending=Triple(reservation,bytes,UUID.randomUUID().toString())
  capture=null;mutable.update {it.copy(preview=null,capturePending=false)}
  finishUpload()
 }
 private suspend fun finishUpload() {
  val (reservation,bytes,key)=requireNotNull(pending)
  try {repository.upload(reservation,bytes)} catch(e:io.github.jan.supabase.exceptions.RestException) {
   // A retry after a lost upload response may find the immutable object already present.
   if(e.statusCode!=409 && !e.message.orEmpty().contains("Duplicate",true))throw e
  }
  repository.confirm(requireNotNull(mutable.value.case),reservation,key)
  pending=null
  val next=repository.current(mode)
  mutable.update { it.copy(case=next) }
 }
 fun retryUpload()=run {finishUpload()}
 fun preview(path:String)=run { val bytes=repository.photo(path);mutable.update {it.copy(preview=bytes)} }
 fun closePreview(){capture=null;mutable.update {it.copy(preview=null,capturePending=false)}}
 companion object {
  fun factory(mode:String,r:RegistrationVerificationRepository,f:RegistrationDocumentCaptureFiles)=object:ViewModelProvider.Factory {
   @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T=RegistrationVerificationViewModel(mode,r,f) as T
  }
 }
}
