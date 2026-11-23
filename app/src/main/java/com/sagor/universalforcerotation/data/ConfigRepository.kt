package com.sagor.universalforcerotation.data

import android.content.Context
import android.content.SharedPreferences
import com.sagor.universalforcerotation.core.ConfigSerializer
import com.sagor.universalforcerotation.model.AppOverride
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for module configuration. Mirrors changes between local storage
 * (for offline editing / persistence) and Vector's RemotePreferences (for hooked processes).
 */
class ConfigRepository(context: Context) {

    companion object {
        private const val LOCAL_PREFS_NAME = "ufr_local_config"
    }

    private val localPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    private val _config = MutableStateFlow(loadInitialLocal())
    val config: StateFlow<RotationConfig> = _config.asStateFlow()

    private fun loadInitialLocal(): RotationConfig {
        if (!localPrefs.getBoolean(ConfigSerializer.KEY_INITIALIZED, false)) {
            val defaults = RotationConfig()
            localPrefs.edit().also { ConfigSerializer.writeFull(it, defaults) }.apply()
            return defaults
        }
        return ConfigSerializer.readFrom(localPrefs)
    }

    @Synchronized
    fun attachRemotePreferences(prefs: SharedPreferences) {
        remotePrefs = prefs
        val remoteInitialized = runCatching {
            prefs.getBoolean(ConfigSerializer.KEY_INITIALIZED, false)
        }.getOrDefault(false)

        if (remoteInitialized) {
            val remoteConfig = ConfigSerializer.readFrom(prefs)
            localPrefs.edit().also { ConfigSerializer.writeFull(it, remoteConfig) }.apply()
            _config.value = remoteConfig
        } else {
            val current = _config.value
            runCatching {
                prefs.edit().also { ConfigSerializer.writeFull(it, current) }.apply()
            }
        }
    }

    @Synchronized
    fun detachRemotePreferences() {
        remotePrefs = null
    }

    @Synchronized
    fun setMasterEnabled(enabled: Boolean) {
        updateGlobals(_config.value.copy(masterEnabled = enabled, revision = _config.value.revision + 1))
    }

    @Synchronized
    fun setGlobalForceEnabled(enabled: Boolean) {
        updateGlobals(_config.value.copy(globalForceEnabled = enabled, revision = _config.value.revision + 1))
    }

    @Synchronized
    fun setGlobalMode(mode: OrientationMode) {
        updateGlobals(_config.value.copy(globalMode = mode, revision = _config.value.revision + 1))
    }

    @Synchronized
    fun setExcludeSystemAppsByDefault(exclude: Boolean) {
        updateGlobals(_config.value.copy(excludeSystemAppsByDefault = exclude, revision = _config.value.revision + 1))
    }

    @Synchronized
    fun setVerboseLogging(verbose: Boolean) {
        updateGlobals(_config.value.copy(verboseLogging = verbose, revision = _config.value.revision + 1))
    }

    @Synchronized
    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val current = _config.value
        val existing = current.appOverrides[packageName]
        val nextOverride = AppOverride(enabled = enabled, mode = existing?.mode)
        updateSingleAppOverride(packageName, nextOverride)
    }

    @Synchronized
    fun setAppMode(packageName: String, mode: OrientationMode?) {
        val current = _config.value
        val existing = current.appOverrides[packageName]
        val nextOverride = AppOverride(enabled = existing?.enabled ?: true, mode = mode)
        updateSingleAppOverride(packageName, nextOverride)
    }

    @Synchronized
    fun setAppOverride(packageName: String, override: AppOverride?) {
        updateSingleAppOverride(packageName, override)
    }

    @Synchronized
    fun resetAllAppOverrides() {
        val next = _config.value.copy(
            appOverrides = emptyMap(),
            revision = _config.value.revision + 1,
        )
        localPrefs.edit().also { ConfigSerializer.writeFull(it, next) }.apply()
        runCatching {
            remotePrefs?.edit()?.also { ConfigSerializer.writeFull(it, next) }?.apply()
        }
        _config.value = next
    }

    private fun updateGlobals(next: RotationConfig) {
        localPrefs.edit().also { ConfigSerializer.writeGlobals(it, next) }.apply()
        runCatching {
            remotePrefs?.edit()?.also { ConfigSerializer.writeGlobals(it, next) }?.apply()
        }
        _config.value = next
    }

    private fun updateSingleAppOverride(packageName: String, override: AppOverride?) {
        val current = _config.value
        val nextMap = current.appOverrides.toMutableMap()
        if (override == null) {
            nextMap.remove(packageName)
        } else {
            nextMap[packageName] = override
        }
        val nextRevision = current.revision + 1
        val next = current.copy(appOverrides = nextMap, revision = nextRevision)

        localPrefs.edit().also {
            ConfigSerializer.writeAppOverride(it, packageName, override, nextRevision)
        }.apply()
        runCatching {
            remotePrefs?.edit()?.also {
                ConfigSerializer.writeAppOverride(it, packageName, override, nextRevision)
            }?.apply()
        }
        _config.value = next
    }
}
