package com.sagor.universalforcerotation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sagor.universalforcerotation.BuildConfig
import com.sagor.universalforcerotation.UniversalForceRotationApp
import com.sagor.universalforcerotation.data.FrameworkSnapshot
import com.sagor.universalforcerotation.databinding.FragmentDiagnosticsBinding
import com.sagor.universalforcerotation.model.OrientationMode
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!
    private var lastReportText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as UniversalForceRotationApp
        val serviceManager = app.serviceManager
        val configRepo = app.configRepository

        binding.btnRefreshDiagnostics.setOnClickListener {
            serviceManager.refreshSnapshot()
            renderAll(serviceManager.snapshot.value, configRepo.config.value)
        }

        binding.btnHotReloadAll.setOnClickListener {
            val targets = serviceManager.snapshot.value.runningTargets
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), "No running hooked processes reported by Vector", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            targets.forEach { target ->
                serviceManager.hotReloadTarget(target) { reloadedTarget, result ->
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            val msg = result.message()?.let { " ($it)" }.orEmpty()
                            Toast.makeText(
                                requireContext(),
                                "Hot reload ${reloadedTarget.processName}: ${result.status()}$msg",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        }

        binding.btnCopyDiagnostics.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("UFR Diagnostics", lastReportText))
            Toast.makeText(requireContext(), "Diagnostics report copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(serviceManager.snapshot, configRepo.config) { snapshot, config ->
                    snapshot to config
                }.collect { (snapshot, config) ->
                    renderAll(snapshot, config)
                }
            }
        }
    }

    private fun renderAll(
        snapshot: FrameworkSnapshot,
        config: com.sagor.universalforcerotation.model.RotationConfig,
    ) {
        val props = buildList {
            if ((snapshot.frameworkProperties and XposedService.PROP_CAP_SYSTEM) != 0L) add("PROP_CAP_SYSTEM")
            if ((snapshot.frameworkProperties and XposedService.PROP_CAP_REMOTE) != 0L) add("PROP_CAP_REMOTE")
            if ((snapshot.frameworkProperties and XposedService.PROP_RT_API_PROTECTION) != 0L) add("PROP_RT_API_PROTECTION")
        }

        val frameworkText = if (snapshot.connected) {
            buildString {
                appendLine("Module Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Status: Connected")
                appendLine("Framework: ${snapshot.frameworkName}")
                appendLine("Version: ${snapshot.frameworkVersion} (${snapshot.frameworkVersionCode})")
                appendLine("libxposed API: ${snapshot.apiVersion}")
                appendLine("Capabilities: ${if (props.isEmpty()) "none" else props.joinToString()}")
                appendLine("Scope Count: ${snapshot.scope.size}")
                appendLine("Master Enabled: ${config.masterEnabled}")
                appendLine("Global Force: ${config.globalForceEnabled}")
                appendLine("Global Mode: ${config.globalMode.title} (${OrientationMode.orientationName(config.globalMode.screenOrientation)})")
                append("Overrides: ${config.appOverrides.size} package(s)")
            }
        } else {
            buildString {
                appendLine("Module Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Status: Disconnected (Vector service not bound)")
                appendLine("Master Enabled: ${config.masterEnabled}")
                appendLine("Global Force: ${config.globalForceEnabled}")
                appendLine("Global Mode: ${config.globalMode.title}")
                append("Action: Enable Universal Force Rotation in Vector Manager and restart app.")
            }
        }
        binding.tvFrameworkDetails.text = frameworkText

        val resolver = requireContext().contentResolver
        val autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        val userRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        val cfg = resources.configuration
        val swDp = cfg.smallestScreenWidthDp
        val largeScreenRule = if (swDp >= 600) {
            "sw=${swDp}dp >= 600dp (Android 16/17 large-screen orientation ignore policy active)"
        } else {
            "sw=${swDp}dp < 600dp (phone display; Android 16/17 large-screen override inactive — apps enforce locks normally)"
        }
        val currentOrientation = when (cfg.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
            Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
            else -> "UNDEFINED (${cfg.orientation})"
        }
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.rotation
        }

        val deviceText = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Display Policy: $largeScreenRule")
            appendLine("System Auto Rotate: ${if (autoRotate) "ENABLED" else "DISABLED"}")
            appendLine("User Rotation Lock: ${rotationLabel(userRotation)}")
            appendLine("Current UI Orientation: $currentOrientation")
            append("Current Display Rotation: ${rotationLabel(displayRotation)}")
        }
        binding.tvDeviceDetails.text = deviceText

        val targetsText = if (!snapshot.connected) {
            "Vector service not connected."
        } else if (snapshot.apiVersion < XposedService.API_102) {
            "Running target query requires libxposed service API 102+ (current: ${snapshot.apiVersion})."
        } else if (snapshot.runningTargets.isEmpty()) {
            "No scoped target processes are currently running."
        } else {
            snapshot.runningTargets.joinToString("\n") { t ->
                "- ${t.processName} (pid=${t.pid}, uid=${t.uid}, state=${t.state}, loadedVer=${t.loadedVersionCode})"
            }
        }
        binding.tvRunningTargets.text = targetsText
        binding.btnHotReloadAll.isEnabled = snapshot.connected &&
            snapshot.apiVersion >= XposedService.API_102 &&
            snapshot.runningTargets.isNotEmpty()

        lastReportText = buildString {
            appendLine("=== Universal Force Rotation Diagnostics ===")
            appendLine(frameworkText)
            appendLine()
            appendLine(deviceText)
            appendLine()
            appendLine("=== Running Targets ===")
            appendLine(targetsText)
        }
    }

    private fun rotationLabel(rotation: Int): String = when (rotation) {
        Surface.ROTATION_0 -> "0 deg (Natural)"
        Surface.ROTATION_90 -> "90 deg"
        Surface.ROTATION_180 -> "180 deg"
        Surface.ROTATION_270 -> "270 deg"
        else -> "Unknown ($rotation)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
