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
                val map = mutableMapOf<String, String>()
                map["targeting_key"] = evalContext.getTargetingKey()
                evalContext.asMap().forEach {
                    map[it.key] = Json.encodeToString(it.value)
                }
                map
            } ?: mapOf()
        }
    ),
    context = context
)