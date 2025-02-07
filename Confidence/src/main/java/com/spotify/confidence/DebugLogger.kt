package com.spotify.confidence

import android.util.Log
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

internal interface DebugLogger {
    fun logEvent(action: String, event: EngineEvent)
    fun logMessage(message: String, isWarning: Boolean = false, throwable: Throwable? = null)
    fun logFlag(action: String, details: String? = null)
    fun logContext(action: String, context: Map<String, ConfidenceValue>)
    fun logResolve(flag: String, context: JsonElement)
    fun logError(message: String, throwable: Throwable? = null)
    companion object {
        const val TAG = "Confidence"
    }
}

internal class DebugLoggerImpl(private val filterLevel: LoggingLevel, private val clientKey: String) : DebugLogger {
    private val JsonElement.urlEncoded
        get() = URLEncoder.encode(this.toString(), "UTF-8")
    private val String.urlEncoded
        get() = URLEncoder.encode(this, "UTF-8")

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

    override fun logFlag(action: String, details: String?) {
        verbose("[$action] $details")
    }

    override fun logContext(action: String, context: Map<String, ConfidenceValue>) {
        verbose("[$action] $context")
    }

    override fun logResolve(flag: String, context: JsonElement) {
        debug(
            "See resolves for $flag in Confidence: " +
                "https://app.confidence.spotify.com/flags/resolver-test?client-key=$clientKey&flag=flags/" +
                "${flag.urlEncoded}&context=${context.urlEncoded}"
        )
    }

    override fun logError(message: String, throwable: Throwable?) {
        error(message, throwable)
    }

    private fun verbose(message: String) = log(LoggingLevel.VERBOSE, message)
    private fun debug(message: String) = log(LoggingLevel.DEBUG, message)
    private fun warn(message: String, throwable: Throwable?) =
        log(LoggingLevel.WARN, throwable?.let { "$message: ${throwable.message}" } ?: message)

    private fun error(message: String, throwable: Throwable?) = log(
        LoggingLevel.ERROR,
        throwable?.let { "$message: ${throwable.message}" } ?: message
    )

    private fun log(messageLevel: LoggingLevel, message: String) {
        if (messageLevel >= filterLevel) {
            when (messageLevel) {
                LoggingLevel.VERBOSE -> Log.v(DebugLogger.TAG, message)
                LoggingLevel.DEBUG -> Log.d(DebugLogger.TAG, message)
                LoggingLevel.WARN -> Log.w(DebugLogger.TAG, message)
                LoggingLevel.ERROR -> Log.e(DebugLogger.TAG, message)
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