package com.sagor.universalforcerotation.xposed

import android.app.Activity
import com.sagor.universalforcerotation.model.OrientationMode

/**
 * Guards Activity orientation calls against lifecycle states and Android's translucent/floating
 * window restriction (IllegalStateException on fixed orientation requests).
 */
object ActivitySafetyChecker {

    private val TRANSLUCENT_ATTRS = intArrayOf(
        android.R.attr.windowIsTranslucent,
        android.R.attr.windowIsFloating,
    )

    fun isTranslucentOrFloating(activity: Activity): Boolean {
        val theme = activity.theme ?: return false
        val typedArray = theme.obtainStyledAttributes(TRANSLUCENT_ATTRS)
        return try {
            val isTranslucent = typedArray.getBoolean(0, false)
            val isFloating = typedArray.getBoolean(1, false)
            isTranslucent || isFloating
        } finally {
            typedArray.recycle()
        }
    }

    fun shouldSkipApplyingOrientation(activity: Activity, targetOrientation: Int): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return true
        }
        return OrientationMode.isFixedOrientation(targetOrientation) &&
            isTranslucentOrFloating(activity)
    }
}
