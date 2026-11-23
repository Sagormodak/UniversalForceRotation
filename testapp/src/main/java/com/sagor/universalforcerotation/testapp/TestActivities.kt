package com.sagor.universalforcerotation.testapp

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class ManifestPortraitActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Manifest Portrait Lock (Never calls setRequestedOrientation)"
    override val nextActivityClass: Class<out AppCompatActivity> = ManifestLandscapeActivity::class.java
}

class ManifestLandscapeActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Manifest Landscape Lock"
    override val nextActivityClass: Class<out AppCompatActivity> = RuntimePortraitActivity::class.java
}

class RuntimePortraitActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Runtime Portrait Lock (onCreate + onResume)"
    override val nextActivityClass: Class<out AppCompatActivity> = RuntimeLandscapeActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        renderTelemetry()
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        renderTelemetry()
    }
}

class RuntimeLandscapeActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Runtime Landscape Lock (onCreate + onResume)"
    override val nextActivityClass: Class<out AppCompatActivity> = AggressivePortraitActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        renderTelemetry()
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        renderTelemetry()
    }
}

class AggressivePortraitActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Aggressive Portrait Lock (Calls PORTRAIT every 500ms)"
    override val nextActivityClass: Class<out AppCompatActivity> = WindowLayoutLockActivity::class.java

    private var attempts = 0
    private val handler = Handler(Looper.getMainLooper())
    private val spammer = object : Runnable {
        override fun run() {
            attempts++
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            renderTelemetry()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(spammer)
    }

    override fun onPause() {
        handler.removeCallbacks(spammer)
        super.onPause()
    }

    override fun extraTelemetry(): String = "Portrait lock request count: $attempts"
}

class WindowLayoutLockActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Window.LayoutParams Portrait Lock"
    override val nextActivityClass: Class<out AppCompatActivity> = TranslucentDialogActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lp = window.attributes
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.attributes = lp
        renderTelemetry()
    }
}

class TranslucentDialogActivity : BaseOrientationTestActivity() {
    override val probeTitle = "Translucent Dialog Activity (Crash-Safety Check)"
    override val nextActivityClass: Class<out AppCompatActivity>? = null
}
