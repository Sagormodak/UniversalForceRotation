package com.sagor.universalforcerotation.model

import com.sagor.universalforcerotation.core.OrientationPolicyEngine

/**
 * UI representation of an installed Android application and its current orientation policy.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isProtected: Boolean,
    val hasLauncherActivity: Boolean,
    val inVectorScope: Boolean,
    val override: AppOverride?,
    val effectiveDecision: OrientationPolicyEngine.PolicyDecision,
)
