package com.trustarx.pitgo.features.providertracking.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState
import com.trustarx.pitgo.features.providertracking.viewmodel.ProviderTrackingViewModel

@Composable
fun ProviderTrackingRoute(viewModel: ProviderTrackingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.start()
        }
    }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(stringResource(R.string.tracking_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                when (state) {
                    ProviderTrackingState.Stopped -> stringResource(R.string.tracking_stopped)
                    ProviderTrackingState.Starting -> stringResource(R.string.tracking_starting)
                    is ProviderTrackingState.Publishing -> stringResource(R.string.tracking_live)
                    is ProviderTrackingState.Degraded -> stringResource(R.string.tracking_degraded)
                    ProviderTrackingState.Stopping -> stringResource(R.string.tracking_stopping)
                },
            )
            if (state == ProviderTrackingState.Stopped) Button(
                onClick = {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }.toTypedArray()
                    launcher.launch(permissions)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tracking_start)) }
            else Button(viewModel::stop, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tracking_stop))
            }
        }
    }
}
