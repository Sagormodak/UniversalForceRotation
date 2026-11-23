package com.sagor.universalforcerotation.testapp

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.sagor.universalforcerotation.testapp.databinding.ActivityOrientationProbeBinding

abstract class BaseOrientationTestActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityOrientationProbeBinding
    protected open val probeTitle: String = ""
    protected open val nextActivityClass: Class<out AppCompatActivity>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrientationProbeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvProbeTitle.text = probeTitle

        val next = nextActivityClass
        binding.btnSwitchNext.isEnabled = next != null
        binding.btnSwitchNext.setOnClickListener {
            if (next != null) {
                startActivity(Intent(this, next))
            }
        }
        binding.btnCloseProbe.setOnClickListener { finish() }
        renderTelemetry()
    }

    override fun onResume() {
        super.onResume()
        renderTelemetry()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        renderTelemetry()
    }

    protected open fun extraTelemetry(): String = ""

    protected fun renderTelemetry() {
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
        binding.tvProbeDetails.text = buildString {
            appendLine("UI Orientation: $orientation")
            appendLine("Display Rotation: $rotation")
            appendLine("getRequestedOrientation(): ${orientationLabel(requestedOrientation)}")
            appendLine("Window LayoutParams.screenOrientation: ${orientationLabel(window.attributes.screenOrientation)}")
            append("Auto Rotate: ${if (autoRotate) "ON" else "OFF"}")
            val extra = extraTelemetry()
            if (extra.isNotBlank()) {
                appendLine()
                append(extra)
            }
        }
    }

    private fun orientationLabel(code: Int): String = when (code) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "UNSPECIFIED (-1)"
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "LANDSCAPE (0)"
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "PORTRAIT (1)"
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE (8)"
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "REVERSE_PORTRAIT (9)"
        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> "FULL_SENSOR (10)"
        ActivityInfo.SCREEN_ORIENTATION_FULL_USER -> "FULL_USER (13)"
        else -> "CODE ($code)"
    }
}
