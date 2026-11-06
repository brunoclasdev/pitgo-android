package com.trustarx.pitgo.features.login.repository

import android.net.Uri
import com.trustarx.pitgo.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.FlowType

// Separate, memory-only Auth client: a recovery session never becomes a marketplace session.
class PasswordRecoveryRepository {
 private val client=createSupabaseClient(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_KEY){install(Auth){autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false;flowType=FlowType.IMPLICIT}}
 private var recoveryReady=false
 suspend fun send(email:String){client.auth.resetPasswordForEmail(email,redirectUrl="${BuildConfig.APPLICATION_ID}://password-recovery")}
 suspend fun accept(value:String){
  recoveryReady=false
  val uri=Uri.parse(value)
  require(uri.scheme==BuildConfig.APPLICATION_ID&&uri.host=="password-recovery")
  val fragment=Uri.parse("https://recovery.invalid/?"+uri.fragment.orEmpty())
  require(fragment.getQueryParameter("type")=="recovery")
  val access=requireNotNull(fragment.getQueryParameter("access_token"));val refresh=requireNotNull(fragment.getQueryParameter("refresh_token"))
  client.auth.importAuthToken(access,refresh)
  client.auth.retrieveUserForCurrentSession()
  recoveryReady=true
 }
 suspend fun update(password:String){check(recoveryReady);require(password.length>=8)
  client.auth.updateUser{this.password=password}
  recoveryReady=false;client.auth.clearSession()
 }
 suspend fun close(){client.close()}
 suspend fun clear(){recoveryReady=false;client.auth.clearSession()}
}
