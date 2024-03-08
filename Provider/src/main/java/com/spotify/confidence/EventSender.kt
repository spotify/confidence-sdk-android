package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.io.File

interface EventSender {
    fun emit(
        definition: String,
        payload: ConfidenceFieldsType = mapOf()
    )
    fun onLowMemory(body: (List<File>) -> Unit): EventSender
    fun stop()
}

interface FlushPolicy {
    fun reset()
    fun hit(event: Event)
    fun shouldFlush(): Boolean
}

class EventSenderImpl internal constructor(
    private val eventSenderEngine: EventSenderEngine,
    private val confidenceContext: ConfidenceContextProvider,
    dispatcher: CoroutineDispatcher
) : EventSender {
    private val coroutineScope = CoroutineScope(dispatcher)
    override fun emit(
        definition: String,
        payload: ConfidenceFieldsType
    ) {
        eventSenderEngine.emit(definition, payload, confidenceContext.confidenceContext())
    }

    override fun onLowMemory(body: (List<File>) -> Unit): EventSender {
        coroutineScope.launch {
            eventSenderEngine
                .onLowMemoryChannel()
                .consumeEach {
                    body(it)
                }
        }
        return this
    }

    override fun stop() {
        eventSenderEngine.stop()
    }

    companion object {
        fun create(
            context: Context,
            clientSecret: String,
            flushPolicies: List<FlushPolicy> = listOf(),
            confidenceContext: ConfidenceContextProvider,
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): EventSender {
            val engine = EventSenderEngine.instance(
                context,
                clientSecret,
                flushPolicies = flushPolicies,
                dispatcher = dispatcher
            )
            return EventSenderImpl(engine, confidenceContext, dispatcher)
        }
    }
}