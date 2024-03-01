package com.spotify.confidence

import android.content.Context
import dev.openfeature.sdk.OpenFeatureAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun ConfidenceFeatureProvider.eventSender(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): EventSender = EventSenderImpl.create(
    clientSecret = this.clientSecret(),
    dispatcher = dispatcher,
    scope = EventsScope(
        fields = {
            val evalContext = OpenFeatureAPI.getEvaluationContext()
                ?.asMap()
                ?.mapValues { Json.encodeToString(it.value) }
            evalContext ?: mapOf()
        }
    ),
    context = context
)