package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface EventSender {
    fun emit(definition: String, payload: Map<String, String>)
    fun withScope(scope: EventsScope): EventSender
}

interface FlushPolicy {
    fun reset()
    fun hit(event: Event)
    fun shouldFlush(): Boolean
}

data class EventsScope(
    val fields: () -> Map<String, String> = { mapOf() }
)

class EventSenderImpl private constructor(
    private val eventSenderEngine: EventSenderEngine,
    private val scope: EventsScope = EventsScope()
) : EventSender {
    override fun emit(definition: String, payload: Map<String, String>) {
        eventSenderEngine.emit(definition, payload + scope.fields())
    }

    override fun withScope(scope: EventsScope): EventSender {
        val combinedFields = {
            scope.fields() + this.scope.fields()
        }
        return EventSenderImpl(
            eventSenderEngine,
            EventsScope(fields = combinedFields)
        )
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
                flushPolicies,
                dispatcher = dispatcher
            )
            EventSenderImpl(engine, scope).also {
                instance = it
            }
        }
    }
}