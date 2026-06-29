package com.omniutility

import android.content.Context
import com.omniutility.feature.softpower.data.SoftPowerSettingsRepository

class AppContainer(private val context: Context) {
    val softPowerSettingsRepository by lazy {
        SoftPowerSettingsRepository(context)
    }
}
