package com.trustarx.pitgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.trustarx.pitgo.core.design.theme.PitGoTheme
import com.trustarx.pitgo.core.navigation.PitGoNavHost
import com.trustarx.pitgo.core.preferences.AppearanceMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                PitGoNavHost(container = container)
            }
        }
    }
}
