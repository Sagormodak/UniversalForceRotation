package com.sagor.universalforcerotation.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory-bounded cache loading application icons off the UI thread.
 */
object AppIconCache {

    private val cache = LruCache<String, Drawable>(160)

    fun getCached(packageName: String): Drawable? = cache.get(packageName)

    suspend fun load(pm: PackageManager, packageName: String): Drawable? {
        cache.get(packageName)?.let { return it }
        val icon = withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        }
        if (icon != null) {
            cache.put(packageName, icon)
        }
        return icon
    }
}
