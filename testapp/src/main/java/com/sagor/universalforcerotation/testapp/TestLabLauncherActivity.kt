package com.sagor.universalforcerotation.testapp

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.sagor.universalforcerotation.testapp.databinding.ActivityTestLabLauncherBinding

class TestLabLauncherActivity : AppCompatActivity() {

    companion object {
        private var launchCount = 0
    }

    private lateinit var binding: ActivityTestLabLauncherBinding
    private var recreationCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateTelemetry()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launchCount++
        } else {
            recreationCount = savedInstanceState.getInt("recreation_count", 0) + 1
        }
        binding = ActivityTestLabLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnManifestPortrait.setOnClickListener {
            startActivity(Intent(this, ManifestPortraitActivity::class.java))
        }
        binding.btnManifestLandscape.setOnClickListener {
            startActivity(Intent(this, ManifestLandscapeActivity::class.java))
        }
        binding.btnRuntimePortrait.setOnClickListener {
            startActivity(Intent(this, RuntimePortraitActivity::class.java))
        }
        binding.btnRuntimeLandscape.setOnClickListener {
            startActivity(Intent(this, RuntimeLandscapeActivity::class.java))
        }
        binding.btnAggressivePortrait.setOnClickListener {
            startActivity(Intent(this, AggressivePortraitActivity::class.java))
        }
        binding.btnWindowLayoutLock.setOnClickListener {
            startActivity(Intent(this, WindowLayoutLockActivity::class.java))
        }
        binding.btnTranslucentDialog.setOnClickListener {
            startActivity(Intent(this, TranslucentDialogActivity::class.java))
        }
        binding.btnDialogPortraitLock.setOnClickListener {
            val dialog = android.app.Dialog(this).apply {
                setTitle("Portrait Locked Dialog")
                setContentView(android.widget.TextView(this@TestLabLauncherActivity).apply {
                    text = "Dialog attempted Window.LayoutParams.screenOrientation = PORTRAIT. Rotate phone to verify override."
                    setPadding(48, 36, 48, 36)
                })
                window?.let { w ->
                    val lp = w.attributes
                    lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    w.attributes = lp
                }
            }
            dialog.show()
        }
        binding.btnRecreateSelf.setOnClickListener {
            recreate()
        }

        updateTelemetry()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("recreation_count", recreationCount)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTelemetry()
    }

    private fun updateTelemetry() {
        val cfg = resources.configuration
        val orientation = when (cfg.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
            Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
            else -> "UNDEFINED (${cfg.orientation})"
        }
        val autoRotate = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val rotLabel = when (rotation) {
            Surface.ROTATION_0 -> "0 deg"
            Surface.ROTATION_90 -> "90 deg"
            Surface.ROTATION_180 -> "180 deg"
            Surface.ROTATION_270 -> "270 deg"
            else -> "$rotation"
        }

        binding.tvTelemetry.text = buildString {
            appendLine("Configuration Orientation: $orientation")
            appendLine("Display Rotation: $rotLabel")
            appendLine("getRequestedOrientation(): ${orientationLabel(requestedOrientation)}")
            appendLine("System Auto Rotate: ${if (autoRotate) "ON" else "OFF"}")
            appendLine("SmallestWidth: ${cfg.smallestScreenWidthDp}dp")
            append("Process Launches: $launchCount | Recreations: $recreationCount")
        }
    }

    private fun orientationLabel(code: Int): String = when (code) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "UNSPECIFIED (-1)"
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "LANDSCAPE (0)"
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "PORTRAIT (1)"
        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> "FULL_SENSOR (10)"
        ActivityInfo.SCREEN_ORIENTATION_FULL_USER -> "FULL_USER (13)"
        else -> "CODE ($code)"
    }
}
