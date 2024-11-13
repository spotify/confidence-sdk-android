package com.spotify.confidence

import android.util.Log

private const val TAG = "Confidence"

internal interface DebugLogger {
    fun logEvent(action: String, event: EngineEvent)
    fun logMessage(message: String, isWarning: Boolean = false, throwable: Throwable? = null)
    fun logFlag(action: String, flag: String? = null)
    fun logContext(action: String, context: Map<String, ConfidenceValue>)
    fun logResolve(flag: String, context: Map<String, ConfidenceValue>)
}

internal class DebugLoggerImpl(private val filterLevel: LoggingLevel) : DebugLogger {

    override fun logEvent(action: String, event: EngineEvent) {
        debug("[$action] $event")
    }

    override fun logMessage(message: String, isWarning: Boolean, throwable: Throwable?) {
        if (isWarning) {
            warn(message, throwable)
        } else if (throwable != null) {
            error(message, throwable)
        } else {
            debug(message)
        }
    }

    override fun logFlag(action: String, flag: String?) {
        verbose("[$action] $flag")
    }

    override fun logContext(action: String, context: Map<String, ConfidenceValue>) {
        verbose("[$action] $context")
    }

    override fun logResolve(flag: String, context: Map<String, ConfidenceValue>) {
        val trimContext = "$context".replace(" ", "")
        debug("[Resolve Debug] https://app.confidence.spotify.com/flags/resolver-test?flag=flags/$flag&context=$trimContext")
    }

    private fun verbose(message: String) = log(LoggingLevel.VERBOSE, message)
    private fun debug(message: String) = log(LoggingLevel.DEBUG, message)
    private fun warn(message: String, throwable: Throwable?) =
        log(LoggingLevel.WARN, throwable?.let { "$message: ${throwable.message}" } ?: message)

    private fun error(message: String, throwable: Throwable) = log(LoggingLevel.ERROR, "$message: ${throwable.message}")

    private fun log(messageLevel: LoggingLevel, message: String) {
        if (messageLevel >= filterLevel) {
            when (messageLevel) {
                LoggingLevel.VERBOSE -> Log.v(TAG, message)
                LoggingLevel.DEBUG -> Log.d(TAG, message)
                LoggingLevel.WARN -> Log.w(TAG, message)
                LoggingLevel.ERROR -> Log.e(TAG, message)
                LoggingLevel.NONE -> {
                    // do nothing
                }
            }
        }
    }
}

enum class LoggingLevel {
    VERBOSE, // 0
    DEBUG, // 1
    WARN, // 2
    ERROR, // 3
    NONE // 4
}