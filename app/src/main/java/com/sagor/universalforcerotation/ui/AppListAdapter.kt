package com.sagor.universalforcerotation.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sagor.universalforcerotation.core.OrientationPolicyEngine.PolicyDecision
import com.sagor.universalforcerotation.databinding.ItemAppEntryBinding
import com.sagor.universalforcerotation.model.AppEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppListAdapter(
    private val pm: PackageManager,
    private val scope: CoroutineScope,
    private val onToggleEnabled: (AppEntry, Boolean) -> Unit,
    private val onClickItem: (AppEntry) -> Unit,
) : ListAdapter<AppEntry, AppListAdapter.ViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelIconJob()
    }

    inner class ViewHolder(
        private val binding: ItemAppEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var iconJob: Job? = null

        fun cancelIconJob() {
            iconJob?.cancel()
            iconJob = null
        }

        fun bind(entry: AppEntry) {
            cancelIconJob()
            binding.tvAppName.text = entry.label
            binding.tvPackageName.text = entry.packageName

            val cachedIcon = AppIconCache.getCached(entry.packageName)
            if (cachedIcon != null) {
                binding.ivAppIcon.setImageDrawable(cachedIcon)
            } else {
                binding.ivAppIcon.setImageDrawable(null)
                val targetPkg = entry.packageName
                iconJob = scope.launch {
                    val loaded = AppIconCache.load(pm, targetPkg)
                    if (binding.tvPackageName.text == targetPkg && loaded != null) {
                        binding.ivAppIcon.setImageDrawable(loaded)
                    }
                }
            }

            binding.tvPolicyBadge.text = when (val decision = entry.effectiveDecision) {
                is PolicyDecision.Blocked -> "Protected"
                is PolicyDecision.Disabled -> if (entry.isSystemApp) "System Excluded" else "Disabled"
                is PolicyDecision.Apply -> {
                    if (entry.override?.mode != null) "Custom: ${decision.mode.shortLabel}"
                    else decision.mode.shortLabel
                }
            }

            binding.tvScopeBadge.text = if (entry.inVectorScope) "In Scope" else "Not in Scope"

            val isToggleChecked = entry.effectiveDecision is PolicyDecision.Apply
            binding.switchAppEnabled.setOnCheckedChangeListener(null)
            binding.switchAppEnabled.isEnabled = !entry.isProtected
            binding.switchAppEnabled.isChecked = isToggleChecked
            binding.switchAppEnabled.setOnCheckedChangeListener { _, checked ->
                onToggleEnabled(entry, checked)
            }

            binding.root.setOnClickListener {
                onClickItem(entry)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppEntry>() {
        override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean =
            oldItem == newItem
    }
}
