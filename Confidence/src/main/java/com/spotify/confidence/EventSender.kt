package com.spotify.confidence

interface EventSender : Contextual {
    /**
     * Store a custom event to be tracked
     * @param eventName name of the event.
     * @param data any additional data that needs to be tracked.
     */
    fun track(
        eventName: String,
        data: ConfidenceFieldsType = mapOf()
    )

    /**
     * Track Android-specific events like activities.
     * @param eventProducer an eventProducer that produces the event, e.g. AndroidLifecycleEventProducer.
     */
    fun track(eventProducer: EventProducer)

    /**
     * Safely stop a Confidence instance
     */
    fun stop()

    /**
     * Manually flush events from storage.
     */
    fun flush()

    override fun withContext(context: Map<String, ConfidenceValue>): EventSender
}

internal interface FlushPolicy {
    fun reset()
    fun hit(event: EngineEvent)
    fun shouldFlush(): Boolean
}