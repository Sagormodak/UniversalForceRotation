package com.sagor.universalforcerotation.xposed

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.sagor.universalforcerotation.core.HookLogger
import com.sagor.universalforcerotation.core.OrientationPolicyEngine
import com.sagor.universalforcerotation.core.OrientationPolicyEngine.PolicyDecision
import com.sagor.universalforcerotation.core.OrientationStateTracker
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.Invoker
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Installs, coordinates, and hot-replaces orientation interception hooks via modern libxposed API 102.
 */
class OrientationHookCoordinator(
    private val xposed: XposedInterface,
    private val packageName: String,
    private val isSystemApp: Boolean,
    private val logger: HookLogger,
    private val stateTracker: OrientationStateTracker = OrientationStateTracker(),
) {

    companion object {
        const val HOOK_ACTIVITY_SET_ORIENTATION = "ufr.activity.setRequestedOrientation"
        const val HOOK_INSTR_ON_CREATE_1 = "ufr.instr.callActivityOnCreate.1"
        const val HOOK_INSTR_ON_CREATE_2 = "ufr.instr.callActivityOnCreate.2"
        const val HOOK_INSTR_ON_RESUME = "ufr.instr.callActivityOnResume"
        const val HOOK_WINDOW_SET_ATTRIBUTES = "ufr.window.setAttributes"
        const val HOOK_WMI_ADD_VIEW = "ufr.wmi.addView"
        const val HOOK_WMI_UPDATE_LAYOUT = "ufr.wmi.updateViewLayout"
    }

    private data class HookSpec(
        val id: String,
        val executable: Executable,
        val optional: Boolean = false,
        val hookerFactory: () -> Hooker,
    )

    @Volatile
    private var currentConfig: RotationConfig = RotationConfig()

    @Volatile
    private var currentDecision: PolicyDecision =
        OrientationPolicyEngine.evaluate(packageName, isSystemApp, currentConfig)

    @Volatile
    private var policyRevision: Long = 1L

    private val activeHandles = mutableListOf<HookHandle>()

    private var setRequestedOrientationMethod: Method? = null
    private var originSetOrientationInvoker: Invoker<*, Method>? = null

    fun updateConfig(newConfig: RotationConfig) {
        currentConfig = newConfig
        logger.verbose = newConfig.verboseLogging
        val nextDecision = OrientationPolicyEngine.evaluate(packageName, isSystemApp, newConfig)
        currentDecision = nextDecision
        policyRevision++
        logger.debug("Policy updated for $packageName (rev=$policyRevision): $nextDecision")
    }

    fun installOrReplaceHooks(oldHandles: List<HookHandle> = emptyList()) {
        val existingById = oldHandles.mapNotNull { handle ->
            val id = if (xposed.apiVersion >= XposedInterface.API_102) handle.id else null
            if (id != null) id to handle else null
        }.toMap().toMutableMap()

        val specs = buildHookSpecs()
        val installed = mutableListOf<HookHandle>()
        val handledOld = HashSet<HookHandle>()
        var failures = 0

        for (spec in specs) {
            val existing = existingById.remove(spec.id)
            val result = runCatching {
                val hooker = spec.hookerFactory()
                if (existing != null &&
                    xposed.apiVersion >= XposedInterface.API_102 &&
                    existing.executable == spec.executable
                ) {
                    handledOld.add(existing)
                    existing.replaceHook(hooker)
                } else {
                    if (existing != null) {
                        handledOld.add(existing)
                        existing.unhook()
                    }
                    val builder = xposed.hook(spec.executable)
                        .setPriority(XposedInterface.PRIORITY_DEFAULT)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                    if (xposed.apiVersion >= XposedInterface.API_102) {
                        builder.setId(spec.id)
                    }
                    builder.intercept(hooker)
                }
            }
            result.onSuccess { handle ->
                installed.add(handle)
                logger.debug("Hook ready [${spec.id}] -> ${spec.executable}")
            }.onFailure { err ->
                if (spec.optional) {
                    logger.warn("Optional hook [${spec.id}] unavailable on this ROM: ${err.message}")
                } else {
                    failures++
                    logger.error("Failed to install hook [${spec.id}]", err)
                }
            }
        }

        // Clean up any leftover handles from previous generations that were not replaced or unhooked.
        for (handle in oldHandles) {
            if (handle !in handledOld) {
                runCatching { handle.unhook() }
            }
        }

        activeHandles.clear()
        activeHandles.addAll(installed)
        logger.info("Hooks installed for $packageName: active=${installed.size}, failures=$failures, decision=$currentDecision")
    }

    fun clearTransientState() {
        stateTracker.clear()
    }

    private fun buildHookSpecs(): List<HookSpec> {
        val specs = mutableListOf<HookSpec>()

        runCatching {
            val method = Activity::class.java.getDeclaredMethod(
                "setRequestedOrientation",
                Int::class.javaPrimitiveType,
            )
            setRequestedOrientationMethod = method
            originSetOrientationInvoker = xposed.getInvoker(method).setType(Invoker.Type.ORIGIN)
            specs += HookSpec(HOOK_ACTIVITY_SET_ORIENTATION, method) { SetRequestedOrientationHooker() }
        }.onFailure {
            logger.error("Cannot resolve Activity.setRequestedOrientation(int)", it)
        }

        runCatching {
            val onCreate1 = Instrumentation::class.java.getDeclaredMethod(
                "callActivityOnCreate",
                Activity::class.java,
                Bundle::class.java,
            )
            specs += HookSpec(HOOK_INSTR_ON_CREATE_1, onCreate1) { LifecycleCreateHooker() }
        }.onFailure {
            logger.error("Cannot resolve Instrumentation.callActivityOnCreate(Activity, Bundle)", it)
        }

        runCatching {
            val onCreate2 = Instrumentation::class.java.getDeclaredMethod(
                "callActivityOnCreate",
                Activity::class.java,
                Bundle::class.java,
                PersistableBundle::class.java,
            )
            specs += HookSpec(HOOK_INSTR_ON_CREATE_2, onCreate2) { LifecycleCreateHooker() }
        }.onFailure {
            logger.warn("Instrumentation.callActivityOnCreate(3-arg) unavailable: ${it.message}")
        }

        runCatching {
            val onResume = Instrumentation::class.java.getDeclaredMethod(
                "callActivityOnResume",
                Activity::class.java,
            )
            specs += HookSpec(HOOK_INSTR_ON_RESUME, onResume) { LifecycleResumeHooker() }
        }.onFailure {
            logger.error("Cannot resolve Instrumentation.callActivityOnResume(Activity)", it)
        }

        runCatching {
            val setAttributes = Window::class.java.getDeclaredMethod(
                "setAttributes",
                WindowManager.LayoutParams::class.java,
            )
            specs += HookSpec(HOOK_WINDOW_SET_ATTRIBUTES, setAttributes) { WindowSetAttributesHooker() }
        }.onFailure {
            logger.warn("Window.setAttributes unavailable: ${it.message}")
        }

        runCatching {
            val wmiClass = Class.forName("android.view.WindowManagerImpl", false, Activity::class.java.classLoader)
            val addView = wmiClass.getDeclaredMethod(
                "addView",
                View::class.java,
                ViewGroup.LayoutParams::class.java,
            )
            specs += HookSpec(HOOK_WMI_ADD_VIEW, addView, optional = true) { WindowManagerLayoutHooker(1) }

            val updateView = wmiClass.getDeclaredMethod(
                "updateViewLayout",
                View::class.java,
                ViewGroup.LayoutParams::class.java,
            )
            specs += HookSpec(HOOK_WMI_UPDATE_LAYOUT, updateView, optional = true) { WindowManagerLayoutHooker(1) }
        }.onFailure {
            logger.warn("WindowManagerImpl optional hooks unavailable: ${it.message}")
        }

        return specs
    }

    private fun invokeOriginalSetOrientation(activity: Activity, orientation: Int): Boolean {
        val invoker = originSetOrientationInvoker ?: return false
        return try {
            invoker.invoke(activity, orientation)
            true
        } catch (ite: InvocationTargetException) {
            val cause = ite.cause
            if (cause is IllegalStateException) {
                logger.warn(
                    "Framework rejected orientation ${OrientationMode.orientationName(orientation)} for ${activity.javaClass.simpleName}: ${cause.message}"
                )
            } else {
                logger.error(
                    "Original setRequestedOrientation threw for ${activity.javaClass.simpleName}",
                    cause ?: ite,
                )
            }
            false
        } catch (t: Throwable) {
            logger.error("Failed invoking original setRequestedOrientation", t)
            false
        }
    }

    private fun enforceOnActivityLifecycle(activity: Activity, stage: String) {
        if (activity.packageName != packageName) return
        val decision = currentDecision
        val rev = policyRevision

        if (decision !is PolicyDecision.Apply) {
            val restoreOrientation = stateTracker.consumeRestoreOrientationIfNeeded(activity)
            if (restoreOrientation != null && !activity.isFinishing && !activity.isDestroyed) {
                logger.debug(
                    "Restoring ${activity.javaClass.simpleName} ($stage) to ${OrientationMode.orientationName(restoreOrientation)}"
                )
                invokeOriginalSetOrientation(activity, restoreOrientation)
            }
            return
        }

        val targetOrientation = decision.mode.screenOrientation
        if (!stateTracker.shouldApplyForLifecycle(activity, targetOrientation, rev)) {
            return
        }
        if (ActivitySafetyChecker.shouldSkipApplyingOrientation(activity, targetOrientation)) {
            logger.debug("Skipped $stage enforcement on translucent/finishing ${activity.javaClass.simpleName}")
            return
        }

        if (invokeOriginalSetOrientation(activity, targetOrientation)) {
            stateTracker.markApplied(activity, targetOrientation, rev, SystemClock.elapsedRealtime())
            logger.debug(
                "Applied ${OrientationMode.orientationName(targetOrientation)} on ${activity.javaClass.simpleName} ($stage)"
            )
        }
    }

    private inner class SetRequestedOrientationHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val activity = chain.thisObject as? Activity ?: return chain.proceed()
            if (activity.packageName != packageName) return chain.proceed()
            val appRequested = chain.getArg(0) as? Int ?: return chain.proceed()

            val decision = currentDecision
            val targetOrientation = OrientationPolicyEngine.resolveActivityOrientation(appRequested, decision)
                ?: return chain.proceed()

            if (ActivitySafetyChecker.shouldSkipApplyingOrientation(activity, targetOrientation)) {
                logger.debug(
                    "Skipping runtime override on translucent/finishing ${activity.javaClass.simpleName} (requested=${OrientationMode.orientationName(appRequested)})"
                )
                return chain.proceed()
            }

            val rev = policyRevision
            val nowMs = SystemClock.elapsedRealtime()
            val shouldApply = stateTracker.shouldApplyForRuntimeRequest(
                activity = activity,
                appRequested = appRequested,
                targetOrientation = targetOrientation,
                policyRevision = rev,
                nowMs = nowMs,
            )
            if (!shouldApply) {
                logger.debug(
                    "Suppressed duplicate setRequestedOrientation(${OrientationMode.orientationName(appRequested)}) on ${activity.javaClass.simpleName}"
                )
                return null
            }

            return try {
                val res = chain.proceed(arrayOf(targetOrientation))
                stateTracker.markApplied(activity, targetOrientation, rev, nowMs)
                logger.debug(
                    "Overrode setRequestedOrientation(${OrientationMode.orientationName(appRequested)}) -> ${OrientationMode.orientationName(targetOrientation)} on ${activity.javaClass.simpleName}"
                )
                res
            } catch (ise: IllegalStateException) {
                logger.warn(
                    "Activity ${activity.javaClass.simpleName} rejected ${OrientationMode.orientationName(targetOrientation)}: ${ise.message}"
                )
                null
            }
        }
    }

    private inner class LifecycleCreateHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val activity = chain.getArg(0) as? Activity ?: return result
            enforceOnActivityLifecycle(activity, "onCreate")
            return result
        }
    }

    private inner class LifecycleResumeHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val activity = chain.getArg(0) as? Activity ?: return result
            enforceOnActivityLifecycle(activity, "onResume")
            return result
        }
    }

    private inner class WindowSetAttributesHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val lp = chain.getArg(0) as? WindowManager.LayoutParams ?: return chain.proceed()
            val targetOrientation = OrientationPolicyEngine.resolveWindowLayoutOrientation(
                lp.screenOrientation,
                currentDecision,
            ) ?: return chain.proceed()

            if (lp.screenOrientation == targetOrientation) {
                return chain.proceed()
            }

            val copy = WindowManager.LayoutParams().apply { copyFrom(lp) }
            copy.screenOrientation = targetOrientation
            logger.debug(
                "Window.setAttributes screenOrientation ${OrientationMode.orientationName(lp.screenOrientation)} -> ${OrientationMode.orientationName(targetOrientation)}"
            )
            return chain.proceed(arrayOf(copy))
        }
    }

    private inner class WindowManagerLayoutHooker(private val paramsArgIndex: Int) : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val rawParams = chain.getArg(paramsArgIndex) as? WindowManager.LayoutParams
                ?: return chain.proceed()

            val targetOrientation = OrientationPolicyEngine.resolveWindowLayoutOrientation(
                rawParams.screenOrientation,
                currentDecision,
            ) ?: return chain.proceed()

            if (rawParams.screenOrientation == targetOrientation) {
                return chain.proceed()
            }

            val copy = WindowManager.LayoutParams().apply { copyFrom(rawParams) }
            copy.screenOrientation = targetOrientation
            val newArgs = chain.args.toTypedArray()
            newArgs[paramsArgIndex] = copy
            logger.debug(
                "WindowManagerImpl layout screenOrientation ${OrientationMode.orientationName(rawParams.screenOrientation)} -> ${OrientationMode.orientationName(targetOrientation)}"
            )
            return chain.proceed(newArgs)
        }
    }
}
