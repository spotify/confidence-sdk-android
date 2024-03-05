package com.spotify.confidence

import android.content.Context
import dev.openfeature.sdk.OpenFeatureAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val confidenceFlushPolicy = object : FlushPolicy {
    private var size = 0
    override fun reset() {
        size = 0
    }

    override fun hit(event: Event) {
        size++
    }

    override fun shouldFlush(): Boolean {
        return size > 4
    }
}

fun ConfidenceFeatureProvider.eventSender(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): EventSender = EventSenderImpl.create(
    clientSecret = this.clientSecret(),
    dispatcher = dispatcher,
    flushPolicies = listOf(confidenceFlushPolicy),
    scope = EventsScope(
        fields = {
            OpenFeatureAPI.getEvaluationContext()?.let { evalContext ->
                val map = mutableMapOf<String, EventValue>()
                map["targeting_key"] = EventValue.String(evalContext.getTargetingKey())
                evalContext.asMap().forEach {
                    map[it.key] = EventValue.String(Json.encodeToString(it.value))
                }
                map
            } ?: mapOf()
        }
    ),
    context = context
).onLowMemory { files ->
    val sortedFiles = files.sortedBy { it.lastModified() }
    sortedFiles.take(10).forEach { it.delete() }
}