package com.spotify.confidence

import android.util.Log

internal class DebugLogger(private val level: DebugLoggerLevel) {

    internal fun logEvent(tag: String, event: EngineEvent, details: String) {
        log(tag, details + event.toString())
    }

    internal fun logMessage(tag: String, message: String, isWarning: Boolean = false) {
        if (!isWarning) {
            log(tag, message)
        } else {
            Log.w(tag, message)
        }
    }

    internal fun logFlags(tag: String, flag: String) {
        log(tag, flag)
    }

    internal fun logContext(context: Map<String, ConfidenceValue>) {
        log("CurrentContext", context.toString())
    }

    private fun log(tag: String,message: String) {
        when (level) {
            DebugLoggerLevel.VERBOSE -> Log.v(tag, message)
            DebugLoggerLevel.DEBUG -> Log.d(tag, message)
            DebugLoggerLevel.NONE -> {
                // do nothing
            }
        }
    }
}

enum class DebugLoggerLevel {
    VERBOSE, DEBUG, NONE
}