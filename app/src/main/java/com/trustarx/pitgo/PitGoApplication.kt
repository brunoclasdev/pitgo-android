package com.trustarx.pitgo

import android.app.Application
import com.trustarx.pitgo.core.app.PitGoAppContainer

class PitGoApplication : Application() {
    lateinit var container: PitGoAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PitGoAppContainer(this)
    }
}
