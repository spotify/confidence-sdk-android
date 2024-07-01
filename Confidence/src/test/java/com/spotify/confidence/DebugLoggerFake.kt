package com.spotify.confidence

internal open class DebugLoggerFake : DebugLogger {
    val messagesLogged = mutableListOf<Msg>()

    override fun logEvent(action: String, event: EngineEvent) {
        // not important enough to test right now
    }

    override fun logMessage(message: String, isWarning: Boolean, throwable: Throwable?) {
        messagesLogged.add(Msg(message, isWarning, throwable))
    }

    override fun logFlag(action: String, flag: String?) {
        // not important enough to test right now
    }

    override fun logContext(action: String, context: Map<String, ConfidenceValue>) {
        // not important enough to test right now
    }

    data class Msg(val message: String, val isWarning: Boolean, val throwable: Throwable?)
}