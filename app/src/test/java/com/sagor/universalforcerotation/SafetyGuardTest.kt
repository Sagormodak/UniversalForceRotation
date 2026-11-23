package com.sagor.universalforcerotation

import android.content.pm.ApplicationInfo
import com.sagor.universalforcerotation.core.SafetyGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardTest {

    @Test
    fun `critical processes and system_server are protected`() {
        assertTrue(SafetyGuard.isProtectedProcess("system_server", isSystemServer = true))
        assertTrue(SafetyGuard.isProtectedProcess("system_server", isSystemServer = false))
        assertTrue(SafetyGuard.isProtectedProcess("com.android.systemui", isSystemServer = false))
        assertTrue(SafetyGuard.isProtectedProcess("com.android.systemui:screenshot", isSystemServer = false))
        assertTrue(SafetyGuard.isProtectedProcess("com.android.phone", isSystemServer = false))
        assertTrue(SafetyGuard.isProtectedProcess("com.android.bluetooth", isSystemServer = false))

        assertFalse(SafetyGuard.isProtectedProcess("com.example.videoplayer", isSystemServer = false))
    }

    @Test
    fun `protected packages include self, Vector, SystemUI, and framework`() {
        assertTrue(SafetyGuard.isProtectedPackage("android"))
        assertTrue(SafetyGuard.isProtectedPackage("system"))
        assertTrue(SafetyGuard.isProtectedPackage("com.android.systemui"))
        assertTrue(SafetyGuard.isProtectedPackage("com.sagor.universalforcerotation"))
        assertTrue(SafetyGuard.isProtectedPackage("org.matrix.vector.manager"))

        assertFalse(SafetyGuard.isProtectedPackage("com.sagor.universalforcerotation.testapp"))
        assertFalse(SafetyGuard.isProtectedPackage("com.whatsapp"))
    }

    @Test
    fun `system application flags detected accurately`() {
        assertFalse(SafetyGuard.isSystemApplicationFlags(0))
        assertTrue(SafetyGuard.isSystemApplicationFlags(ApplicationInfo.FLAG_SYSTEM))
        assertTrue(SafetyGuard.isSystemApplicationFlags(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
    }
}
