package com.spotify.confidence

internal open class DebugLoggerFake : DebugLogger {
    var messagesLogged = 0

    override fun logEvent(action: String, event: EngineEvent) {
        // not important enough to test right now
    }

    override fun logMessage(message: String, isWarning: Boolean, throwable: Throwable?) {
        messagesLogged++
    }

    override fun logFlag(action: String, flag: String?) {
        // not important enough to test right now
    }

    override fun logContext(action: String, context: Map<String, ConfidenceValue>) {
        // not important enough to test right now
    }
}