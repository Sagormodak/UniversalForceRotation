package com.sagor.universalforcerotation

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.sagor.universalforcerotation.data.ConfigRepository
import com.sagor.universalforcerotation.data.InstalledAppsRepository
import com.sagor.universalforcerotation.data.XposedServiceManager
import io.github.libxposed.service.XposedServiceHelper

class UniversalForceRotationApp : Application() {

    lateinit var serviceManager: XposedServiceManager
        private set

    lateinit var configRepository: ConfigRepository
        private set

    lateinit var appsRepository: InstalledAppsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)

        configRepository = ConfigRepository(this)
        serviceManager = XposedServiceManager(configRepository)
        appsRepository = InstalledAppsRepository(this)

        XposedServiceHelper.registerListener(serviceManager)
    }
}
