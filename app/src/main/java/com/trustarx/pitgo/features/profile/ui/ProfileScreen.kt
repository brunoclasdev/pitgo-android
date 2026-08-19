package com.trustarx.pitgo.features.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.AccountSnapshot
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.ui.PitGoBottomNavigationItems

@Composable
fun ProfileScreen(
    account: AccountSnapshot,
    onBottomItemClick: (BottomItemId) -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        bottomBar = {
            PitGoBottomNavigation(
                items = PitGoBottomNavigationItems,
                selectedItem = BottomItemId.PROFILE,
                onItemClick = onBottomItemClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(PitGoSpacing.Large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(account.profile.displayName, style = MaterialTheme.typography.titleLarge)
            Text(account.authEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = PitGoSize.MinimumTouchTarget,
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_profile_logout),
                        contentDescription = null,
                        modifier = Modifier.size(PitGoSize.FeatureIcon),
                    )
                    Text(stringResource(R.string.action_logout))
                }
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_message)) },
            confirmButton = {
                TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
