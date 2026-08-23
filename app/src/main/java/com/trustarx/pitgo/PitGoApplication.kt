package com.trustarx.pitgo

import android.app.Application
import com.trustarx.pitgo.core.app.PitGoAppContainer
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class PitGoApplication : Application() {
    lateinit var container: PitGoAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(applicationContext)
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        container = PitGoAppContainer(this)
    }
}
