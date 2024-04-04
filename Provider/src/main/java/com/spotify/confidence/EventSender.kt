package com.spotify.confidence

import com.spotify.confidence.client.serializers.JsonAnySerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface EventSender : Contextual {
    fun send(
        definition: String,
        payload: ConfidenceFieldsType = mapOf()
    )

    fun onLowMemory(body: (List<File>) -> Unit): Contextual
}

interface FlushPolicy {
    fun reset()
    fun hit(event: Event)
    fun shouldFlush(): Boolean
}

inline fun <reified T> EventSender.send(
    definition: String,
    payload: T,
    serializationStrategy: SerializationStrategy<T>? = null
) {
    val serializedT = serializationStrategy?.let { Json.encodeToString(it, payload) }
        ?: JsonAnySerializer.encodeToString(payload)
    send(definition, Json.decodeFromString(serializedT))
}

inline fun <reified T> EventSender.send(
    definition: String,
    payload: T
) {
    send(definition, payload, null)
}