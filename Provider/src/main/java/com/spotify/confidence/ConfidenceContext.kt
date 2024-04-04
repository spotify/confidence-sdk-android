package com.spotify.confidence

import com.spotify.confidence.client.serializers.JsonAnySerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ConfidenceContextProvider {
    fun getContext(): Map<String, ConfidenceValue>
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface Contextual : ConfidenceContextProvider {
    fun withContext(context: Map<String, ConfidenceValue>): Contextual

    fun putContext(context: Map<String, ConfidenceValue>)
    fun setContext(context: Map<String, ConfidenceValue>)
    fun putContext(key: String, value: ConfidenceValue)
    fun removeContext(key: String)
}

fun Contextual.withContext(context: Map<String, Any>): Contextual {
    val serialized = JsonAnySerializer.encodeToString(context)
    return withContext(Json.decodeFromString(serialized))
}

fun Contextual.putContext(context: Map<String, Any>) {
    val serialized = JsonAnySerializer.encodeToString(context)
    return putContext(Json.decodeFromString(serialized))
}

fun Contextual.putContext(key: String, value: Any) {
    val serialized = JsonAnySerializer.encodeToString(value)
    return putContext(key, Json.decodeFromString(serialized))
}

fun EventSender.withContext(context: Map<String, Any>): EventSender {
    val serialized = JsonAnySerializer.encodeToString(context)
    return withContext(Json.decodeFromString(serialized)) as EventSender
}

fun EventSender.putContext(context: Map<String, Any>) {
    val serialized = JsonAnySerializer.encodeToString(context)
    return putContext(Json.decodeFromString(serialized))
}

fun EventSender.putContext(key: String, value: Any) {
    val serialized = JsonAnySerializer.encodeToString(value)
    return putContext(key, Json.decodeFromString(serialized))
}