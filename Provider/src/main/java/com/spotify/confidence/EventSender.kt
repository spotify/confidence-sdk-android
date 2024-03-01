package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
    private val context: Context,
    private val clientSecret: String,
    private val scope: EventsScope = EventsScope(),
    private val clock: Clock = Clock.CalendarBacked.systemUTC(),
    private val flushPolicies: List<FlushPolicy> = listOf(),
    private val eventStorage: EventStorage = EventStorageImpl(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EventSender {

    private lateinit var uploader: EventSenderUploader

    private val writeReqChannel: Channel<Event> = Channel()
    private val sendChannel: Channel<String> = Channel()
    private val coroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatcher)
    }
    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, _ ->
            // do nothing
        }
    }

    init {
        coroutineScope.launch {
            for (event in writeReqChannel) {
                eventStorage.writeEvent(event)
                flushPolicies.forEach { it.hit(event) }
                val shouldFlush = flushPolicies.any { it.shouldFlush() }
                if (shouldFlush) {
                    flushPolicies.forEach { it.reset() }
                    sendChannel.send(SEND_SIG)
                }
            }
        }

        // upload might throw exceptions
        coroutineScope.launch(exceptionHandler) {
            for (flush in sendChannel) {
                eventStorage.rollover()
                val readyFiles = eventStorage.batchReadyFiles()
                for (readyFile in readyFiles) {
                    val batch = EventBatch(
                        clientSecret = clientSecret,
                        events = eventStorage.eventsFor(readyFile),
                        sendTime = clock.currentTime()
                    )
                    val shouldCleanup = uploader.upload(batch)
                    if (shouldCleanup) {
                        readyFile.delete()
                    }
                }
            }
        }
    }
    override fun emit(definition: String, payload: Map<String, String>) {
        coroutineScope.launch {
            val event = Event(
                eventDefinition = definition,
                eventTime = clock.currentTime(),
                payload = payload
            )
            writeReqChannel.send(event)
        }
    }

    override fun withScope(scope: EventsScope): EventSender {
        val combinedFields = {
            scope.fields() + this.scope.fields()
        }
        return EventSenderImpl(
            context,
            clientSecret,
            EventsScope(fields = combinedFields),
            clock,
            flushPolicies,
            eventStorage,
            dispatcher
        )
    }

    companion object {
        private var instance: EventSender? = null
        fun create(
            context: Context,
            clientSecret: String,
            scope: EventsScope,
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): EventSender = instance ?: run {
            EventSenderImpl(context, clientSecret, scope, dispatcher = dispatcher).also {
                instance = it
            }
        }
        private const val SEND_SIG = "FLUSH"
    }
}