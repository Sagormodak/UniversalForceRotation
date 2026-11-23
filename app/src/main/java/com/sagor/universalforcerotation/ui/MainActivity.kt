package com.sagor.universalforcerotation.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sagor.universalforcerotation.R
import com.sagor.universalforcerotation.UniversalForceRotationApp
import com.sagor.universalforcerotation.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val app = application as UniversalForceRotationApp
        lifecycleScope.launch {
            if (app.appsRepository.rawApps.value.isEmpty()) {
                app.appsRepository.refreshInstalledApps()
            }
        }

        if (savedInstanceState == null) {
            showTab(R.id.menu_apps)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        (application as UniversalForceRotationApp).serviceManager.refreshSnapshot()
    }

    private fun showTab(menuId: Int) {
        val tag = "tab_$menuId"
        val fragment: Fragment = supportFragmentManager.findFragmentByTag(tag) ?: when (menuId) {
            R.id.menu_exclusions -> ExclusionsFragment()
            R.id.menu_diagnostics -> DiagnosticsFragment()
            else -> AppsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }
}
