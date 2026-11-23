package com.sagor.universalforcerotation.core

import android.content.SharedPreferences
import com.sagor.universalforcerotation.model.AppOverride
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig

/**
 * Serializes [RotationConfig] to and from flat primitive SharedPreferences entries so updates
 * sync reliably over Vector's diff-based RemotePreferences IPC.
 */
object ConfigSerializer {

    const val PREFS_GROUP = "ufr_config"

    const val KEY_INITIALIZED = "initialized"
    const val KEY_MASTER_ENABLED = "master_enabled"
    const val KEY_GLOBAL_FORCE_ENABLED = "global_force_enabled"
    const val KEY_GLOBAL_MODE = "global_mode"
    const val KEY_EXCLUDE_SYSTEM_DEFAULT = "exclude_system_default"
    const val KEY_VERBOSE_LOGGING = "verbose_logging"
    const val KEY_REVISION = "revision"

    const val PREFIX_APP_ENABLED = "app_enabled_"
    const val PREFIX_APP_MODE = "app_mode_"
    const val MODE_INHERIT = "inherit"

    fun readFrom(prefs: SharedPreferences): RotationConfig {
        val all = prefs.all
        val masterEnabled = (all[KEY_MASTER_ENABLED] as? Boolean) ?: true
        val globalForceEnabled = (all[KEY_GLOBAL_FORCE_ENABLED] as? Boolean) ?: true
        val globalMode = OrientationMode.fromId(all[KEY_GLOBAL_MODE] as? String)
            ?: OrientationMode.FOLLOW_AUTO_ROTATE
        val excludeSystemDefault = (all[KEY_EXCLUDE_SYSTEM_DEFAULT] as? Boolean) ?: true
        val verboseLogging = (all[KEY_VERBOSE_LOGGING] as? Boolean) ?: false
        val revision = (all[KEY_REVISION] as? Number)?.toLong() ?: 0L

        val packages = linkedSetOf<String>()
        for (key in all.keys) {
            when {
                key.startsWith(PREFIX_APP_ENABLED) ->
                    packages.add(key.removePrefix(PREFIX_APP_ENABLED))
                key.startsWith(PREFIX_APP_MODE) ->
                    packages.add(key.removePrefix(PREFIX_APP_MODE))
            }
        }

        val overrides = linkedMapOf<String, AppOverride>()
        for (pkg in packages) {
            if (pkg.isBlank()) continue
            val enabled = (all["$PREFIX_APP_ENABLED$pkg"] as? Boolean) ?: true
            val rawMode = all["$PREFIX_APP_MODE$pkg"] as? String
            val mode = if (rawMode == null || rawMode == MODE_INHERIT) {
                null
            } else {
                OrientationMode.fromId(rawMode)
            }
            overrides[pkg] = AppOverride(enabled = enabled, mode = mode)
        }

        return RotationConfig(
            masterEnabled = masterEnabled,
            globalForceEnabled = globalForceEnabled,
            globalMode = globalMode,
            excludeSystemAppsByDefault = excludeSystemDefault,
            verboseLogging = verboseLogging,
            appOverrides = overrides,
            revision = revision,
        )
    }

    fun writeFull(editor: SharedPreferences.Editor, config: RotationConfig) {
        editor.clear()
        editor.putBoolean(KEY_INITIALIZED, true)
        editor.putBoolean(KEY_MASTER_ENABLED, config.masterEnabled)
        editor.putBoolean(KEY_GLOBAL_FORCE_ENABLED, config.globalForceEnabled)
        editor.putString(KEY_GLOBAL_MODE, config.globalMode.id)
        editor.putBoolean(KEY_EXCLUDE_SYSTEM_DEFAULT, config.excludeSystemAppsByDefault)
        editor.putBoolean(KEY_VERBOSE_LOGGING, config.verboseLogging)
        editor.putLong(KEY_REVISION, config.revision)

        for ((pkg, override) in config.appOverrides) {
            editor.putBoolean("$PREFIX_APP_ENABLED$pkg", override.enabled)
            editor.putString("$PREFIX_APP_MODE$pkg", override.mode?.id ?: MODE_INHERIT)
        }
    }

    fun writeGlobals(editor: SharedPreferences.Editor, config: RotationConfig) {
        editor.putBoolean(KEY_INITIALIZED, true)
        editor.putBoolean(KEY_MASTER_ENABLED, config.masterEnabled)
        editor.putBoolean(KEY_GLOBAL_FORCE_ENABLED, config.globalForceEnabled)
        editor.putString(KEY_GLOBAL_MODE, config.globalMode.id)
        editor.putBoolean(KEY_EXCLUDE_SYSTEM_DEFAULT, config.excludeSystemAppsByDefault)
        editor.putBoolean(KEY_VERBOSE_LOGGING, config.verboseLogging)
        editor.putLong(KEY_REVISION, config.revision)
    }

    fun writeAppOverride(
        editor: SharedPreferences.Editor,
        packageName: String,
        override: AppOverride?,
        revision: Long,
    ) {
        editor.putBoolean(KEY_INITIALIZED, true)
        editor.putLong(KEY_REVISION, revision)
        if (override == null) {
            editor.remove("$PREFIX_APP_ENABLED$packageName")
            editor.remove("$PREFIX_APP_MODE$packageName")
        } else {
            editor.putBoolean("$PREFIX_APP_ENABLED$packageName", override.enabled)
            editor.putString("$PREFIX_APP_MODE$packageName", override.mode?.id ?: MODE_INHERIT)
        }
    }
}
