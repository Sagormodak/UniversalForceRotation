package com.sagor.universalforcerotation.model

/**
 * Per-package override stored in preferences.
 *
 * @property enabled Whether force rotation is enabled for this package.
 * @property mode Custom orientation mode, or null to inherit [RotationConfig.globalMode].
 */
data class AppOverride(
    val enabled: Boolean,
    val mode: OrientationMode? = null,
)

/**
 * Immutable module configuration shared between the UI app and hooked target processes via
 * Vector's RemotePreferences.
 */
data class RotationConfig(
    val masterEnabled: Boolean = true,
    val globalForceEnabled: Boolean = true,
    val globalMode: OrientationMode = OrientationMode.FOLLOW_AUTO_ROTATE,
    val excludeSystemAppsByDefault: Boolean = true,
    val verboseLogging: Boolean = false,
    val appOverrides: Map<String, AppOverride> = emptyMap(),
    val revision: Long = 0L,
)
