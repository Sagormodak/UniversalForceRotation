package com.sagor.universalforcerotation.core

import android.content.pm.ActivityInfo
import java.util.WeakHashMap

/**
 * Tracks per-Activity orientation state to eliminate redundant IPC calls, prevent infinite
 * recreation/loop throttling on Android 16/17, and restore original behavior when disabled live.
 */
class OrientationStateTracker {

    data class ActivityOrientationRecord(
        var lastAppRequested: Int? = null,
        var lastAppliedOrientation: Int? = null,
        var lastAppliedPolicyRevision: Long = -1L,
        var lastAppliedElapsedMs: Long = 0L,
        var overriddenByModule: Boolean = false,
        var interceptedCount: Int = 0,
        var suppressedDuplicateCount: Int = 0,
    )

    private val records = WeakHashMap<Any, ActivityOrientationRecord>()

    @Synchronized
    fun shouldApplyForRuntimeRequest(
        activity: Any,
        appRequested: Int,
        targetOrientation: Int,
        policyRevision: Long,
        nowMs: Long,
    ): Boolean {
        val record = records.getOrPut(activity) { ActivityOrientationRecord() }
        record.lastAppRequested = appRequested

        if (record.overriddenByModule &&
            record.lastAppliedOrientation == targetOrientation &&
            record.lastAppliedPolicyRevision == policyRevision
        ) {
            record.suppressedDuplicateCount++
            return false
        }
        return true
    }

    @Synchronized
    fun shouldApplyForLifecycle(
        activity: Any,
        targetOrientation: Int,
        policyRevision: Long,
    ): Boolean {
        val record = records[activity] ?: return true
        return !record.overriddenByModule ||
            record.lastAppliedOrientation != targetOrientation ||
            record.lastAppliedPolicyRevision != policyRevision
    }

    @Synchronized
    fun markApplied(
        activity: Any,
        appliedOrientation: Int,
        policyRevision: Long,
        nowMs: Long,
    ) {
        val record = records.getOrPut(activity) { ActivityOrientationRecord() }
        record.lastAppliedOrientation = appliedOrientation
        record.lastAppliedPolicyRevision = policyRevision
        record.lastAppliedElapsedMs = nowMs
        record.overriddenByModule = true
        record.interceptedCount++
    }

    @Synchronized
    fun consumeRestoreOrientationIfNeeded(activity: Any): Int? {
        val record = records[activity] ?: return null
        if (!record.overriddenByModule) return null
        record.overriddenByModule = false
        record.lastAppliedOrientation = null
        record.lastAppliedPolicyRevision = -1L
        return record.lastAppRequested ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    @Synchronized
    fun snapshot(activity: Any): ActivityOrientationRecord? = records[activity]?.copy()

    @Synchronized
    fun clear() {
        records.clear()
    }
}
