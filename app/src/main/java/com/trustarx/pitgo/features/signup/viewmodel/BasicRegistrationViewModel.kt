package com.trustarx.pitgo.features.signup.viewmodel

import androidx.lifecycle.*
import com.trustarx.pitgo.core.common.validation.InternationalPhone
import com.trustarx.pitgo.features.registrationverification.repository.RegistrationVerificationRepository
import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation as Validation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class BasicRegistrationState(val country:String="BR",val fields:List<String> = List(7){""},val busy:Boolean=false,val error:Boolean=false,val success:Boolean=false,val failure:String?=null)
class BasicRegistrationViewModel(private val mode:String,private val repository:RegistrationVerificationRepository):ViewModel() {
 val isProvider: Boolean get() = mode == "PROVIDER"
 private val mutable=MutableStateFlow(BasicRegistrationState());val state=mutable.asStateFlow()
 fun selectCountry(region:String){if(!mutable.value.busy && region in InternationalPhone.regions)mutable.update{it.copy(country=region,error=false,failure=null)}}
 fun edit(index:Int,value:String){if(!mutable.value.busy)mutable.update {it.copy(fields=it.fields.toMutableList().apply{set(index,if(index==3)value.filter { it in '0'..'9' }.take(15) else value)},error=false,failure=null)}}
 fun submit(){val s=mutable.value;if(s.busy)return
  val (first,last,email,phone,rawDocument)=s.fields
  val normalizedPhone=InternationalPhone.toE164(phone,s.country)
  val document=rawDocument.uppercase(java.util.Locale.ROOT).filter { it in '0'..'9' || it in 'A'..'Z' }
  val validCnpj=document.length==14 && Validation.isValidCnpj(document)
  val validCpf=document.length==11 && document.all { it in '0'..'9' } && Validation.isValidCpf(document)
  val validDocument=if(isProvider) validCnpj else validCpf||validCnpj
  if(first.isBlank()||last.isBlank()||!Validation.isValidEmail(email.trim())||normalizedPhone==null||!validDocument||s.fields[5].length<8||s.fields[5]!=s.fields[6]){mutable.update{it.copy(error=true,failure=null)};return}
  mutable.update{it.copy(busy=true,error=false)}
  viewModelScope.launch{try{
   repository.basic(mode,first.trim(),last.trim(),email.trim().lowercase(),requireNotNull(normalizedPhone),document,s.fields[5])
   mutable.update{it.copy(success=true,fields=List(7){""})}
  }catch(c:CancellationException){throw c}catch(e:Exception){
   val reason=when(e){
    is io.github.jan.supabase.exceptions.HttpRequestException -> "OFFLINE"
    is io.ktor.client.plugins.HttpRequestTimeoutException -> "TIMEOUT"
    is io.github.jan.supabase.auth.exception.AuthRestException -> when(e.errorCode){
     io.github.jan.supabase.auth.exception.AuthErrorCode.EmailExists,io.github.jan.supabase.auth.exception.AuthErrorCode.UserAlreadyExists -> "EXISTS"
     io.github.jan.supabase.auth.exception.AuthErrorCode.OverRequestRateLimit,io.github.jan.supabase.auth.exception.AuthErrorCode.OverEmailSendRateLimit -> "RATE"
     else -> "SERVER"
    }
    else -> "SERVER"
   }
   mutable.update{it.copy(error=true,failure=reason)}
  }finally{mutable.update{it.copy(busy=false)}}}
 }
 companion object{fun factory(mode:String,r:RegistrationVerificationRepository)=object:ViewModelProvider.Factory{
 @Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=BasicRegistrationViewModel(mode,r)as T}}
}
