package com.sagor.universalforcerotation.core

import android.content.pm.ActivityInfo
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig

/**
 * Pure policy engine resolving whether orientation interception applies to a given package
 * and which Android screenOrientation constant should be enforced.
 */
object OrientationPolicyEngine {

    sealed interface PolicyDecision {
        data class Blocked(val reason: String) : PolicyDecision
        data class Disabled(val reason: String) : PolicyDecision
        data class Apply(val mode: OrientationMode, val source: String) : PolicyDecision
    }

    fun evaluate(
        packageName: String,
        isSystemApp: Boolean,
        config: RotationConfig,
    ): PolicyDecision {
        if (SafetyGuard.isProtectedPackage(packageName)) {
            return PolicyDecision.Blocked("Protected critical system package")
        }
        if (!config.masterEnabled) {
            return PolicyDecision.Disabled("Master switch disabled")
        }

        val appOverride = config.appOverrides[packageName]
        if (appOverride != null) {
            if (!appOverride.enabled) {
                return PolicyDecision.Disabled("Excluded by per-app policy")
            }
            val effectiveMode = appOverride.mode ?: config.globalMode
            val source = if (appOverride.mode != null) "Per-app custom mode" else "Per-app enabled (global mode)"
            return PolicyDecision.Apply(effectiveMode, source)
        }

        if (isSystemApp && config.excludeSystemAppsByDefault) {
            return PolicyDecision.Disabled("System app excluded by default")
        }
        if (!config.globalForceEnabled) {
            return PolicyDecision.Disabled("Global Force Rotation disabled")
        }

        return PolicyDecision.Apply(config.globalMode, "Global policy")
    }

    /**
     * Resolves the orientation value to apply to Activity.setRequestedOrientation().
     * Returns null when no override should be applied.
     */
    fun resolveActivityOrientation(
        requestedOrientation: Int,
        decision: PolicyDecision,
    ): Int? {
        if (decision !is PolicyDecision.Apply) return null
        return decision.mode.screenOrientation
    }

    /**
     * Resolves the orientation value for WindowManager.LayoutParams.screenOrientation.
     * Leaves SCREEN_ORIENTATION_UNSPECIFIED (-1) untouched so floating menus/popups do not
     * impose an artificial orientation request on top of the underlying Activity.
     */
    fun resolveWindowLayoutOrientation(
        layoutScreenOrientation: Int,
        decision: PolicyDecision,
    ): Int? {
        if (decision !is PolicyDecision.Apply) return null
        if (layoutScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            return null
        }
        return decision.mode.screenOrientation
    }
}
