package com.spotify.confidence

import android.util.Log

internal interface DebugLogger {
    fun logEvent(tag: String, event: EngineEvent, details: String)
    fun logMessage(tag: String, message: String, isWarning: Boolean = false)
    fun logFlags(tag: String, flag: String)
    fun logContext(context: Map<String, ConfidenceValue>)
}

internal class DebugLoggerImpl(private val level: DebugLoggerLevel) : DebugLogger {
    override fun logEvent(tag: String, event: EngineEvent, details: String) {
        log(tag, details + event.toString())
    }

    override fun logMessage(tag: String, message: String, isWarning: Boolean) {
        if (!isWarning) {
            log(tag, message)
        } else {
            Log.w(tag, message)
        }
    }

    override fun logFlags(tag: String, flag: String) {
        log(tag, flag)
    }

    override fun logContext(context: Map<String, ConfidenceValue>) {
        log("CurrentContext", context.toString())
    }

    private fun log(tag: String, message: String) {
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