package com.sagor.universalforcerotation

import android.content.SharedPreferences
import com.sagor.universalforcerotation.core.ConfigSerializer
import com.sagor.universalforcerotation.model.AppOverride
import com.sagor.universalforcerotation.model.OrientationMode
import com.sagor.universalforcerotation.model.RotationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSerializerTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private var clear = false
            private val removals = hashSetOf<String>()
            private val puts = linkedMapOf<String, Any?>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                if (value == null) remove(key) else { removals.remove(key); puts[key] = value }
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (values == null) remove(key) else { removals.remove(key); puts[key] = values }
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { removals.remove(key); puts[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { removals.remove(key); puts[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { removals.remove(key); puts[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { removals.remove(key); puts[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { puts.remove(key); removals.add(key) }
            override fun clear(): SharedPreferences.Editor = apply { clear = true; puts.clear(); removals.clear() }
            override fun commit(): Boolean {
                if (clear) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(puts)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }

    @Test
    fun `empty preferences yield default Follow Auto Rotate config`() {
        val prefs = InMemorySharedPreferences()
        val cfg = ConfigSerializer.readFrom(prefs)

        assertTrue(cfg.masterEnabled)
        assertTrue(cfg.globalForceEnabled)
        assertEquals(OrientationMode.FOLLOW_AUTO_ROTATE, cfg.globalMode)
        assertTrue(cfg.excludeSystemAppsByDefault)
        assertFalse(cfg.verboseLogging)
        assertTrue(cfg.appOverrides.isEmpty())
    }

    @Test
    fun `writeFull and readFrom preserve globals and per-app overrides across restart`() {
        val prefs = InMemorySharedPreferences()
        val original = RotationConfig(
            masterEnabled = true,
            globalForceEnabled = false,
            globalMode = OrientationMode.FULL_SENSOR,
            excludeSystemAppsByDefault = false,
            verboseLogging = true,
            revision = 42L,
            appOverrides = mapOf(
                "com.example.excluded" to AppOverride(enabled = false, mode = null),
                "com.example.landscape" to AppOverride(enabled = true, mode = OrientationMode.FORCE_LANDSCAPE),
                "com.example.rev" to AppOverride(enabled = true, mode = OrientationMode.REVERSE_PORTRAIT),
            ),
        )

        prefs.edit().also { ConfigSerializer.writeFull(it, original) }.commit()
        val restored = ConfigSerializer.readFrom(prefs)

        assertEquals(original, restored)
    }

    @Test
    fun `writeAppOverride updates and removes single package cleanly`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().also {
            ConfigSerializer.writeAppOverride(it, "com.example.a", AppOverride(false, OrientationMode.FORCE_PORTRAIT), 1L)
        }.commit()

        var cfg = ConfigSerializer.readFrom(prefs)
        assertEquals(AppOverride(false, OrientationMode.FORCE_PORTRAIT), cfg.appOverrides["com.example.a"])
        assertEquals(1L, cfg.revision)

        prefs.edit().also {
            ConfigSerializer.writeAppOverride(it, "com.example.a", null, 2L)
        }.commit()

        cfg = ConfigSerializer.readFrom(prefs)
        assertNull(cfg.appOverrides["com.example.a"])
        assertEquals(2L, cfg.revision)
    }

    @Test
    fun `corrupt global mode falls back safely to FOLLOW_AUTO_ROTATE`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putString(ConfigSerializer.KEY_GLOBAL_MODE, "invalid_mode_name")
            .putString("${ConfigSerializer.PREFIX_APP_MODE}com.example.x", "bad_mode")
            .putBoolean("${ConfigSerializer.PREFIX_APP_ENABLED}com.example.x", true)
            .commit()

        val cfg = ConfigSerializer.readFrom(prefs)
        assertEquals(OrientationMode.FOLLOW_AUTO_ROTATE, cfg.globalMode)
        assertNull(cfg.appOverrides["com.example.x"]?.mode)
    }
}
