package com.trustarx.pitgo.features.guincho.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.core.map.ui.*
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.GuinchoBottomNavigationItems
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.viewmodel.GuinchoProviderHomeViewModel
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUiState
import com.trustarx.pitgo.features.providerdashboard.viewmodel.ProviderDashboardViewModel
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState

@Composable
fun GuinchoProviderHomeRoute(home: GuinchoProviderHomeViewModel, dashboard: ProviderDashboardViewModel,
    onHistory: () -> Unit, incomingContent: @Composable () -> Unit = {}, profile: @Composable () -> Unit) {
    val state by home.state.collectAsStateWithLifecycle()
    val account by dashboard.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var profileVisible by remember { mutableStateOf(false) }
    var enableAvailability by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        dashboard.enableTrackedAvailability(ProviderLocationMode.MOBILE)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (!profileVisible) home.start(true)
            if (enableAvailability) {
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else dashboard.enableTrackedAvailability(ProviderLocationMode.MOBILE)
            }
        } else { home.start(false); if (enableAvailability) dashboard.onPrecisePermissionDenied() }
        enableAvailability = false
    }
    Scaffold(
        bottomBar = {
            PitGoBottomNavigation(
                items = GuinchoBottomNavigationItems,
                selectedItem = if (profileVisible) BottomItemId.PROFILE else BottomItemId.MAP,
                onItemClick = { item ->
                    when (item) {
                        BottomItemId.MAP -> profileVisible = false
                        BottomItemId.PROFILE -> profileVisible = true
                        BottomItemId.APPOINTMENTS -> onHistory()
                        else -> Unit
                    }
                }
            )
        }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
        if (profileVisible) {
            Box(Modifier.weight(1f)) { profile() }
            incomingContent()
        }
        else {
            LaunchedEffect(home) {
                if (!hasPrecisePermission(context)) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            LifecycleStartEffect(home) {
                home.start(hasPrecisePermission(context))
                onStopOrDispose { home.stop() }
            }
            val controller = remember { ParticipantMapStyleController() }
            val fix = when (val position = state.position) {
                is GuinchoProviderPosition.Current -> position.fix
                is GuinchoProviderPosition.Stale -> position.fix
                else -> null
            }
            val label = stringResource(R.string.guincho_home_position)
            val markers = fix?.let { listOf(MapMarkerSpec("guincho-self", MapCoordinate(it.latitude, it.longitude),
                if (state.position is GuinchoProviderPosition.Stale) MapMarkerKind.STALE else MapMarkerKind.LIVE,
                accessibilityLabel = label)) }.orEmpty()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                MapLibreMapHost(PitGoManagedMapStyle.configuration, MapRenderModel(markers), state.viewport,
                    state.camera, state.styleRevision, true, controller, home::viewport, {}, home::surface,
                    modifier = Modifier.fillMaxSize(), onUserGesture = home::gesture)
            }
            incomingContent()
            Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.position !is GuinchoProviderPosition.Current) {
                    Text(stringResource(when (state.position) {
                        GuinchoProviderPosition.Loading -> R.string.guincho_home_locating
                        GuinchoProviderPosition.PermissionDenied -> R.string.guincho_home_permission
                        GuinchoProviderPosition.LocationDisabled -> R.string.guincho_home_gps
                        is GuinchoProviderPosition.Stale -> R.string.guincho_home_stale
                        else -> R.string.guincho_home_unavailable
                    }))
                    Row {
                        TextButton(onClick = { home.start(hasPrecisePermission(context)) }) { Text(stringResource(R.string.guincho_refresh)) }
                        TextButton(onClick = { context.startActivity(
                            if (state.position == GuinchoProviderPosition.LocationDisabled) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.guincho_home_settings)) }
                    }
                }
                if (state.surface.style is MapStyleState.Failed || state.surface.resources is MapResourceState.Degraded) {
                    Text(stringResource(R.string.guincho_map_failure))
                    TextButton(onClick = home::retryMap) { Text(stringResource(R.string.guincho_refresh)) }
                }
                val content = account as? ProviderDashboardUiState.Content
                val availabilityLabel = stringResource(R.string.provider_dashboard_available)
                if (content != null) {
                    Text(content.provider.name, Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(when {
                        !content.provider.available -> R.string.provider_dashboard_available_off
                        content.tracking is ProviderTrackingState.Publishing -> R.string.provider_dashboard_available_on
                        else -> R.string.guincho_home_sharing_pending
                    }), Modifier.weight(1f))
                    Switch(modifier = Modifier.semantics { contentDescription = availabilityLabel }, checked = content.provider.available, enabled = !content.updating, onCheckedChange = { available ->
                        if (available) { enableAvailability = true; permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
                        else dashboard.setAvailability(false)
                    })
                    }
                }
                if (content?.availabilityIssue != null) Text(stringResource(R.string.guincho_home_availability_issue), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    }
}

private fun hasPrecisePermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
