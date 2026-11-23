package com.sagor.universalforcerotation.model

import android.content.pm.ActivityInfo

/**
 * Supported target orientation override modes mapped directly to [ActivityInfo] constants.
 */
enum class OrientationMode(
    val id: String,
    val screenOrientation: Int,
    val title: String,
    val shortLabel: String,
    val description: String,
) {
    FOLLOW_AUTO_ROTATE(
        id = "follow_auto_rotate",
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
        title = "Follow System Auto Rotate",
        shortLabel = "Auto Rotate",
        description = "Allows all 4 orientations when Auto Rotate is enabled; follows user rotation lock when disabled."
    ),
    FORCE_PORTRAIT(
        id = "force_portrait",
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        title = "Force Portrait",
        shortLabel = "Portrait",
        description = "Forces standard portrait orientation regardless of sensor state."
    ),
    FORCE_LANDSCAPE(
        id = "force_landscape",
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        title = "Force Landscape",
        shortLabel = "Landscape",
        description = "Forces standard landscape orientation regardless of sensor state."
    ),
    REVERSE_PORTRAIT(
        id = "reverse_portrait",
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        title = "Reverse Portrait",
        shortLabel = "Rev. Portrait",
        description = "Forces upside-down portrait orientation (180 degrees)."
    ),
    REVERSE_LANDSCAPE(
        id = "reverse_landscape",
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        title = "Reverse Landscape",
        shortLabel = "Rev. Landscape",
        description = "Forces reverse landscape orientation (270 degrees)."
    ),
    FULL_SENSOR(
        id = "full_sensor",
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
        title = "Full Sensor",
        shortLabel = "Full Sensor",
        description = "Rotates across all 4 orientations using the accelerometer, ignoring system Auto Rotate lock."
    );

    companion object {
        fun fromId(id: String?): OrientationMode? = entries.firstOrNull { it.id == id }

        /**
         * Returns true for fixed or axis-locked orientation constants that Android rejects on
         * translucent or floating activities with IllegalStateException.
         */
        fun isFixedOrientation(code: Int): Boolean = when (code) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LOCKED -> true
            else -> false
        }

        fun orientationName(code: Int): String = when (code) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "UNSPECIFIED (-1)"
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "LANDSCAPE (0)"
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "PORTRAIT (1)"
            ActivityInfo.SCREEN_ORIENTATION_USER -> "USER (2)"
            ActivityInfo.SCREEN_ORIENTATION_BEHIND -> "BEHIND (3)"
            ActivityInfo.SCREEN_ORIENTATION_SENSOR -> "SENSOR (4)"
            ActivityInfo.SCREEN_ORIENTATION_NOSENSOR -> "NOSENSOR (5)"
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "SENSOR_LANDSCAPE (6)"
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> "SENSOR_PORTRAIT (7)"
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE (8)"
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "REVERSE_PORTRAIT (9)"
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> "FULL_SENSOR (10)"
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> "USER_LANDSCAPE (11)"
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> "USER_PORTRAIT (12)"
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER -> "FULL_USER (13)"
            ActivityInfo.SCREEN_ORIENTATION_LOCKED -> "LOCKED (14)"
            else -> "UNKNOWN ($code)"
        }
    }
}
