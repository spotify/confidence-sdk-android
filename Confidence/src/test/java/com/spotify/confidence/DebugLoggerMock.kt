package com.spotify.confidence

internal open class DebugLoggerMock : DebugLogger {
    var eventsLogged = 0
    var messagesLogged = 0
    var flagsLogged = 0
    var contextLogs = 0
    override fun logEvent(tag: String, event: EngineEvent, details: String) {
        eventsLogged++
    }

    override fun logMessage(tag: String, message: String, isWarning: Boolean) {
        messagesLogged++
    }

    override fun logFlags(tag: String, flag: String) {
        flagsLogged++
    }

    override fun logContext(context: Map<String, ConfidenceValue>) {
        contextLogs++
    }
}