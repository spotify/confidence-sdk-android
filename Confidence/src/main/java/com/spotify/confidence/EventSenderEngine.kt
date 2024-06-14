package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.Clock
import com.spotify.confidence.client.Sdk
import com.spotify.confidence.client.SdkMetadata
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

internal interface EventSenderEngine {
    fun onLowMemoryChannel(): Channel<List<File>>
    fun emit(eventName: String, data: ConfidenceFieldsType, context: Map<String, ConfidenceValue>)
    fun flush()
    fun stop()
}

internal class EventSenderEngineImpl(
    private val eventStorage: EventStorage,
    private val clientSecret: String,
    private val uploader: EventSenderUploader,
    private val flushPolicies: MutableList<FlushPolicy> = mutableListOf(),
    private val clock: Clock = Clock.CalendarBacked.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private var sdkMetadata: SdkMetadata
) : EventSenderEngine {
    private val writeReqChannel: Channel<EngineEvent> = Channel()
    private val sendChannel: Channel<String> = Channel()
    private val payloadMerger: PayloadMerger = PayloadMergerImpl()
    private val coroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatcher)
    }
    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, e ->
            print(e.message)
        }
    }

    init {
        flushPolicies.add(ManualFlushPolicy)
        coroutineScope.launch(exceptionHandler) {
            for (event in writeReqChannel) {
                if (event.eventDefinition != manualFlushEvent.eventDefinition) {
                    // skip storing manual flush event
                    eventStorage.writeEvent(event)
                }
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
                    val events = eventStorage.eventsFor(readyFile)
                        .map { e ->
                            EngineEvent(
                                "eventDefinitions/${e.eventDefinition}",
                                e.eventTime,
                                e.payload
                            )
                        }
                    val batch = EventBatchRequest(
                        clientSecret = clientSecret,
                        events = events,
                        sendTime = clock.currentTime(),
                        sdk = Sdk(sdkMetadata.sdkId, sdkMetadata.sdkVersion)
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

    fun setSdk(sdk: SdkMetadata) {
        sdkMetadata = sdk
    }

    override fun onLowMemoryChannel(): Channel<List<File>> {
        return eventStorage.onLowMemoryChannel()
    }
    override fun emit(
        eventName: String,
        data: ConfidenceFieldsType,
        context: Map<String, ConfidenceValue>
    ) {
        coroutineScope.launch {
            val payload = payloadMerger(context, data)
            val event = EngineEvent(
                eventDefinition = eventName,
                eventTime = clock.currentTime(),
                payload = payload
            )
            writeReqChannel.send(event)
        }
    }

    override fun flush() {
        coroutineScope.launch {
            writeReqChannel.send(manualFlushEvent)
        }
    }

    override fun stop() {
        coroutineScope.cancel()
        eventStorage.stop()
    }

    companion object {
        private const val SEND_SIG = "FLUSH"
        private var Instance: EventSenderEngine? = null
        fun instance(
            context: Context,
            clientSecret: String,
            sdkMetadata: SdkMetadata,
            flushPolicies: List<FlushPolicy> = listOf(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): EventSenderEngine {
            return Instance ?: run {
                EventSenderEngineImpl(
                    EventStorageImpl(context),
                    clientSecret,
                    uploader = EventSenderUploaderImpl(OkHttpClient(), dispatcher),
                    flushPolicies = flushPolicies.toMutableList(),
                    dispatcher = dispatcher,
                    sdkMetadata = sdkMetadata
                )
            }
        }
    }
}