package com.sagor.universalforcerotation.data

import android.os.Bundle
import com.sagor.universalforcerotation.core.ConfigSerializer
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FrameworkSnapshot(
    val connected: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val frameworkVersionCode: Long = 0L,
    val apiVersion: Int = 0,
    val frameworkProperties: Long = 0L,
    val scope: Set<String> = emptySet(),
    val runningTargets: List<HookedTarget> = emptyList(),
)

/**
 * Manages the libxposed service Binder connection and exposes reactive framework telemetry,
 * scope requests, and API 102 hot reload operations.
 */
class XposedServiceManager(
    private val configRepository: ConfigRepository,
) : XposedServiceHelper.OnServiceListener {

    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service.asStateFlow()

    private val _snapshot = MutableStateFlow(FrameworkSnapshot())
    val snapshot: StateFlow<FrameworkSnapshot> = _snapshot.asStateFlow()

    override fun onServiceBind(service: XposedService) {
        _service.value = service

        val hasRemote = runCatching {
            (service.frameworkProperties and XposedService.PROP_CAP_REMOTE) != 0L
        }.getOrDefault(false)

        if (hasRemote) {
            runCatching {
                service.getRemotePreferences(ConfigSerializer.PREFS_GROUP)
            }.onSuccess { remotePrefs ->
                configRepository.attachRemotePreferences(remotePrefs)
            }
        }

        refreshSnapshot()
    }

    override fun onServiceDied(service: XposedService) {
        if (_service.value === service) {
            _service.value = null
            configRepository.detachRemotePreferences()
            _snapshot.value = FrameworkSnapshot(connected = false)
        }
    }

    fun refreshSnapshot(): FrameworkSnapshot {
        val svc = _service.value
        if (svc == null) {
            val empty = FrameworkSnapshot(connected = false)
            _snapshot.value = empty
            return empty
        }
        val next = runCatching {
            val api = svc.apiVersion
            val targets = if (api >= XposedService.API_102) {
                runCatching { svc.runningTargets }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            FrameworkSnapshot(
                connected = true,
                frameworkName = svc.frameworkName,
                frameworkVersion = svc.frameworkVersion,
                frameworkVersionCode = svc.frameworkVersionCode,
                apiVersion = api,
                frameworkProperties = svc.frameworkProperties,
                scope = svc.scope.toSet(),
                runningTargets = targets,
            )
        }.getOrElse {
            FrameworkSnapshot(connected = false)
        }
        _snapshot.value = next
        return next
    }

    fun requestSingleAppScope(
        packageName: String,
        onApproved: (List<String>) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val svc = _service.value
        if (svc == null) {
            onFailed("Vector service is not bound. Enable the module in Vector Manager first.")
            return
        }
        runCatching {
            svc.requestScope(
                listOf(packageName),
                object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        refreshSnapshot()
                        onApproved(approved)
                    }

                    override fun onScopeRequestFailed(message: String) {
                        onFailed(message)
                    }
                },
            )
        }.onFailure {
            onFailed(it.message ?: "Failed to request scope from Vector")
        }
    }

    fun removeSingleAppScope(packageName: String): Result<Unit> {
        val svc = _service.value ?: return Result.failure(
            IllegalStateException("Vector service is not bound")
        )
        return runCatching {
            svc.removeScope(listOf(packageName))
            refreshSnapshot()
        }
    }

    fun hotReloadTarget(
        target: HookedTarget,
        onResult: (HookedTarget, HotReloadResult) -> Unit,
    ): Result<Unit> {
        val svc = _service.value ?: return Result.failure(
            IllegalStateException("Vector service is not bound")
        )
        return runCatching {
            svc.hotReloadModule(target, Bundle.EMPTY) { reloadedTarget, result ->
                refreshSnapshot()
                onResult(reloadedTarget, result)
            }
        }
    }
}
