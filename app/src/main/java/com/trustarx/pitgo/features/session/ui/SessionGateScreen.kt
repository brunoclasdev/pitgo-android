package com.trustarx.pitgo.features.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing

@Composable
fun SessionGateScreen(
    state: AppSessionState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(PitGoSpacing.Large),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                AppSessionState.Initializing,
                AppSessionState.Verifying,
                -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(PitGoSize.MinimumTouchTarget))
                    Text(stringResource(R.string.session_verifying))
                }
                AppSessionState.OfflineVerification -> GateMessage(
                    title = stringResource(R.string.session_offline_title),
                    message = stringResource(R.string.session_offline_message),
                    onRetry = onRetry,
                )
                is AppSessionState.ProfileBlocked -> GateMessage(
                    title = stringResource(R.string.session_profile_blocked_title),
                    message = stringResource(R.string.session_profile_blocked_message),
                    onRetry = onRetry,
                    onLogout = onLogout,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun GateMessage(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onLogout: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = PitGoSpacing.ExtraSmall,
    ) {
        Column(
            modifier = Modifier.padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = PitGoSize.MinimumTouchTarget,
                ),
            ) {
                Text(stringResource(R.string.action_retry))
            }
            onLogout?.let { logout ->
                OutlinedButton(
                    onClick = logout,
                    modifier = Modifier.fillMaxWidth().heightIn(
                        min = PitGoSize.MinimumTouchTarget,
                    ),
                ) {
                    Text(stringResource(R.string.action_logout))
                }
            }
        }
    }
}
