package com.sagor.universalforcerotation

import android.content.pm.ActivityInfo
import com.sagor.universalforcerotation.core.OrientationPolicyEngine
import com.sagor.universalforcerotation.core.OrientationPolicyEngine.PolicyDecision
import com.sagor.universalforcerotation.core.SafetyGuard
import com.sagor.universalforcerotation.model.AppOverride
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationPolicyEngineTest {

    @Test
    fun `orientation modes map to exact Android ActivityInfo constants`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, OrientationMode.FOLLOW_AUTO_ROTATE.screenOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, OrientationMode.FORCE_PORTRAIT.screenOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, OrientationMode.FORCE_LANDSCAPE.screenOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, OrientationMode.REVERSE_PORTRAIT.screenOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, OrientationMode.REVERSE_LANDSCAPE.screenOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, OrientationMode.FULL_SENSOR.screenOrientation)
    }

    @Test
    fun `third-party app enabled by default with Follow System Auto Rotate`() {
        val config = RotationConfig()
        val decision = OrientationPolicyEngine.evaluate("com.example.portraitapp", isSystemApp = false, config = config)

        assertTrue(decision is PolicyDecision.Apply)
        val apply = decision as PolicyDecision.Apply
        assertEquals(OrientationMode.FOLLOW_AUTO_ROTATE, apply.mode)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            OrientationPolicyEngine.resolveActivityOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, decision),
        )
    }

    @Test
    fun `system app excluded by default but can be explicitly enabled`() {
        val defaultConfig = RotationConfig(excludeSystemAppsByDefault = true)
        val defaultDecision = OrientationPolicyEngine.evaluate("com.sec.android.app.camera", isSystemApp = true, config = defaultConfig)
        assertTrue(defaultDecision is PolicyDecision.Disabled)

        val customConfig = defaultConfig.copy(
            appOverrides = mapOf("com.sec.android.app.camera" to AppOverride(enabled = true, mode = OrientationMode.FULL_SENSOR))
        )
        val customDecision = OrientationPolicyEngine.evaluate("com.sec.android.app.camera", isSystemApp = true, config = customConfig)
        assertTrue(customDecision is PolicyDecision.Apply)
        assertEquals(OrientationMode.FULL_SENSOR, (customDecision as PolicyDecision.Apply).mode)
    }

    @Test
    fun `protected critical system package stays blocked even if override enables it`() {
        val config = RotationConfig(
            masterEnabled = true,
            globalForceEnabled = true,
            excludeSystemAppsByDefault = false,
            appOverrides = mapOf("com.android.systemui" to AppOverride(enabled = true, mode = OrientationMode.FULL_SENSOR)),
        )
        for (pkg in SafetyGuard.PROTECTED_PACKAGES) {
            val decision = OrientationPolicyEngine.evaluate(pkg, isSystemApp = true, config = config)
            assertTrue("Expected $pkg to be Blocked", decision is PolicyDecision.Blocked)
        }
    }

    @Test
    fun `master switch off disables all non-protected overrides`() {
        val config = RotationConfig(
            masterEnabled = false,
            appOverrides = mapOf("com.example.app" to AppOverride(enabled = true, mode = OrientationMode.FORCE_LANDSCAPE)),
        )
        val decision = OrientationPolicyEngine.evaluate("com.example.app", isSystemApp = false, config = config)
        assertTrue(decision is PolicyDecision.Disabled)
        assertNull(OrientationPolicyEngine.resolveActivityOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, decision))
    }

    @Test
    fun `global force off allows explicit per-app opt-in while disabling others`() {
        val config = RotationConfig(
            masterEnabled = true,
            globalForceEnabled = false,
            appOverrides = mapOf("com.example.optin" to AppOverride(enabled = true, mode = OrientationMode.FORCE_LANDSCAPE)),
        )
        assertTrue(OrientationPolicyEngine.evaluate("com.example.other", false, config) is PolicyDecision.Disabled)
        val optInDecision = OrientationPolicyEngine.evaluate("com.example.optin", false, config)
        assertTrue(optInDecision is PolicyDecision.Apply)
        assertEquals(OrientationMode.FORCE_LANDSCAPE, (optInDecision as PolicyDecision.Apply).mode)
    }

    @Test
    fun `window layout resolution skips unspecified (-1) and rewrites locking requests`() {
        val decision = PolicyDecision.Apply(OrientationMode.FOLLOW_AUTO_ROTATE, "Global")
        assertNull(
            OrientationPolicyEngine.resolveWindowLayoutOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, decision)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            OrientationPolicyEngine.resolveWindowLayoutOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, decision),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            OrientationPolicyEngine.resolveWindowLayoutOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, decision),
        )
    }
}
