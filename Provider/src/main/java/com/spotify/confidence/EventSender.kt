package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

interface EventSender {
    fun emit(definition: String, payload: EventPayloadType = mapOf())
    fun withScope(scope: EventsScope): EventSender
    fun onLowMemory(body: (List<File>) -> Unit): EventSender
    fun stop()
}

interface FlushPolicy {
    fun reset()
    fun hit(event: Event)
    fun shouldFlush(): Boolean
}

sealed class EventValue {
    data class String(val value: kotlin.String) : EventValue()
    data class Double(val value: kotlin.Double) : EventValue()
    data class Boolean(val value: kotlin.Boolean) : EventValue()
    data class Int(val value: kotlin.Int) : EventValue()
    data class Struct(val value: Map<kotlin.String, EventValue>) : EventValue()
}

typealias EventPayloadType = Map<String, EventValue>

data class EventsScope(
    val fields: () -> EventPayloadType = { mapOf() }
)

class EventSenderImpl internal constructor(
    private val eventSenderEngine: EventSenderEngine,
    private val dispatcher: CoroutineDispatcher,
    private val scope: EventsScope = EventsScope()
) : EventSender {
    private val coroutineScope = CoroutineScope(dispatcher)
    override fun emit(definition: String, payload: EventPayloadType) {
        val scope = scope.fields()
        eventSenderEngine.emit(definition, payload + scope)
    }

    override fun withScope(scope: EventsScope): EventSender {
        val combinedFields = {
            scope.fields() + this.scope.fields()
        }
        return EventSenderImpl(
            eventSenderEngine,
            dispatcher,
            EventsScope(fields = combinedFields)
        )
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
        instance = null
    }

    companion object {
        private var instance: EventSender? = null
        fun create(
            context: Context,
            clientSecret: String,
            scope: EventsScope,
            flushPolicies: List<FlushPolicy> = listOf(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): EventSender = instance ?: run {
            val engine = EventSenderEngine(
                EventStorageImpl(context),
                clientSecret,
                uploader = EventSenderUploaderImpl(OkHttpClient(), dispatcher),
                flushPolicies = flushPolicies,
                dispatcher = dispatcher
            )
            EventSenderImpl(engine, dispatcher, scope).also {
                instance = it
            }
        }
    }
}