package com.trustarx.pitgo.features.registrationverification.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.registrationverification.model.RegistrationStatus

@Composable
fun RegistrationStatusScreen(status:RegistrationStatus?,reason:String?,items:List<String>) {
 Text(stringResource(when(status){
  RegistrationStatus.IN_REVIEW->R.string.registration_in_review
  RegistrationStatus.CORRECTION_REQUESTED->R.string.registration_correction_requested
  RegistrationStatus.APPROVED->R.string.registration_approved
  else->R.string.registration_incomplete
 }),style=MaterialTheme.typography.titleMedium)
 if(!reason.isNullOrBlank())Text(reason)
 items.forEach {Text("• $it")}
}
