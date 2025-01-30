package com.spotify.confidence

import kotlinx.coroutines.flow.Flow

sealed interface Update {
    data class Event(
        val name: String,
        val data: Map<String, ConfidenceValue>,
        val shouldFlush: Boolean = false
    ) : Update

    data class ContextUpdate(val context: Map<String, ConfidenceValue>) : Update
}

/**
 * A producer is a class that can produce updates to the confidence system.
 * Currently, the only supported updates are a context update or an event update where an Event will be
 * sent using the event sender engine.
 */
interface Producer {
    fun stop()
    fun updates(): Flow<Update>
}