package com.spotify.confidence

import kotlinx.coroutines.flow.Flow

data class Event(
    val name: String,
    val data: Map<String, ConfidenceValue>,
    val shouldFlush: Boolean = false
)

interface EventProducer {
    fun events(): Flow<Event>
    fun contextChanges(): Flow<Map<String, ConfidenceValue>>

    fun stop()
}