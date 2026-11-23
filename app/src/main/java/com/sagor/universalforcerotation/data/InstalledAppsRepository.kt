package com.sagor.universalforcerotation.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.sagor.universalforcerotation.core.OrientationPolicyEngine
import com.sagor.universalforcerotation.core.SafetyGuard
import com.sagor.universalforcerotation.model.AppEntry
import com.sagor.universalforcerotation.model.RotationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Loads installed applications asynchronously and merges them with current policy & Vector scope.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    data class RawAppInfo(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
        val isProtected: Boolean,
        val hasLauncherActivity: Boolean,
    )

    private val _rawApps = MutableStateFlow<List<RawAppInfo>>(emptyList())
    val rawApps: StateFlow<List<RawAppInfo>> = _rawApps.asStateFlow()

    suspend fun refreshInstalledApps(): List<RawAppInfo> {
        val loaded = queryInstalledApps()
        _rawApps.value = loaded
        return loaded
    }

    suspend fun queryInstalledApps(): List<RawAppInfo> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPackages = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toHashSet()

        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
        apps.map { info ->
            val pkg = info.packageName
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
            val isSystem = SafetyGuard.isSystemApplicationFlags(info.flags)
            val isProtected = SafetyGuard.isProtectedPackage(pkg)
            RawAppInfo(
                packageName = pkg,
                label = label.ifBlank { pkg },
                isSystemApp = isSystem,
                isProtected = isProtected,
                hasLauncherActivity = pkg in launcherPackages,
            )
        }.sortedWith(
            compareBy<RawAppInfo> { it.isSystemApp }
                .thenBy { !it.hasLauncherActivity }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName }
        )
    }

    fun combineWithPolicy(
        rawApps: List<RawAppInfo>,
        config: RotationConfig,
        scopeSet: Set<String>,
    ): List<AppEntry> {
        return rawApps.map { raw ->
            val decision = OrientationPolicyEngine.evaluate(
                packageName = raw.packageName,
                isSystemApp = raw.isSystemApp,
                config = config,
            )
            AppEntry(
                packageName = raw.packageName,
                label = raw.label,
                isSystemApp = raw.isSystemApp,
                isProtected = raw.isProtected,
                hasLauncherActivity = raw.hasLauncherActivity,
                inVectorScope = raw.packageName in scopeSet,
                override = config.appOverrides[raw.packageName],
                effectiveDecision = decision,
            )
        }
    }
}
