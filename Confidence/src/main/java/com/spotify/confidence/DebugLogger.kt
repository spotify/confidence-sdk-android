package com.spotify.confidence

import android.util.Log

internal class DebugLogger {
    var level: DebugLoggerLevel = DebugLoggerLevel.NONE

    internal fun logEvent(tag: String, event: EngineEvent, details: String) {
        when (level) {
            DebugLoggerLevel.VERBOSE -> Log.v(tag, details + event.toString())
            DebugLoggerLevel.DEBUG -> Log.d(tag, details + event.eventDefinition)
            DebugLoggerLevel.NONE -> {
                // do nothing
            }
        }
    }

    internal fun logMessage(tag: String, message: String, isWarning: Boolean = false) {
        if (!isWarning) {
            when (level) {
                DebugLoggerLevel.VERBOSE, DebugLoggerLevel.DEBUG -> Log.v(tag, message)
                DebugLoggerLevel.NONE -> {
                    // do nothing
                }
            }
        } else {
            Log.w(tag, message)
        }
    }

    internal fun logFlags() {

    }

    internal fun logContext(context: Map<String, ConfidenceValue>) {
        when (level) {
            DebugLoggerLevel.VERBOSE, DebugLoggerLevel.DEBUG -> Log.v("CurrentContext", context.toString())
            DebugLoggerLevel.NONE -> {
                // do nothing
            }
        }
    }
}

enum class DebugLoggerLevel {
    VERBOSE, DEBUG, NONE
}