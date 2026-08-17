package com.spotify.confidence

import com.spotify.confidence.client.SdkMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class EventSenderEngineReliabilityTest {
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var uploader: RecordingEventUploader
    private lateinit var storage: RecordingEventStorage

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        uploader = RecordingEventUploader()
        storage = RecordingEventStorage()
    }

    private fun engine(
        dispatcher: CoroutineDispatcher = testDispatcher,
        flushIntervalMillis: Long? = null
    ) = EventSenderEngineImpl(
        eventStorage = storage,
        clientSecret = "secret",
        uploader = uploader,
        flushPolicies = mutableListOf(),
        dispatcher = dispatcher,
        sdkMetadata = SdkMetadata("id", "1.0"),
        debugLogger = null,
        flushIntervalMillis = flushIntervalMillis
    )

    @Test
    fun startupUploadsPendingReadyBatchesWithoutSealingCurrentBatch() = runTest(testDispatcher) {
        storage.readyEvents["pending.batch"] = listOf(
            EngineEvent("pending", Date(), mapOf())
        )
        storage.currentEvents.add(
            EngineEvent("current", Date(), mapOf())
        )

        engine()

        advanceUntilIdle()

        assertEquals(listOf("pending"), uploader.uploadedEventNames)
        assertEquals(listOf("current"), storage.currentEvents.map { it.eventDefinition })
        assertTrue(storage.readyEvents.isEmpty())
    }

    @Test
    fun stopUploadsCurrentBatch() = runTest(testDispatcher) {
        val engine = engine()

        engine.emit("session-end", mapOf(), mapOf())
        advanceUntilIdle()
        engine.stop()

        assertEquals(listOf("session-end"), uploader.uploadedEventNames)
    }

    @Test
    fun stopDrainsQueuedEventsBeforeUploading() {
        val engine = engine(dispatcher = Dispatchers.IO)

        repeat(50) { engine.emit("event-$it", mapOf(), mapOf()) }
        engine.stop()

        assertEquals(50, uploader.uploadedEventNames.size)
    }

    @Test
    fun stopDrainsAllEventsWhenFlushPolicyBlocksWriter() {
        val slowUploader = SlowEventUploader(uploadDelayMillis = 3_000)
        val batchFlush = object : FlushPolicy {
            private var count = 0
            override fun reset() {
                count = 0
            }
            override fun hit(event: EngineEvent) {
                count++
            }
            override fun shouldFlush(): Boolean = count > 4
        }
        val engine = EventSenderEngineImpl(
            eventStorage = storage,
            clientSecret = "secret",
            uploader = slowUploader,
            flushPolicies = mutableListOf(batchFlush),
            dispatcher = Dispatchers.IO,
            sdkMetadata = SdkMetadata("id", "1.0"),
            debugLogger = null
        )

        repeat(12) { engine.emit("event-$it", mapOf(), mapOf()) }
        Thread.sleep(100)
        engine.stop()

        assertEquals(
            "All 12 events should be written to storage before stop() returns",
            12,
            storage.storedEventCount()
        )
    }

    @Test
    fun emitAfterStopIsIgnored() = runTest(testDispatcher) {
        val engine = engine()

        engine.stop()
        engine.emit("late-event", mapOf(), mapOf())
        advanceUntilIdle()

        assertTrue(uploader.uploadedEventNames.isEmpty())
    }

    @Test
    fun periodicFlushIntervalUploadsEventsExactlyOnce() = runTest(testDispatcher) {
        val engine = engine(flushIntervalMillis = 100)

        engine.emit("interval-event", mapOf(), mapOf())
        testScheduler.runCurrent()
        // advanceUntilIdle would spin forever on the self-rescheduling interval job
        testScheduler.advanceTimeBy(150)
        testScheduler.runCurrent()
        engine.stop()

        assertEquals(listOf("interval-event"), uploader.uploadedEventNames)
    }

    @Test
    fun periodicFlushWithoutEventsDoesNotUpload() = runTest(testDispatcher) {
        val engine = engine(flushIntervalMillis = 100)

        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        engine.stop()

        assertTrue(uploader.uploadedEventNames.isEmpty())
    }

    private class RecordingEventUploader : EventSenderUploader {
        val uploadedEventNames = mutableListOf<String>()

        override suspend fun upload(events: EventBatchRequest): Boolean {
            synchronized(uploadedEventNames) {
                uploadedEventNames.addAll(
                    events.events.map { it.eventDefinition.removePrefix("eventDefinitions/") }
                )
            }
            return true
        }
    }

    // Mimics EventStorageImpl: rollover always seals the current batch (even when
    // empty) and uploaded batches disappear when their file is deleted.
    private class RecordingEventStorage : EventStorage {
        val currentEvents = mutableListOf<EngineEvent>()
        val readyEvents = mutableMapOf<String, List<EngineEvent>>()
        private var batchCounter = 0

        override suspend fun rollover(): Unit = synchronized(this) {
            readyEvents["batch-${batchCounter++}"] = currentEvents.toList()
            currentEvents.clear()
        }

        override suspend fun writeEvent(event: EngineEvent): Unit = synchronized(this) {
            currentEvents.add(event)
        }

        override suspend fun batchReadyFiles(): List<java.io.File> = synchronized(this) {
            readyEvents.keys.map { name ->
                object : java.io.File(name) {
                    override fun delete(): Boolean = synchronized(this@RecordingEventStorage) {
                        readyEvents.remove(name) != null
                    }
                }
            }
        }

        override suspend fun eventsFor(file: java.io.File): List<EngineEvent> = synchronized(this) {
            readyEvents[file.name].orEmpty()
        }

        override fun onLowMemoryChannel() = kotlinx.coroutines.channels.Channel<List<java.io.File>>()

        override fun stop() {
        }

        fun storedEventCount(): Int = synchronized(this) {
            currentEvents.size + readyEvents.values.sumOf { it.size }
        }
    }

    private class SlowEventUploader(
        private val uploadDelayMillis: Long
    ) : EventSenderUploader {
        override suspend fun upload(events: EventBatchRequest): Boolean {
            delay(uploadDelayMillis)
            return true
        }
    }
}
