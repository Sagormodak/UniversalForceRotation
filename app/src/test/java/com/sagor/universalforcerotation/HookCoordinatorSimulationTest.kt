package com.sagor.universalforcerotation

import android.content.pm.ActivityInfo
import com.sagor.universalforcerotation.core.OrientationPolicyEngine
import com.sagor.universalforcerotation.core.OrientationPolicyEngine.PolicyDecision
import com.sagor.universalforcerotation.core.OrientationStateTracker
import com.sagor.universalforcerotation.model.AppOverride
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates the required 10 orientation test scenarios end-to-end across policy evaluation,
 * runtime interception, lifecycle fallback, duplicate loop suppression, and live updates.
 */
class HookCoordinatorSimulationTest {

    private class FakeActivity(val name: String, var actualOrientation: Int) {
        var applyCount: Int = 0
    }

    private fun simulateRuntimeCall(
        activity: FakeActivity,
        requested: Int,
        config: RotationConfig,
        tracker: OrientationStateTracker,
        nowMs: Long,
    ) {
        val decision = OrientationPolicyEngine.evaluate("com.example.test", false, config)
        val target = OrientationPolicyEngine.resolveActivityOrientation(requested, decision)
        if (target == null) {
            activity.actualOrientation = requested
            activity.applyCount++
            return
        }
        if (tracker.shouldApplyForRuntimeRequest(activity, requested, target, config.revision, nowMs)) {
            activity.actualOrientation = target
            activity.applyCount++
            tracker.markApplied(activity, target, config.revision, nowMs)
        }
    }

    private fun simulateLifecycle(
        activity: FakeActivity,
        config: RotationConfig,
        tracker: OrientationStateTracker,
        nowMs: Long,
    ) {
        val decision = OrientationPolicyEngine.evaluate("com.example.test", false, config)
        if (decision !is PolicyDecision.Apply) {
            val restore = tracker.consumeRestoreOrientationIfNeeded(activity)
            if (restore != null) {
                activity.actualOrientation = restore
                activity.applyCount++
            }
            return
        }
        val target = decision.mode.screenOrientation
        if (tracker.shouldApplyForLifecycle(activity, target, config.revision)) {
            activity.actualOrientation = target
            activity.applyCount++
            tracker.markApplied(activity, target, config.revision, nowMs)
        }
    }

    @Test
    fun `scenarios 1-3 portrait, landscape, and unspecified activities resolve to FULL_USER`() {
        val config = RotationConfig(globalMode = OrientationMode.FOLLOW_AUTO_ROTATE, revision = 1L)
        val tracker = OrientationStateTracker()

        val portraitAct = FakeActivity("Portrait", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        simulateRuntimeCall(portraitAct, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, config, tracker, 10L)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, portraitAct.actualOrientation)

        val landscapeAct = FakeActivity("Landscape", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        simulateRuntimeCall(landscapeAct, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, config, tracker, 20L)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, landscapeAct.actualOrientation)

        val unspecAct = FakeActivity("Unspecified", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        simulateLifecycle(unspecAct, config, tracker, 30L)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, unspecAct.actualOrientation)
    }

    @Test
    fun `scenario 4 repeated portrait requests do not trigger IPC loop`() {
        val config = RotationConfig(globalMode = OrientationMode.FOLLOW_AUTO_ROTATE, revision = 1L)
        val tracker = OrientationStateTracker()
        val aggressive = FakeActivity("AggressivePortrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        repeat(100) { i ->
            simulateRuntimeCall(aggressive, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, config, tracker, i * 100L)
        }

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, aggressive.actualOrientation)
        assertEquals(1, aggressive.applyCount)
        assertEquals(99, tracker.snapshot(aggressive)?.suppressedDuplicateCount)
    }

    @Test
    fun `scenario 5 manifest-declared portrait app without runtime call is overridden on create`() {
        val config = RotationConfig(globalMode = OrientationMode.FOLLOW_AUTO_ROTATE, revision = 1L)
        val tracker = OrientationStateTracker()
        val manifestLocked = FakeActivity("ManifestPortrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        simulateLifecycle(manifestLocked, config, tracker, 100L)
        simulateLifecycle(manifestLocked, config, tracker, 200L) // resume does not re-apply redundantly

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, manifestLocked.actualOrientation)
        assertEquals(1, manifestLocked.applyCount)
    }

    @Test
    fun `scenarios 6-8 activity switching, recreation, and mode switches`() {
        val config1 = RotationConfig(globalMode = OrientationMode.FOLLOW_AUTO_ROTATE, revision = 1L)
        val tracker = OrientationStateTracker()

        val first = FakeActivity("First", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        val second = FakeActivity("Second", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        val recreatedFirst = FakeActivity("First_Recreated", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        simulateLifecycle(first, config1, tracker, 10L)
        simulateLifecycle(second, config1, tracker, 20L)
        simulateLifecycle(recreatedFirst, config1, tracker, 30L)

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, first.actualOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, second.actualOrientation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, recreatedFirst.actualOrientation)

        // Exclude app live -> restores original requested orientation
        val excludedConfig = config1.copy(
            revision = 2L,
            appOverrides = mapOf("com.example.test" to AppOverride(enabled = false)),
        )
        simulateLifecycle(second, excludedConfig, tracker, 40L)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, second.actualOrientation)
    }

    @Test
    fun `scenarios 9-10 auto rotate respect vs full sensor ignore`() {
        // Follow Auto Rotate maps to SCREEN_ORIENTATION_FULL_USER (respects system Auto Rotate setting)
        val autoRotateCfg = RotationConfig(globalMode = OrientationMode.FOLLOW_AUTO_ROTATE)
        val d1 = OrientationPolicyEngine.evaluate("com.example.app", false, autoRotateCfg) as PolicyDecision.Apply
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, d1.mode.screenOrientation)
        assertTrue(!OrientationMode.isFixedOrientation(d1.mode.screenOrientation))

        // Full Sensor maps to SCREEN_ORIENTATION_FULL_SENSOR (ignores Auto Rotate lock)
        val sensorCfg = RotationConfig(globalMode = OrientationMode.FULL_SENSOR)
        val d2 = OrientationPolicyEngine.evaluate("com.example.app", false, sensorCfg) as PolicyDecision.Apply
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, d2.mode.screenOrientation)
        assertTrue(!OrientationMode.isFixedOrientation(d2.mode.screenOrientation))
    }
}
