package com.trustarx.pitgo

import android.os.Bundle
import android.content.Intent
import com.trustarx.pitgo.features.providertracking.notification.GuinchoNotificationOpen
import com.trustarx.pitgo.features.providertracking.notification.ProviderIncomingRequestNotifier
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import com.trustarx.pitgo.R
import kotlinx.coroutines.delay
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.trustarx.pitgo.core.design.theme.PitGoTheme
import com.trustarx.pitgo.core.navigation.PitGoNavHost
import com.trustarx.pitgo.core.preferences.AppearanceMode

class MainActivity : ComponentActivity() {
    private var recoveryOpen by mutableStateOf<String?>(null)
    private var incomingOpen by mutableStateOf<GuinchoNotificationOpen?>(null)
    private fun readIncoming(intent: Intent?) {
        val link = intent?.data
        if (link?.scheme == BuildConfig.APPLICATION_ID && link.host == "password-recovery") {
            recoveryOpen = link.toString()
        }
        val id = intent?.getStringExtra(ProviderIncomingRequestNotifier.EXTRA_APPOINTMENT) ?: return
        val provider = intent.getStringExtra(ProviderIncomingRequestNotifier.EXTRA_PROVIDER) ?: return
        incomingOpen = GuinchoNotificationOpen(id, provider, SystemClock.elapsedRealtimeNanos())
        intent.removeExtra(ProviderIncomingRequestNotifier.EXTRA_APPOINTMENT)
        intent.removeExtra(ProviderIncomingRequestNotifier.EXTRA_PROVIDER)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIncoming(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val startedAt = SystemClock.elapsedRealtime()
        var startupReady = false
        splashScreen.setKeepOnScreenCondition {
            !startupReady || SystemClock.elapsedRealtime() - startedAt < 500L
        }
        super.onCreate(savedInstanceState)
        readIncoming(intent)
        enableEdgeToEdge()
        setContent {
            val container = (application as PitGoApplication).container
            val appearanceMode by container.appearancePreferences.mode
                .collectAsState(initial = AppearanceMode.SYSTEM)
            val darkTheme = when (appearanceMode) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
            }
            PitGoTheme(darkTheme = darkTheme) {
                var showBrandSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1_200L)
                    showBrandSplash = false
                }
                Box(Modifier.fillMaxSize()) {
                    PitGoNavHost(container = container, incomingOpen = incomingOpen, onIncomingConsumed = { incomingOpen = null }, recoveryUri = recoveryOpen, onRecoveryConsumed = { recoveryOpen = null; intent?.data = null })
                    if (showBrandSplash) {
                        Image(
                            painter = painterResource(R.drawable.pitgo_image1200),
                            contentDescription = "PitGo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF6D28D9))
                                .zIndex(1f),
                        )
                    }
                }
            }
            startupReady = true
        }
    }
}
