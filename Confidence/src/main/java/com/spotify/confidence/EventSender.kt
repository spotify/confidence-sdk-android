package com.spotify.confidence

interface EventSender : Contextual {
    fun track(
        eventName: String,
        message: ConfidenceFieldsType = mapOf()
    )

    fun track(eventProducer: EventProducer)

    fun stop()

    fun flush()

    override fun withContext(context: Map<String, ConfidenceValue>): EventSender
}

internal interface FlushPolicy {
    fun reset()
    fun hit(event: EngineEvent)
    fun shouldFlush(): Boolean
}