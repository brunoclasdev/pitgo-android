package com.trustarx.pitgo.features.signup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.ui.InternationalPhoneField
import com.trustarx.pitgo.features.signup.viewmodel.BasicRegistrationViewModel

@Composable
fun BasicRegistrationScreen(viewModel:BasicRegistrationViewModel,onBack:()->Unit,onCreated:()->Unit){
 val state by viewModel.state.collectAsStateWithLifecycle()
 LaunchedEffect(state.success){if(state.success)onCreated()}
 Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text(stringResource(R.string.registration_basic_title),style=MaterialTheme.typography.headlineMedium)
 val labels=listOf(R.string.registration_first_name,R.string.registration_last_name,R.string.registration_email,R.string.registration_phone,if(viewModel.isProvider) R.string.signup_cnpj else R.string.registration_document,R.string.registration_password,R.string.registration_confirmation)
 labels.forEachIndexed{index,label->if(index==3) InternationalPhoneField(state.fields[3],state.country,{viewModel.edit(3,it)},viewModel::selectCountry,!state.busy) else OutlinedTextField(state.fields[index],{viewModel.edit(index,it)},label={Text(stringResource(label))},singleLine=true,enabled=!state.busy,
 visualTransformation=if(index>=5)PasswordVisualTransformation()else VisualTransformation.None,
 keyboardOptions=KeyboardOptions(keyboardType=when(index){2->KeyboardType.Email;3->KeyboardType.Phone;4->KeyboardType.Text;5,6->KeyboardType.Password;else->KeyboardType.Text}),modifier=Modifier.fillMaxWidth())}
 if(state.error)Text(stringResource(when(state.failure){
 "OFFLINE"->R.string.registration_offline;"TIMEOUT"->R.string.registration_timeout
 "EXISTS"->R.string.registration_account_exists;"RATE"->R.string.registration_rate_limit
 "SERVER"->R.string.registration_basic_server;else->R.string.registration_basic_invalid}),color=MaterialTheme.colorScheme.error)
 if(state.busy)CircularProgressIndicator()
 Button(onClick=viewModel::submit,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.registration_basic_submit))}
 TextButton(onClick=onBack,enabled=!state.busy){Text(stringResource(R.string.registration_back))}
 }
}
