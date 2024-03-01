package com.spotify.confidence

import com.spotify.confidence.client.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal class EventSenderEngine(
    private val eventStorage: EventStorage,
    private val clientSecret: String,
    private val flushPolicies: List<FlushPolicy> = listOf(),
    private val clock: Clock = Clock.CalendarBacked.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
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
    private val uploader: EventSenderUploader =
        EventSenderUploaderImpl(OkHttpClient(), dispatcher)

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
    fun emit(definition: String, payload: Map<String, String>) {
        coroutineScope.launch {
            val event = Event(
                eventDefinition = definition,
                eventTime = clock.currentTime(),
                payload = payload
            )
            writeReqChannel.send(event)
        }
    }

    fun stop() {
        coroutineScope.cancel()
    }

    companion object {
        private const val SEND_SIG = "FLUSH"
    }
}