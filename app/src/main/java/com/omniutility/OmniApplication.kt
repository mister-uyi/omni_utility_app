package com.omniutility

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OmniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
