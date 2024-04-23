package com.spotify.confidence

interface EventSender : Contextual {
    fun send(
        eventName: String,
        message: ConfidenceFieldsType = mapOf()
    )

    override fun withContext(context: Map<String, ConfidenceValue>): EventSender
}

internal interface FlushPolicy {
    fun reset()
    fun hit(event: Event)
    fun shouldFlush(): Boolean
}