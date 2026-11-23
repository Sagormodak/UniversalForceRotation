package com.sagor.universalforcerotation.core

import android.util.Log

/**
 * Abstraction over logging so Xposed hooks route through XposedInterface.log() in production
 * and remain verifiable in JVM unit tests.
 */
interface HookLogger {
    var verbose: Boolean
    fun info(msg: String)
    fun debug(msg: String)
    fun warn(msg: String, tr: Throwable? = null)
    fun error(msg: String, tr: Throwable? = null)
}

class XposedHookLogger(
    private val tag: String = "UniversalForceRotation",
    private val logSink: (priority: Int, tag: String, msg: String, tr: Throwable?) -> Unit,
) : HookLogger {

    @Volatile
    override var verbose: Boolean = false

    override fun info(msg: String) {
        logSink(Log.INFO, tag, msg, null)
    }

    override fun debug(msg: String) {
        if (verbose) {
            logSink(Log.DEBUG, tag, msg, null)
        }
    }

    override fun warn(msg: String, tr: Throwable?) {
        logSink(Log.WARN, tag, msg, tr)
    }

    override fun error(msg: String, tr: Throwable?) {
        logSink(Log.ERROR, tag, msg, tr)
    }
}
