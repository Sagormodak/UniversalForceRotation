package com.sagor.universalforcerotation.core

import android.content.pm.ApplicationInfo

/**
 * Defense-in-depth safety guard preventing hooks on critical system processes and packages whose
 * orientation changes or interference could cause bootloops, SystemUI crashes, or lockouts.
 */
object SafetyGuard {

    val PROTECTED_PACKAGES: Set<String> = setOf(
        "android",
        "system",
        "com.android.systemui",
        "com.android.phone",
        "com.android.telecom",
        "com.android.server.telecom",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.se",
        "com.android.networkstack",
        "com.android.shell",
        "com.android.keyguard",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.providers.settings",
        "com.android.webview",
        "com.google.android.webview",
        "com.sagor.universalforcerotation",
        "org.matrix.vector.manager",
        "org.lsposed.manager",
    )

    private val PROTECTED_PROCESS_PREFIXES: Set<String> = setOf(
        "system_server",
        "com.android.systemui",
        "com.android.phone",
        "com.android.bluetooth",
    )

    fun isProtectedProcess(processName: String?, isSystemServer: Boolean): Boolean {
        if (isSystemServer) return true
        if (processName.isNullOrBlank()) return false
        return PROTECTED_PROCESS_PREFIXES.any { prefix ->
            processName == prefix || processName.startsWith("$prefix:")
        }
    }

    fun isProtectedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in PROTECTED_PACKAGES
    }

    fun isSystemApplicationFlags(flags: Int): Boolean {
        val systemMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (flags and systemMask) != 0
    }
}
