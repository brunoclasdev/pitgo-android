package com.trustarx.pitgo.features.login.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.login.viewmodel.PasswordRecoveryViewModel

@Composable
fun PasswordRecoveryScreen(viewModel:PasswordRecoveryViewModel,uri:String?,onBack:()->Unit,onDone:()->Unit){
 val s by viewModel.state.collectAsStateWithLifecycle()
 LaunchedEffect(uri){if(uri!=null)viewModel.accept(uri)}
 LaunchedEffect(s.done){if(s.done)onDone()}
 Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text(stringResource(R.string.registration_recovery_title),style=MaterialTheme.typography.headlineSmall)
 if(s.busy)CircularProgressIndicator()
 if(s.error)Text(stringResource(R.string.registration_recovery_error),color=MaterialTheme.colorScheme.error)
 if(s.ready){
 OutlinedTextField(s.password,viewModel::editPassword,label={Text(stringResource(R.string.registration_new_password))},visualTransformation=PasswordVisualTransformation())
 OutlinedTextField(s.confirmation,viewModel::editConfirmation,label={Text(stringResource(R.string.registration_confirmation))},visualTransformation=PasswordVisualTransformation())
 Button(viewModel::save,enabled=!s.busy){Text(stringResource(R.string.registration_recovery_save))}
 }else{
 OutlinedTextField(s.email,viewModel::editEmail,label={Text(stringResource(R.string.registration_email))},singleLine=true)
 Button(viewModel::send,enabled=!s.busy){Text(stringResource(R.string.registration_recovery_send))}
 if(s.sent)Text(stringResource(R.string.registration_recovery_sent))
 }
 TextButton(onBack){Text(stringResource(R.string.registration_back))}
 }
}
