package com.sagor.universalforcerotation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sagor.universalforcerotation.R
import com.sagor.universalforcerotation.UniversalForceRotationApp
import com.sagor.universalforcerotation.core.OrientationPolicyEngine.PolicyDecision
import com.sagor.universalforcerotation.databinding.FragmentAppsBinding
import com.sagor.universalforcerotation.model.AppEntry
import com.sagor.universalforcerotation.model.OrientationMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppsFragment : androidx.fragment.app.Fragment() {

    private enum class AppFilter {
        THIRD_PARTY,
        EXCLUDED,
        CUSTOM_MODE,
        IN_SCOPE,
        SYSTEM,
        ALL,
    }

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private var currentFilter: AppFilter = AppFilter.THIRD_PARTY
    private var searchQuery: String = ""
    private var latestEntries: List<AppEntry> = emptyList()
    private lateinit var adapter: AppListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as UniversalForceRotationApp
        val configRepo = app.configRepository
        val serviceManager = app.serviceManager
        val appsRepo = app.appsRepository

        adapter = AppListAdapter(
            pm = requireContext().packageManager,
            scope = viewLifecycleOwner.lifecycleScope,
            onToggleEnabled = { entry, enabled ->
                configRepo.setAppEnabled(entry.packageName, enabled)
            },
            onClickItem = { entry ->
                AppConfigBottomSheet.newInstance(entry.packageName)
                    .show(childFragmentManager, "app_config_${entry.packageName}")
            },
        )

        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter

        binding.switchMaster.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                configRepo.setMasterEnabled(isChecked)
            }
        }
        binding.switchGlobalForce.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                configRepo.setGlobalForceEnabled(isChecked)
            }
        }

        val openDefaultModeDialog = View.OnClickListener {
            showDefaultModeDialog(configRepo.config.value.globalMode) { chosen ->
                configRepo.setGlobalMode(chosen)
            }
        }
        binding.rowDefaultMode.setOnClickListener(openDefaultModeDialog)
        binding.btnChangeDefaultMode.setOnClickListener(openDefaultModeDialog)

        binding.etSearch.addTextChangedListener { editable ->
            searchQuery = editable?.toString()?.trim().orEmpty()
            applyFilter()
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipExcluded -> AppFilter.EXCLUDED
                R.id.chipCustomMode -> AppFilter.CUSTOM_MODE
                R.id.chipInScope -> AppFilter.IN_SCOPE
                R.id.chipSystem -> AppFilter.SYSTEM
                R.id.chipAll -> AppFilter.ALL
                else -> AppFilter.THIRD_PARTY
            }
            applyFilter()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    appsRepo.rawApps,
                    configRepo.config,
                    serviceManager.snapshot,
                ) { raw, config, snapshot ->
                    Triple(raw, config, snapshot)
                }.collect { (raw, config, snapshot) ->
                    renderStatusCard(snapshot)
                    renderMasterControls(config)
                    latestEntries = appsRepo.combineWithPolicy(raw, config, snapshot.scope)
                    applyFilter()
                }
            }
        }
    }

    private fun renderStatusCard(snapshot: com.sagor.universalforcerotation.data.FrameworkSnapshot) {
        if (snapshot.connected) {
            binding.statusCard.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.ufr_surface_ok)
            )
            binding.tvStatusTitle.text =
                "${snapshot.frameworkName} ${snapshot.frameworkVersion} · API ${snapshot.apiVersion}"
            binding.tvStatusSubtitle.text =
                "Scope: ${snapshot.scope.size} package(s) · Active hooked processes: ${snapshot.runningTargets.size}"
        } else {
            binding.statusCard.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.ufr_surface_warn)
            )
            binding.tvStatusTitle.text = getString(R.string.status_disconnected_title)
            binding.tvStatusSubtitle.text = getString(R.string.status_disconnected_subtitle)
        }
    }

    private fun renderMasterControls(config: com.sagor.universalforcerotation.model.RotationConfig) {
        if (binding.switchMaster.isChecked != config.masterEnabled) {
            binding.switchMaster.isChecked = config.masterEnabled
        }
        if (binding.switchGlobalForce.isChecked != config.globalForceEnabled) {
            binding.switchGlobalForce.isChecked = config.globalForceEnabled
        }
        binding.switchGlobalForce.isEnabled = config.masterEnabled
        binding.tvDefaultModeValue.text =
            "${config.globalMode.title} (${config.globalMode.shortLabel})"
    }

    private fun applyFilter() {
        val query = searchQuery.lowercase()
        val filtered = latestEntries.filter { entry ->
            val matchesFilter = when (currentFilter) {
                AppFilter.THIRD_PARTY -> !entry.isSystemApp && !entry.isProtected
                AppFilter.EXCLUDED -> entry.effectiveDecision !is PolicyDecision.Apply && !entry.isProtected
                AppFilter.CUSTOM_MODE -> entry.override?.mode != null
                AppFilter.IN_SCOPE -> entry.inVectorScope
                AppFilter.SYSTEM -> entry.isSystemApp
                AppFilter.ALL -> true
            }
            val matchesQuery = query.isEmpty() ||
                entry.label.lowercase().contains(query) ||
                entry.packageName.lowercase().contains(query)
            matchesFilter && matchesQuery
        }

        adapter.submitList(filtered)
        binding.tvAppCount.text = "Showing ${filtered.size} of ${latestEntries.size} installed apps"
        binding.tvEmptyApps.isVisible = filtered.isEmpty() && latestEntries.isNotEmpty()
    }

    private fun showDefaultModeDialog(
        current: OrientationMode,
        onSelected: (OrientationMode) -> Unit,
    ) {
        val modes = OrientationMode.entries
        val labels = modes.map { "${it.title}\n${it.description}" }.toTypedArray()
        val selectedIndex = modes.indexOf(current).coerceAtLeast(0)
        var pendingIndex = selectedIndex

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_mode_label)
            .setSingleChoiceItems(labels, selectedIndex) { _, which ->
                pendingIndex = which
            }
            .setPositiveButton("Apply") { _, _ ->
                onSelected(modes[pendingIndex])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
