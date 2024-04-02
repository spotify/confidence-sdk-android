package com.spotify.confidence

import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import dev.openfeature.sdk.events.observe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.Result

suspend fun awaitProviderReady(

    eventsHandler: EventHandler,

    dispatcher: CoroutineDispatcher = Dispatchers.IO

) = suspendCancellableCoroutine { continuation ->

    fun observeProviderReady() = eventsHandler
        .observe<OpenFeatureEvents.ProviderReady>()
        .onStart {
            if (eventsHandler.getProviderStatus() == OpenFeatureEvents.ProviderReady) {
                this.emit(OpenFeatureEvents.ProviderReady)
            }
        }

    val coroutineScope = CoroutineScope(dispatcher)

    coroutineScope.launch {
        observeProviderReady()
            .take(1)
            .collect {
                continuation.resumeWith(Result.success(Unit))
            }
    }
}