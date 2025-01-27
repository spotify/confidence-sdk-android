package com.spotify.confidence

import kotlinx.coroutines.flow.Flow

data class Event(
    val name: String,
    val data: Map<String, ConfidenceValue>,
    val shouldFlush: Boolean = false
)

sealed interface Producer {
    fun stop()
}

interface EventProducer : Producer {
    fun events(): Flow<Event>
}

interface ContextProducer : Producer {
    fun contextChanges(): Flow<Map<String, ConfidenceValue>>
}