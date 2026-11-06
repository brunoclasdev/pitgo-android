package com.trustarx.pitgo.features.login.viewmodel

import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.trustarx.pitgo.features.login.repository.PasswordRecoveryRepository

data class PasswordRecoveryState(val email:String="",val password:String="",val confirmation:String="",val busy:Boolean=false,val sent:Boolean=false,val ready:Boolean=false,val done:Boolean=false,val error:Boolean=false)
class PasswordRecoveryViewModel:ViewModel(){
 private val repository=PasswordRecoveryRepository();private val mutable=MutableStateFlow(PasswordRecoveryState());val state=mutable.asStateFlow()
 fun editEmail(s:String){mutable.update{it.copy(email=s)}}
 fun editPassword(s:String){mutable.update{it.copy(password=s)}}
 fun editConfirmation(s:String){mutable.update{it.copy(confirmation=s)}}
 private fun run(block:suspend()->Unit){if(mutable.value.busy)return;mutable.update{it.copy(busy=true,error=false)};viewModelScope.launch{try{block()}catch(c:CancellationException){throw c}catch(_:Exception){mutable.update{it.copy(error=true)}}finally{mutable.update{it.copy(busy=false)}}}}
 fun accept(uri:String)=run{repository.accept(uri);mutable.update{it.copy(ready=true)}}
 fun send()=run{require(com.trustarx.pitgo.core.common.validation.BrazilianInputValidation.isValidEmail(mutable.value.email.trim()));repository.send(mutable.value.email.trim());mutable.update{it.copy(sent=true)}}
 override fun onCleared(){
  // The isolated Auth client owns resources; close them after its UI scope is cancelled.
  val cleanup=CoroutineScope(SupervisorJob()+Dispatchers.IO)
  cleanup.launch{try{repository.clear();repository.close()}finally{cleanup.cancel()}}
 }
 fun save()=run{val s=mutable.value;require(s.password.length>=8&&s.password==s.confirmation);repository.update(s.password);mutable.update{it.copy(done=true,password="",confirmation="")}}
}
