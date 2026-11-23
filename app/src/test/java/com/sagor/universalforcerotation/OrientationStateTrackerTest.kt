package com.sagor.universalforcerotation

import android.content.pm.ActivityInfo
import com.sagor.universalforcerotation.core.OrientationStateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationStateTrackerTest {

    @Test
    fun `suppresses repeated duplicate orientation requests under same policy revision`() {
        val tracker = OrientationStateTracker()
        val activity = Any()
        val target = ActivityInfo.SCREEN_ORIENTATION_FULL_USER

        assertTrue(tracker.shouldApplyForRuntimeRequest(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, target, policyRevision = 1L, nowMs = 100L))
        tracker.markApplied(activity, target, policyRevision = 1L, nowMs = 100L)

        // App spams PORTRAIT repeatedly -> suppressed so Binder IPC and loop protection are never hit
        assertFalse(tracker.shouldApplyForRuntimeRequest(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, target, policyRevision = 1L, nowMs = 200L))
        assertFalse(tracker.shouldApplyForRuntimeRequest(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, target, policyRevision = 1L, nowMs = 300L))

        val snap = tracker.snapshot(activity)!!
        assertEquals(1, snap.interceptedCount)
        assertEquals(2, snap.suppressedDuplicateCount)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, snap.lastAppRequested)
    }

    @Test
    fun `policy revision change triggers immediate re-application`() {
        val tracker = OrientationStateTracker()
        val activity = Any()

        tracker.markApplied(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_USER, policyRevision = 1L, nowMs = 100L)
        assertFalse(tracker.shouldApplyForLifecycle(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_USER, policyRevision = 1L))

        // User switched mode to LANDSCAPE -> revision 2
        assertTrue(
            tracker.shouldApplyForRuntimeRequest(
                activity,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                policyRevision = 2L,
                nowMs = 400L,
            )
        )
        assertTrue(tracker.shouldApplyForLifecycle(activity, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, policyRevision = 2L))
    }

    @Test
    fun `separate activities maintain independent orientation state`() {
        val tracker = OrientationStateTracker()
        val activityA = Any()
        val activityB = Any()

        tracker.markApplied(activityA, ActivityInfo.SCREEN_ORIENTATION_FULL_USER, policyRevision = 1L, nowMs = 100L)
        assertFalse(tracker.shouldApplyForLifecycle(activityA, ActivityInfo.SCREEN_ORIENTATION_FULL_USER, policyRevision = 1L))
        assertTrue(tracker.shouldApplyForLifecycle(activityB, ActivityInfo.SCREEN_ORIENTATION_FULL_USER, policyRevision = 1L))
    }

    @Test
    fun `disabling module restores last app-requested orientation once`() {
        val tracker = OrientationStateTracker()
        val activity = Any()

        assertTrue(
            tracker.shouldApplyForRuntimeRequest(
                activity,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                policyRevision = 1L,
                nowMs = 100L,
            )
        )
        tracker.markApplied(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_USER, policyRevision = 1L, nowMs = 100L)

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, tracker.consumeRestoreOrientationIfNeeded(activity))
        // Second call returns null so restore does not keep re-firing
        assertNull(tracker.consumeRestoreOrientationIfNeeded(activity))
    }
}
