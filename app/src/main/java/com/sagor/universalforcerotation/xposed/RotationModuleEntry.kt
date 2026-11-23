package com.sagor.universalforcerotation.xposed

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import com.sagor.universalforcerotation.core.ConfigSerializer
import com.sagor.universalforcerotation.core.HookLogger
import com.sagor.universalforcerotation.core.SafetyGuard
import com.sagor.universalforcerotation.core.XposedHookLogger
import com.sagor.universalforcerotation.model.RotationConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modern libxposed API 102 entry point declared in META-INF/xposed/java_init.list.
 */
class RotationModuleEntry : XposedModule() {

    companion object {
        private const val TAG = "UniversalForceRotation"
        private const val STATE_PACKAGE_NAME = "ufr.pkg"
        private const val STATE_PROCESS_NAME = "ufr.proc"
        private const val STATE_IS_SYSTEM_APP = "ufr.sys"
    }

    private val logger: HookLogger by lazy {
        XposedHookLogger(TAG) { priority, tag, msg, tr ->
            if (tr != null) log(priority, tag, msg, tr) else log(priority, tag, msg)
        }
    }

    private val hooksInitialized = AtomicBoolean(false)

    @Volatile
    private var targetPackageName: String = ""

    @Volatile
    private var targetProcessName: String = ""

    @Volatile
    private var targetIsSystemApp: Boolean = false

    private var remotePrefs: SharedPreferences? = null
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var coordinator: OrientationHookCoordinator? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        targetProcessName = param.processName
        if (SafetyGuard.isProtectedProcess(param.processName, param.isSystemServer)) {
            logger.info("Skipping protected process: ${param.processName} (systemServer=${param.isSystemServer})")
            detachIfSupported()
            return
        }

        logger.info(
            "Loaded in ${param.processName} | framework=$frameworkName $frameworkVersion($frameworkVersionCode) | api=$apiVersion | android=${Build.VERSION.RELEASE}(SDK ${Build.VERSION.SDK_INT})"
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val pkg = param.packageName
        if (SafetyGuard.isProtectedPackage(pkg)) {
            logger.info("Skipping protected package: $pkg")
            detachIfSupported()
            return
        }

        // Vector dispatches onPackageReady again for split APKs / dynamic feature modules (e.g., TikTok sub-features).
        // Initialize process-wide framework hooks once so split loads never duplicate listeners or reset state.
        if (!hooksInitialized.compareAndSet(false, true)) {
            logger.debug("Ignoring subsequent onPackageReady for $pkg (isFirst=${param.isFirstPackage}); hooks already active")
            return
        }

        targetPackageName = pkg
        targetIsSystemApp = SafetyGuard.isSystemApplicationFlags(param.applicationInfo.flags)

        val hookCoordinator = OrientationHookCoordinator(
            xposed = this,
            packageName = pkg,
            isSystemApp = targetIsSystemApp,
            logger = logger,
        )
        coordinator = hookCoordinator

        bindRemotePreferences(hookCoordinator)
        hookCoordinator.installOrReplaceHooks()
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        logger.info("Hot reloading $targetPackageName in $targetProcessName")
        unbindRemotePreferences()
        coordinator?.clearTransientState()

        val savedState = Bundle().apply {
            putString(STATE_PACKAGE_NAME, targetPackageName)
            putString(STATE_PROCESS_NAME, targetProcessName)
            putBoolean(STATE_IS_SYSTEM_APP, targetIsSystemApp)
        }
        param.setSavedInstanceState(savedState)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val state = param.savedInstanceState as? Bundle
        targetProcessName = state?.getString(STATE_PROCESS_NAME) ?: param.processName
        targetPackageName = state?.getString(STATE_PACKAGE_NAME) ?: param.processName
        targetIsSystemApp = state?.getBoolean(STATE_IS_SYSTEM_APP) ?: false
        hooksInitialized.set(true)

        logger.info(
            "Hot reloaded $targetPackageName ($targetProcessName) with ${param.oldHookHandles.size} previous hook handles"
        )

        val hookCoordinator = OrientationHookCoordinator(
            xposed = this,
            packageName = targetPackageName,
            isSystemApp = targetIsSystemApp,
            logger = logger,
        )
        coordinator = hookCoordinator

        bindRemotePreferences(hookCoordinator)
        hookCoordinator.installOrReplaceHooks(param.oldHookHandles)
    }

    private fun bindRemotePreferences(hookCoordinator: OrientationHookCoordinator) {
        val hasRemote = (frameworkProperties and XposedInterface.PROP_CAP_REMOTE) != 0L
        if (!hasRemote) {
            logger.warn("Framework does not report PROP_CAP_REMOTE; using default configuration")
            hookCoordinator.updateConfig(RotationConfig())
            return
        }

        runCatching {
            val prefs = getRemotePreferences(ConfigSerializer.PREFS_GROUP)
            remotePrefs = prefs
            hookCoordinator.updateConfig(ConfigSerializer.readFrom(prefs))

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { updatedPrefs, _ ->
                runCatching {
                    hookCoordinator.updateConfig(ConfigSerializer.readFrom(updatedPrefs))
                }.onFailure {
                    logger.error("Failed applying live RemotePreferences update", it)
                }
            }
            prefListener = listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { err ->
            logger.error("Failed binding RemotePreferences; falling back to defaults", err)
            hookCoordinator.updateConfig(RotationConfig())
        }
    }

    private fun unbindRemotePreferences() {
        val prefs = remotePrefs
        val listener = prefListener
        if (prefs != null && listener != null) {
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        prefListener = null
        remotePrefs = null
    }

    private fun detachIfSupported() {
        if (apiVersion >= XposedInterface.API_102) {
            detach()
        }
    }
}
