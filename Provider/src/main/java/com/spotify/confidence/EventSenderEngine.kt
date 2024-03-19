package com.spotify.confidence

import android.content.Context
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
import java.io.File

internal class EventSenderEngine(
    private val eventStorage: EventStorage,
    private val clientSecret: String,
    private val uploader: EventSenderUploader,
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
        CoroutineExceptionHandler { _, e ->
            print(e.message)
        }
    }

    init {
        coroutineScope.launch(exceptionHandler) {
            for (event in writeReqChannel) {
                eventStorage.writeEvent(event)
                for (policy in flushPolicies) {
                    policy.hit(event)
                }
                val shouldFlush = flushPolicies.any { it.shouldFlush() }
                if (shouldFlush) {
                    for (policy in flushPolicies) {
                        policy.reset()
                    }
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
                    runCatching {
                        val shouldCleanup = uploader.upload(batch)
                        if (shouldCleanup) {
                            readyFile.delete()
                        }
                    }
                }
            }
        }
    }

    fun onLowMemoryChannel(): Channel<List<File>> {
        return eventStorage.onLowMemoryChannel()
    }
    fun emit(definition: String, payload: ConfidenceFieldsType, context: Map<String, ConfidenceValue>) {
        coroutineScope.launch {
            val event = Event(
                eventDefinition = definition,
                eventTime = clock.currentTime(),
                payload = payload,
                context = context
            )
            writeReqChannel.send(event)
        }
    }

    fun stop() {
        coroutineScope.cancel()
        eventStorage.stop()
    }

    companion object {
        private const val SEND_SIG = "FLUSH"
        private var Instance: EventSenderEngine? = null
        fun instance(
            context: Context,
            clientSecret: String,
            flushPolicies: List<FlushPolicy> = listOf(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): EventSenderEngine {
            return Instance ?: run {
                EventSenderEngine(
                    EventStorageImpl(context),
                    clientSecret,
                    uploader = EventSenderUploaderImpl(OkHttpClient(), dispatcher),
                    flushPolicies = flushPolicies,
                    dispatcher = dispatcher
                )
            }
        }
    }
}