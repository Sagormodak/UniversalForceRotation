package com.sagor.universalforcerotation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sagor.universalforcerotation.UniversalForceRotationApp
import com.sagor.universalforcerotation.core.OrientationPolicyEngine.PolicyDecision
import com.sagor.universalforcerotation.databinding.BottomSheetAppConfigBinding
import com.sagor.universalforcerotation.model.OrientationMode
import kotlinx.coroutines.launch

class AppConfigBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PACKAGE = "arg_package"

        fun newInstance(packageName: String): AppConfigBottomSheet {
            return AppConfigBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACKAGE, packageName)
                }
            }
        }
    }

    private var _binding: BottomSheetAppConfigBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetAppConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageName = requireArguments().getString(ARG_PACKAGE) ?: run {
            dismiss()
            return
        }

        val app = requireActivity().application as UniversalForceRotationApp
        val configRepo = app.configRepository
        val serviceManager = app.serviceManager
        val rawInfo = app.appsRepository.rawApps.value.firstOrNull { it.packageName == packageName }

        binding.tvSheetTitle.text = rawInfo?.label ?: packageName
        binding.tvSheetPackage.text = packageName

        viewLifecycleOwner.lifecycleScope.launch {
            val icon = AppIconCache.load(requireContext().packageManager, packageName)
            if (_binding != null && icon != null) {
                binding.ivSheetIcon.setImageDrawable(icon)
            }
        }

        val isProtected = rawInfo?.isProtected == true
        val isSystem = rawInfo?.isSystemApp == true
        binding.tvSheetProtectedNotice.isVisible = isProtected

        val currentConfig = configRepo.config.value
        val currentDecision = app.appsRepository.combineWithPolicy(
            rawApps = listOfNotNull(rawInfo),
            config = currentConfig,
            scopeSet = serviceManager.snapshot.value.scope,
        ).firstOrNull()?.effectiveDecision

        val initialEnabled = currentDecision is PolicyDecision.Apply
        binding.switchSheetEnabled.isEnabled = !isProtected
        binding.switchSheetEnabled.isChecked = initialEnabled
        binding.switchSheetEnabled.setOnCheckedChangeListener { _, checked ->
            configRepo.setAppEnabled(packageName, checked)
        }

        val modeOptions: List<OrientationMode?> = listOf(null) + OrientationMode.entries
        val currentMode = currentConfig.appOverrides[packageName]?.mode
        val buttonIds = mutableMapOf<Int, OrientationMode?>()

        modeOptions.forEach { mode ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = if (mode == null) {
                    "Inherit Global (${currentConfig.globalMode.shortLabel})"
                } else {
                    "${mode.title} — ${mode.shortLabel}"
                }
                isEnabled = !isProtected
                setPadding(8, 12, 8, 12)
            }
            buttonIds[rb.id] = mode
            binding.rgSheetModes.addView(rb)
            if (mode == currentMode) {
                binding.rgSheetModes.check(rb.id)
            }
        }

        binding.rgSheetModes.setOnCheckedChangeListener { _, checkedId ->
            if (!isProtected && checkedId in buttonIds) {
                configRepo.setAppMode(packageName, buttonIds[checkedId])
            }
        }

        val snapshot = serviceManager.snapshot.value
        val inScope = packageName in snapshot.scope
        binding.btnSheetScope.isEnabled = snapshot.connected && !isProtected
        binding.btnSheetScope.text = when {
            isProtected -> "Protected System App (Scope Disabled)"
            !snapshot.connected -> "Vector Not Connected"
            inScope -> "Remove from Vector Scope"
            else -> "Request Vector Scope for App"
        }

        binding.btnSheetScope.setOnClickListener {
            if (inScope) {
                serviceManager.removeSingleAppScope(packageName)
                    .onSuccess {
                        Toast.makeText(requireContext(), "Removed $packageName from Vector scope", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                    .onFailure {
                        Toast.makeText(requireContext(), it.message ?: "Scope removal failed", Toast.LENGTH_SHORT).show()
                    }
            } else {
                serviceManager.requestSingleAppScope(
                    packageName = packageName,
                    onApproved = { approved ->
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Scope approved: $approved", Toast.LENGTH_SHORT).show()
                                dismiss()
                            }
                        }
                    },
                    onFailed = { message ->
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Scope denied: $message", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        }

        binding.btnSheetReset.isEnabled = !isProtected && (!isSystem || currentConfig.appOverrides.containsKey(packageName))
        binding.btnSheetReset.setOnClickListener {
            configRepo.setAppOverride(packageName, null)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
