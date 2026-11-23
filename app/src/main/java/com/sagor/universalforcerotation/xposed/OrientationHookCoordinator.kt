package com.sagor.universalforcerotation.xposed

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
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
import com.sagor.universalforcerotation.core.SafetyGuard
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.Invoker
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Installs, coordinates, and hot-replaces multi-layer orientation interception hooks via modern
 * libxposed API 102 across Activity lifecycle, Instrumentation, ActivityClient Binder, Window,
 * WindowManagerImpl, and WindowManagerGlobal.
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
        const val HOOK_ACTIVITY_ON_CREATE_1 = "ufr.activity.onCreate.1"
        const val HOOK_ACTIVITY_ON_CREATE_2 = "ufr.activity.onCreate.2"
        const val HOOK_ACTIVITY_ON_START = "ufr.activity.onStart"
        const val HOOK_ACTIVITY_ON_RESUME = "ufr.activity.onResume"
        const val HOOK_ACTIVITY_ON_ATTACHED = "ufr.activity.onAttachedToWindow"
        const val HOOK_ACTIVITY_ON_WINDOW_FOCUS = "ufr.activity.onWindowFocusChanged"
        const val HOOK_ACTIVITY_CLIENT_SET_ORIENTATION = "ufr.activityClient.setRequestedOrientation"
        const val HOOK_INSTR_ON_CREATE_1 = "ufr.instr.callActivityOnCreate.1"
        const val HOOK_INSTR_ON_CREATE_2 = "ufr.instr.callActivityOnCreate.2"
        const val HOOK_INSTR_ON_RESUME = "ufr.instr.callActivityOnResume"
        const val HOOK_WINDOW_SET_ATTRIBUTES = "ufr.window.setAttributes"
        const val HOOK_WMI_ADD_VIEW = "ufr.wmi.addView"
        const val HOOK_WMI_UPDATE_LAYOUT = "ufr.wmi.updateViewLayout"
        const val HOOK_WMG_ADD_VIEW_PREFIX = "ufr.wmg.addView."
        const val HOOK_WMG_UPDATE_LAYOUT_PREFIX = "ufr.wmg.updateViewLayout."
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

    private var activityTokenField: Field? = null
    private var activityLastOrientationField: Field? = null
    private var activityInfoField: Field? = null

    private var activityClientGetInstanceMethod: Method? = null
    private var activityClientGetOrientationMethod: Method? = null
    private var originActivityClientSetOrientationInvoker: Invoker<*, Method>? = null
    private var activityClientSetIsStatic: Boolean = false

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

    private fun initReflectionHelpers() {
        runCatching {
            activityTokenField = Activity::class.java.getDeclaredField("mToken").apply {
                isAccessible = true
            }
        }
        runCatching {
            activityLastOrientationField = runCatching {
                Activity::class.java.getDeclaredField("mLastRequestedOrientation")
            }.getOrElse {
                Activity::class.java.getDeclaredField("mRequestedOrientation")
            }.apply {
                isAccessible = true
            }
        }
        runCatching {
            activityInfoField = Activity::class.java.getDeclaredField("mActivityInfo").apply {
                isAccessible = true
            }
        }
    }

    private fun buildHookSpecs(): List<HookSpec> {
        initReflectionHelpers()
        val specs = mutableListOf<HookSpec>()
        val frameworkLoader = Activity::class.java.classLoader

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

        // Direct Activity lifecycle hooks cannot be bypassed by custom Instrumentation wrappers.
        runCatching {
            val actOnCreate1 = Activity::class.java.getDeclaredMethod(
                "onCreate",
                Bundle::class.java,
            )
            specs += HookSpec(HOOK_ACTIVITY_ON_CREATE_1, actOnCreate1) { DirectActivityLifecycleHooker("activity.onCreate") }
        }.onFailure {
            logger.warn("Activity.onCreate(Bundle) unavailable: ${it.message}")
        }

        runCatching {
            val actOnCreate2 = Activity::class.java.getDeclaredMethod(
                "onCreate",
                Bundle::class.java,
                PersistableBundle::class.java,
            )
            specs += HookSpec(HOOK_ACTIVITY_ON_CREATE_2, actOnCreate2, optional = true) {
                DirectActivityLifecycleHooker("activity.onCreate2")
            }
        }

        runCatching {
            val actOnStart = Activity::class.java.getDeclaredMethod("onStart")
            specs += HookSpec(HOOK_ACTIVITY_ON_START, actOnStart) { DirectActivityLifecycleHooker("activity.onStart") }
        }.onFailure {
            logger.warn("Activity.onStart() unavailable: ${it.message}")
        }

        runCatching {
            val actOnResume = Activity::class.java.getDeclaredMethod("onResume")
            specs += HookSpec(HOOK_ACTIVITY_ON_RESUME, actOnResume) { DirectActivityLifecycleHooker("activity.onResume") }
        }.onFailure {
            logger.warn("Activity.onResume() unavailable: ${it.message}")
        }

        runCatching {
            val actAttached = Activity::class.java.getDeclaredMethod("onAttachedToWindow")
            specs += HookSpec(HOOK_ACTIVITY_ON_ATTACHED, actAttached, optional = true) {
                DirectActivityLifecycleHooker("activity.onAttachedToWindow")
            }
        }

        runCatching {
            val actFocus = Activity::class.java.getDeclaredMethod(
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType,
            )
            specs += HookSpec(HOOK_ACTIVITY_ON_WINDOW_FOCUS, actFocus, optional = true) { ActivityWindowFocusHooker() }
        }

        runCatching {
            val onCreate1 = Instrumentation::class.java.getDeclaredMethod(
                "callActivityOnCreate",
                Activity::class.java,
                Bundle::class.java,
            )
            specs += HookSpec(HOOK_INSTR_ON_CREATE_1, onCreate1) { InstrumentationLifecycleHooker("instr.onCreate") }
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
            specs += HookSpec(HOOK_INSTR_ON_CREATE_2, onCreate2, optional = true) {
                InstrumentationLifecycleHooker("instr.onCreate2")
            }
        }

        runCatching {
            val onResume = Instrumentation::class.java.getDeclaredMethod(
                "callActivityOnResume",
                Activity::class.java,
            )
            specs += HookSpec(HOOK_INSTR_ON_RESUME, onResume) { InstrumentationLifecycleHooker("instr.onResume") }
        }.onFailure {
            logger.error("Cannot resolve Instrumentation.callActivityOnResume(Activity)", it)
        }

        runCatching {
            val acClass = Class.forName("android.app.ActivityClient", false, frameworkLoader)
            activityClientGetInstanceMethod = acClass.declaredMethods.firstOrNull {
                it.name == "getInstance" && it.parameterCount == 0
            }?.apply { isAccessible = true }

            activityClientGetOrientationMethod = acClass.declaredMethods.firstOrNull {
                it.name == "getRequestedOrientation" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == IBinder::class.java
            }?.apply { isAccessible = true }

            val acSetMethod = acClass.declaredMethods.firstOrNull {
                it.name == "setRequestedOrientation" &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0] == IBinder::class.java &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType
            }
            if (acSetMethod != null) {
                acSetMethod.isAccessible = true
                activityClientSetIsStatic = Modifier.isStatic(acSetMethod.modifiers)
                originActivityClientSetOrientationInvoker =
                    xposed.getInvoker(acSetMethod).setType(Invoker.Type.ORIGIN)
                specs += HookSpec(HOOK_ACTIVITY_CLIENT_SET_ORIENTATION, acSetMethod, optional = true) {
                    ActivityClientSetOrientationHooker()
                }
            }
        }.onFailure {
            logger.warn("ActivityClient optional hooks unavailable: ${it.message}")
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
            val wmiClass = Class.forName("android.view.WindowManagerImpl", false, frameworkLoader)
            val addView = wmiClass.getDeclaredMethod(
                "addView",
                View::class.java,
                ViewGroup.LayoutParams::class.java,
            )
            specs += HookSpec(HOOK_WMI_ADD_VIEW, addView, optional = true) { WindowManagerLayoutHooker(1, "WindowManagerImpl.addView") }

            val updateView = wmiClass.getDeclaredMethod(
                "updateViewLayout",
                View::class.java,
                ViewGroup.LayoutParams::class.java,
            )
            specs += HookSpec(HOOK_WMI_UPDATE_LAYOUT, updateView, optional = true) {
                WindowManagerLayoutHooker(1, "WindowManagerImpl.updateViewLayout")
            }
        }.onFailure {
            logger.warn("WindowManagerImpl optional hooks unavailable: ${it.message}")
        }

        runCatching {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal", false, frameworkLoader)
            var addIdx = 0
            var updateIdx = 0
            for (method in wmgClass.declaredMethods) {
                val params = method.parameterTypes
                val lpIndex = params.indexOfFirst { ViewGroup.LayoutParams::class.java.isAssignableFrom(it) }
                if (lpIndex < 0) continue
                when (method.name) {
                    "addView" -> {
                        specs += HookSpec("${HOOK_WMG_ADD_VIEW_PREFIX}${addIdx++}", method, optional = true) {
                            WindowManagerLayoutHooker(lpIndex, "WindowManagerGlobal.addView")
                        }
                    }
                    "updateViewLayout" -> {
                        specs += HookSpec("${HOOK_WMG_UPDATE_LAYOUT_PREFIX}${updateIdx++}", method, optional = true) {
                            WindowManagerLayoutHooker(lpIndex, "WindowManagerGlobal.updateViewLayout")
                        }
                    }
                }
            }
        }.onFailure {
            logger.warn("WindowManagerGlobal optional hooks unavailable: ${it.message}")
        }

        return specs
    }

    private fun readServerRequestedOrientation(activity: Activity): Int? {
        val getMethod = activityClientGetOrientationMethod
        val token = runCatching { activityTokenField?.get(activity) as? IBinder }.getOrNull()
        if (getMethod != null && token != null) {
            val serverVal = runCatching {
                val instance = if (Modifier.isStatic(getMethod.modifiers)) {
                    null
                } else {
                    activityClientGetInstanceMethod?.invoke(null)
                }
                if (instance != null || Modifier.isStatic(getMethod.modifiers)) {
                    getMethod.invoke(instance, token) as? Int
                } else {
                    null
                }
            }.getOrNull()
            if (serverVal != null) return serverVal
        }
        return runCatching { activity.requestedOrientation }.getOrNull()
    }

    private fun syncActivityFields(activity: Activity, orientation: Int) {
        runCatching {
            (activityInfoField?.get(activity) as? ActivityInfo)?.let { info ->
                if (info.screenOrientation != orientation) {
                    info.screenOrientation = orientation
                }
            }
        }
        runCatching {
            activityLastOrientationField?.setInt(activity, orientation)
        }
        runCatching {
            val win = activity.window
            val attrs = win?.attributes
            if (attrs != null &&
                attrs.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                attrs.screenOrientation != orientation
            ) {
                attrs.screenOrientation = orientation
                win.attributes = attrs
            }
        }
    }

    private fun invokeDirectOrientation(activity: Activity, orientation: Int): Boolean {
        syncActivityFields(activity, orientation)

        val acInvoker = originActivityClientSetOrientationInvoker
        val token = runCatching { activityTokenField?.get(activity) as? IBinder }.getOrNull()
        if (acInvoker != null && token != null) {
            val acSuccess = try {
                val instance = if (activityClientSetIsStatic) {
                    null
                } else {
                    activityClientGetInstanceMethod?.invoke(null)
                }
                if (instance != null || activityClientSetIsStatic) {
                    acInvoker.invoke(instance, token, orientation)
                    true
                } else {
                    false
                }
            } catch (ite: InvocationTargetException) {
                val cause = ite.cause
                if (cause is IllegalStateException) {
                    logger.warn(
                        "ActivityClient rejected ${OrientationMode.orientationName(orientation)} for ${activity.javaClass.name}: ${cause.message}"
                    )
                    return false
                }
                logger.warn("ActivityClient invocation fallback for ${activity.javaClass.name}: ${cause?.message ?: ite.message}")
                false
            } catch (_: Throwable) {
                false
            }
            if (acSuccess) return true
        }

        val invoker = originSetOrientationInvoker ?: return false
        return try {
            // Ensure Activity.mLastRequestedOrientation cannot short-circuit a stale server state.
            runCatching { activityLastOrientationField?.setInt(activity, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED - 1) }
            invoker.invoke(activity, orientation)
            true
        } catch (ite: InvocationTargetException) {
            val cause = ite.cause
            if (cause is IllegalStateException) {
                logger.warn(
                    "Framework rejected orientation ${OrientationMode.orientationName(orientation)} for ${activity.javaClass.name}: ${cause.message}"
                )
            } else {
                logger.error(
                    "Original setRequestedOrientation threw for ${activity.javaClass.name}",
                    cause ?: ite,
                )
            }
            false
        } catch (t: Throwable) {
            logger.error("Failed invoking original setRequestedOrientation", t)
            false
        }
    }

    private fun isActivitySafeForPackage(activity: Activity): Boolean {
        val actPkg = runCatching { activity.packageName }.getOrNull()
        return !SafetyGuard.isProtectedPackage(actPkg)
    }

    private fun enforceOnActivityLifecycle(activity: Activity, stage: String) {
        if (!isActivitySafeForPackage(activity)) return
        val decision = currentDecision
        val rev = policyRevision
        val nowMs = SystemClock.elapsedRealtime()

        if (decision !is PolicyDecision.Apply) {
            val restoreOrientation = stateTracker.consumeRestoreOrientationIfNeeded(activity)
            if (restoreOrientation != null && !activity.isFinishing && !activity.isDestroyed) {
                logger.debug(
                    "Restoring ${activity.javaClass.name} ($stage) to ${OrientationMode.orientationName(restoreOrientation)}"
                )
                invokeDirectOrientation(activity, restoreOrientation)
            }
            return
        }

        val targetOrientation = decision.mode.screenOrientation
        val serverBefore = readServerRequestedOrientation(activity)
        if (!stateTracker.shouldApplyForLifecycle(activity, targetOrientation, rev, nowMs, serverBefore)) {
            return
        }
        if (ActivitySafetyChecker.shouldSkipApplyingOrientation(activity, targetOrientation)) {
            logger.debug(
                "Skipped $stage enforcement on translucent/finishing ${activity.javaClass.name} (server=${serverBefore?.let(OrientationMode::orientationName)})"
            )
            return
        }

        if (invokeDirectOrientation(activity, targetOrientation)) {
            stateTracker.markApplied(activity, targetOrientation, rev, nowMs)
            logger.debug(
                "Applied ${OrientationMode.orientationName(targetOrientation)} on ${activity.javaClass.name} ($stage, serverBefore=${serverBefore?.let(OrientationMode::orientationName)}, translucent=${ActivitySafetyChecker.isTranslucentOrFloating(activity)})"
            )
        }
    }

    private inner class SetRequestedOrientationHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val activity = chain.thisObject as? Activity ?: return chain.proceed()
            if (!isActivitySafeForPackage(activity)) return chain.proceed()
            val appRequested = chain.getArg(0) as? Int ?: return chain.proceed()

            val decision = currentDecision
            val targetOrientation = OrientationPolicyEngine.resolveActivityOrientation(appRequested, decision)
                ?: return chain.proceed()

            if (ActivitySafetyChecker.shouldSkipApplyingOrientation(activity, targetOrientation)) {
                logger.debug(
                    "Skipping runtime override on translucent/finishing ${activity.javaClass.name} (requested=${OrientationMode.orientationName(appRequested)})"
                )
                return chain.proceed()
            }

            val rev = policyRevision
            val nowMs = SystemClock.elapsedRealtime()
            val serverBefore = readServerRequestedOrientation(activity)
            val shouldApply = stateTracker.shouldApplyForRuntimeRequest(
                activity = activity,
                appRequested = appRequested,
                targetOrientation = targetOrientation,
                policyRevision = rev,
                nowMs = nowMs,
                serverOrientation = serverBefore,
            )
            if (!shouldApply) {
                logger.debug(
                    "Suppressed duplicate setRequestedOrientation(${OrientationMode.orientationName(appRequested)}) on ${activity.javaClass.name}"
                )
                return null
            }

            return try {
                // Force past Activity.mLastRequestedOrientation if server state had drifted.
                runCatching { activityLastOrientationField?.setInt(activity, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED - 1) }
                val res = chain.proceed(arrayOf(targetOrientation))
                syncActivityFields(activity, targetOrientation)
                stateTracker.markApplied(activity, targetOrientation, rev, nowMs)
                logger.debug(
                    "Overrode Activity.setRequestedOrientation(${OrientationMode.orientationName(appRequested)}) -> ${OrientationMode.orientationName(targetOrientation)} on ${activity.javaClass.name}"
                )
                res
            } catch (ise: IllegalStateException) {
                logger.warn(
                    "Activity ${activity.javaClass.name} rejected ${OrientationMode.orientationName(targetOrientation)}: ${ise.message}"
                )
                null
            }
        }
    }

    private inner class ActivityClientSetOrientationHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val requested = chain.getArg(1) as? Int ?: return chain.proceed()
            val targetOrientation = OrientationPolicyEngine.resolveActivityOrientation(requested, currentDecision)
                ?: return chain.proceed()
            if (requested == targetOrientation) {
                return chain.proceed()
            }
            val newArgs = chain.args.toTypedArray()
            newArgs[1] = targetOrientation
            logger.debug(
                "Overrode ActivityClient.setRequestedOrientation(${OrientationMode.orientationName(requested)}) -> ${OrientationMode.orientationName(targetOrientation)}"
            )
            return try {
                chain.proceed(newArgs)
            } catch (ise: IllegalStateException) {
                logger.warn("ActivityClient rejected ${OrientationMode.orientationName(targetOrientation)}: ${ise.message}")
                null
            }
        }
    }

    private inner class DirectActivityLifecycleHooker(private val stage: String) : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity ?: return result
            enforceOnActivityLifecycle(activity, stage)
            return result
        }
    }

    private inner class ActivityWindowFocusHooker : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity ?: return result
            val hasFocus = chain.getArg(0) as? Boolean ?: false
            if (hasFocus) {
                enforceOnActivityLifecycle(activity, "activity.onWindowFocus")
            }
            return result
        }
    }

    private inner class InstrumentationLifecycleHooker(private val stage: String) : Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val activity = chain.getArg(0) as? Activity ?: return result
            enforceOnActivityLifecycle(activity, stage)
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

            val old = lp.screenOrientation
            lp.screenOrientation = targetOrientation
            logger.debug(
                "Window.setAttributes screenOrientation ${OrientationMode.orientationName(old)} -> ${OrientationMode.orientationName(targetOrientation)}"
            )
            return chain.proceed()
        }
    }

    private inner class WindowManagerLayoutHooker(
        private val paramsArgIndex: Int,
        private val sourceName: String,
    ) : Hooker {
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

            val old = rawParams.screenOrientation
            rawParams.screenOrientation = targetOrientation
            logger.debug(
                "$sourceName screenOrientation ${OrientationMode.orientationName(old)} -> ${OrientationMode.orientationName(targetOrientation)}"
            )
            return chain.proceed()
        }
    }
}
