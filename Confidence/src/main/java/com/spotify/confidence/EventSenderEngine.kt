package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.Clock
import com.spotify.confidence.client.Sdk
import com.spotify.confidence.client.SdkMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val sdkMetadata: SdkMetadata,
    private val debugLogger: DebugLogger?,
    private val flushIntervalMillis: Long? = null
) : EventSenderEngine {
    // Main used Channel() (rendezvous, capacity 0) with suspending send() inside
    // coroutineScope.launch. stop() only cancelled the scope, so in-flight emits
    // could be lost.
    //
    // emit()/flush() now use trySend() on the caller thread so an event is either
    // queued or rejected before stop() sets isStopped. stop() then closes this
    // channel and joins writeJob to drain every queued event to disk.
    //
    // trySend on a rendezvous channel fails unless the consumer is already waiting,
    // which would silently drop events whenever the writer is busy with disk I/O.
    // UNLIMITED buffering guarantees trySend succeeds for all events accepted
    // before stop(); see stopDrainsQueuedEventsBeforeUploading.
    private val writeReqChannel: Channel<EngineEvent> = Channel(Channel.UNLIMITED)

    // Conflated + trySend so the writer never suspends while signaling flush. A
    // rendezvous sendChannel.send() blocks the write loop during slow uploads
    // (uploadMutex held), preventing writeReqChannel drain before stop() times out.
    // Duplicate flush signals are harmless: uploadReadyBatches processes all
    // ready files per invocation.
    private val sendChannel: Channel<String> = Channel(Channel.CONFLATED)
    private val payloadMerger: PayloadMerger = PayloadMergerImpl(debugLogger)

    // Serializes read-upload-delete of ready files across the flush consumer,
    // the startup retry and stop(), so a batch is never uploaded twice.
    private val uploadMutex = Mutex()
    private val coroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatcher)
    }
    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, e ->
            debugLogger?.logMessage(message = "EventSenderEngine error: $e", isWarning = true)
        }
    }
    private var flushIntervalJob: Job? = null
    private val writeJob: Job

    @Volatile
    private var isStopped = false

    init {
        flushPolicies.add(ManualFlushPolicy)
        writeJob = coroutineScope.launch(exceptionHandler) {
            for (event in writeReqChannel) {
                if (event.eventDefinition != manualFlushEvent.eventDefinition) {
                    eventStorage.writeEvent(event)
                    debugLogger?.logEvent(action = "DiskWrite ", event = event)
                }
                for (policy in flushPolicies) {
                    policy.hit(event)
                }
                val shouldFlush = flushPolicies.any { it.shouldFlush() }
                if (shouldFlush) {
                    for (policy in flushPolicies) {
                        policy.reset()
                        debugLogger?.logMessage(
                            message = "Flush policy $policy triggered to flush. Flushing."
                        )
                    }
                    sendChannel.trySend(SEND_SIG)
                }
            }
        }

        coroutineScope.launch(exceptionHandler) {
            for (flush in sendChannel) {
                uploadReadyBatches(sealCurrentBatch = true)
            }
        }

        if (flushIntervalMillis != null && flushIntervalMillis > 0) {
            flushIntervalJob = coroutineScope.launch(exceptionHandler) {
                while (isActive) {
                    delay(flushIntervalMillis)
                    flush()
                }
            }
        }

        coroutineScope.launch(exceptionHandler) {
            uploadReadyBatches(sealCurrentBatch = false)
        }
    }

    override fun onLowMemoryChannel(): Channel<List<File>> {
        debugLogger?.logMessage(message = "LowMemory", isWarning = true)
        return eventStorage.onLowMemoryChannel()
    }

    override fun emit(
        eventName: String,
        data: ConfidenceFieldsType,
        context: Map<String, ConfidenceValue>
    ) {
        if (isStopped) {
            return
        }
        val payload = payloadMerger(context, data)
        val event = EngineEvent(
            eventDefinition = eventName,
            eventTime = clock.currentTime(),
            payload = payload
        )
        if (writeReqChannel.trySend(event).isSuccess) {
            debugLogger?.logEvent(action = "EmitEvent ", event = event)
        }
    }

    override fun flush() {
        if (isStopped) {
            return
        }
        if (writeReqChannel.trySend(manualFlushEvent).isSuccess) {
            debugLogger?.logEvent(action = "Flush ", event = manualFlushEvent)
        }
    }

    override fun stop() {
        isStopped = true
        flushIntervalJob?.cancel()
        // Best effort: drain queued events to disk and attempt one final upload,
        // bounded so stop() can never block the caller indefinitely. Batches that
        // don't make it are sealed on disk and retried at next startup.
        runBlocking(dispatcher) {
            withTimeoutOrNull(STOP_TIMEOUT_MILLIS) {
                writeReqChannel.close()
                writeJob.join()
                uploadReadyBatches(sealCurrentBatch = true)
            }
        }
        coroutineScope.cancel()
        eventStorage.stop()
        debugLogger?.logMessage(message = "EventSenderEngine closed ")
    }

    private suspend fun uploadReadyBatches(sealCurrentBatch: Boolean) = uploadMutex.withLock {
        if (sealCurrentBatch) {
            eventStorage.rollover()
        }
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
            if (events.isEmpty()) {
                readyFile.delete()
                continue
            }
            val batch = EventBatchRequest(
                clientSecret = clientSecret,
                events = events,
                sendTime = clock.currentTime(),
                sdk = Sdk(sdkMetadata.sdkId, sdkMetadata.sdkVersion)
            )
            try {
                val shouldCleanup = uploader.upload(batch)
                debugLogger?.logMessage(message = "Uploading events")
                if (shouldCleanup) {
                    readyFile.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                debugLogger?.logMessage(
                    message = "Failed to upload events: $e",
                    isWarning = true
                )
            }
        }
    }

    companion object {
        private const val SEND_SIG = "FLUSH"
        private const val STOP_TIMEOUT_MILLIS = 2_000L
        private var Instance: EventSenderEngine? = null
        fun instance(
            context: Context,
            clientSecret: String,
            sdkMetadata: SdkMetadata,
            flushPolicies: List<FlushPolicy> = listOf(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            debugLogger: DebugLogger?,
            flushIntervalMillis: Long? = null
        ): EventSenderEngine {
            return Instance ?: run {
                EventSenderEngineImpl(
                    EventStorageImpl(context),
                    clientSecret,
                    uploader = EventSenderUploaderImpl(OkHttpClient(), dispatcher, debugLogger),
                    flushPolicies = flushPolicies.toMutableList(),
                    dispatcher = dispatcher,
                    sdkMetadata = sdkMetadata,
                    debugLogger = debugLogger,
                    flushIntervalMillis = flushIntervalMillis
                )
            }
        }
    }
}
