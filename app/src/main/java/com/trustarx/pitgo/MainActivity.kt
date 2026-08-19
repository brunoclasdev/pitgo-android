package com.trustarx.pitgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trustarx.pitgo.core.design.theme.PitGoTheme
import com.trustarx.pitgo.core.navigation.PitGoNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PitGoTheme {
                PitGoNavHost(
                    container = (application as PitGoApplication).container,
                )
            }
        }
    }
}
