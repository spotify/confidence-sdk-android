package com.spotify.confidence

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var testDispatcher: UnconfinedTestDispatcher
    private lateinit var uploader: RecordingEventUploader
    private lateinit var storage: RecordingEventStorage

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        uploader = RecordingEventUploader()
        storage = RecordingEventStorage()
    }

    @Test
    fun startupUploadsPendingReadyBatchesWithoutSealingCurrentBatch() = runTest(testDispatcher) {
        storage.readyEvents["pending.batch"] = listOf(
            EngineEvent("pending", Date(), mapOf())
        )
        storage.currentEvents.add(
            EngineEvent("current", Date(), mapOf())
        )

        EventSenderEngineImpl(
            eventStorage = storage,
            clientSecret = "secret",
            uploader = uploader,
            flushPolicies = mutableListOf(),
            dispatcher = testDispatcher,
            sdkMetadata = com.spotify.confidence.client.SdkMetadata("id", "1.0"),
            debugLogger = null
        )

        advanceUntilIdle()

        assertEquals(listOf("pending"), uploader.uploadedEventNames)
        assertEquals(listOf("current"), storage.currentEvents.map { it.eventDefinition })
    }

    @Test
    fun stopUploadsCurrentBatch() = runTest(testDispatcher) {
        val engine = EventSenderEngineImpl(
            eventStorage = storage,
            clientSecret = "secret",
            uploader = uploader,
            flushPolicies = mutableListOf(),
            dispatcher = testDispatcher,
            sdkMetadata = com.spotify.confidence.client.SdkMetadata("id", "1.0"),
            debugLogger = null
        )

        engine.emit("session-end", mapOf(), mapOf())
        advanceUntilIdle()
        engine.stop()

        assertTrue(uploader.uploadedEventNames.contains("session-end"))
    }

    @Test
    fun periodicFlushIntervalUploadsEvents() = runTest(testDispatcher) {
        val engine = EventSenderEngineImpl(
            eventStorage = storage,
            clientSecret = "secret",
            uploader = uploader,
            flushPolicies = mutableListOf(),
            dispatcher = testDispatcher,
            sdkMetadata = com.spotify.confidence.client.SdkMetadata("id", "1.0"),
            debugLogger = null,
            flushIntervalMillis = 100
        )

        engine.emit("interval-event", mapOf(), mapOf())
        advanceUntilIdle()
        testScheduler.advanceTimeBy(150)
        advanceUntilIdle()
        engine.stop()

        assertTrue(uploader.uploadedEventNames.contains("interval-event"))
    }

    private class RecordingEventUploader : EventSenderUploader {
        val uploadedEventNames = mutableListOf<String>()

        override suspend fun upload(events: EventBatchRequest): Boolean {
            uploadedEventNames.addAll(events.events.map { it.eventDefinition.removePrefix("eventDefinitions/") })
            return true
        }
    }

    private class RecordingEventStorage : EventStorage {
        val currentEvents = mutableListOf<EngineEvent>()
        val readyEvents = mutableMapOf<String, List<EngineEvent>>()

        override suspend fun rollover() {
            if (currentEvents.isNotEmpty()) {
                readyEvents["batch-${readyEvents.size}"] = currentEvents.toList()
                currentEvents.clear()
            }
        }

        override suspend fun writeEvent(event: EngineEvent) {
            currentEvents.add(event)
        }

        override suspend fun batchReadyFiles(): List<java.io.File> {
            return readyEvents.keys.map { java.io.File(it) }
        }

        override suspend fun eventsFor(file: java.io.File): List<EngineEvent> {
            return readyEvents[file.name].orEmpty()
        }

        override fun onLowMemoryChannel() = kotlinx.coroutines.channels.Channel<List<java.io.File>>()

        override fun stop() {
        }
    }
}
