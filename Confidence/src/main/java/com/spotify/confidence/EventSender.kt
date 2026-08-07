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
     * Store a custom event to be tracked with an explicit evaluation context.
     * @param eventContext evaluation context for this event only; does not mutate session context.
     */
    fun track(
        eventName: String,
        data: ConfidenceFieldsType,
        eventContext: Map<String, ConfidenceValue>
    )

    /**
     * Track Android-specific events like activities or Track Context updates.
     * Please note that this method is collecting data in a coroutine scope and will be
     * executed on the dispatcher that was defined with the creation of the Confidence instance.
     *
     * @param producer a producer that produces the events or context updates.
     */
    fun track(producer: Producer)

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
