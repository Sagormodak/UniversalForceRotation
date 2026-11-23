package com.sagor.universalforcerotation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sagor.universalforcerotation.UniversalForceRotationApp
import com.sagor.universalforcerotation.core.SafetyGuard
import com.sagor.universalforcerotation.databinding.FragmentExclusionsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ExclusionsFragment : Fragment() {

    private var _binding: FragmentExclusionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExclusionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as UniversalForceRotationApp
        val configRepo = app.configRepository
        val appsRepo = app.appsRepository

        binding.switchExcludeSystem.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                configRepo.setExcludeSystemAppsByDefault(isChecked)
            }
        }
        binding.switchVerboseLog.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                configRepo.setVerboseLogging(isChecked)
            }
        }
        binding.btnResetOverrides.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset all per-app overrides?")
                .setMessage("All excluded apps and custom orientation modes will revert to the global default.")
                .setPositiveButton("Reset") { _, _ ->
                    configRepo.resetAllAppOverrides()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.tvProtectedPackages.text = SafetyGuard.PROTECTED_PACKAGES.sorted().joinToString("\n")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(appsRepo.rawApps, configRepo.config) { raw, config ->
                    raw to config
                }.collect { (raw, config) ->
                    if (binding.switchExcludeSystem.isChecked != config.excludeSystemAppsByDefault) {
                        binding.switchExcludeSystem.isChecked = config.excludeSystemAppsByDefault
                    }
                    if (binding.switchVerboseLog.isChecked != config.verboseLogging) {
                        binding.switchVerboseLog.isChecked = config.verboseLogging
                    }

                    val labelByPkg = raw.associate { it.packageName to it.label }
                    renderOverrides(config, labelByPkg, configRepo)
                }
            }
        }
    }

    private fun renderOverrides(
        config: com.sagor.universalforcerotation.model.RotationConfig,
        labelByPkg: Map<String, String>,
        configRepo: com.sagor.universalforcerotation.data.ConfigRepository,
    ) {
        val excluded = config.appOverrides.filter { !it.value.enabled }
        val customEnabled = config.appOverrides.filter { it.value.enabled }

        binding.containerExcludedApps.removeAllViews()
        if (excluded.isEmpty()) {
            binding.containerExcludedApps.addView(emptyLine("No third-party apps are currently excluded."))
        } else {
            excluded.keys.sorted().forEach { pkg ->
                addOverrideRow(
                    container = binding.containerExcludedApps,
                    title = labelByPkg[pkg] ?: pkg,
                    subtitle = "$pkg · Excluded",
                    actionLabel = "Re-enable",
                ) {
                    configRepo.setAppOverride(pkg, null)
                }
            }
        }

        binding.containerCustomApps.removeAllViews()
        if (customEnabled.isEmpty()) {
            binding.containerCustomApps.addView(emptyLine("No custom per-app modes or system overrides configured."))
        } else {
            customEnabled.entries.sortedBy { it.key }.forEach { (pkg, ov) ->
                val modeDesc = ov.mode?.title ?: "Enabled (Follow Global Mode)"
                addOverrideRow(
                    container = binding.containerCustomApps,
                    title = labelByPkg[pkg] ?: pkg,
                    subtitle = "$pkg · $modeDesc",
                    actionLabel = "Reset",
                ) {
                    configRepo.setAppOverride(pkg, null)
                }
            }
        }
    }

    private fun emptyLine(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setPadding(4, 8, 4, 8)
        }
    }

    private fun addOverrideRow(
        container: LinearLayout,
        title: String,
        subtitle: String,
        actionLabel: String,
        onAction: () -> Unit,
    ) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(requireContext()).apply {
            text = title
            textSize = 15f
        }
        val tvSub = TextView(requireContext()).apply {
            text = subtitle
            textSize = 12f
        }
        textCol.addView(tvTitle)
        textCol.addView(tvSub)

        val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = actionLabel
            setOnClickListener { onAction() }
        }
        row.addView(textCol)
        row.addView(btn)
        container.addView(row)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
