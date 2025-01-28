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

sealed interface Producer {
    fun stop()
    fun updates(): Flow<Update>
}

interface EventProducer : Producer

interface ContextProducer : Producer