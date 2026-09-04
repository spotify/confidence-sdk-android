package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.SdkMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class PersistedDataRetryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pendingEventsFromPreviousSessionAreUploadedOnStartup() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val context: Context = mock()
        val eventsDirectory = temporaryFolder.newFolder("events")
        whenever(context.getDir("events", Context.MODE_PRIVATE)).thenReturn(eventsDirectory)
        val persistedStorage = EventStorageImpl(context, dispatcher)
        persistedStorage.writeEvent(
            EngineEvent("pending-event", Date(0), mapOf())
        )
        persistedStorage.rollover()
        persistedStorage.stop()

        assertTrue(
            eventsDirectory.listFiles().orEmpty().any {
                it.name.endsWith(EventStorageImpl.READY_TO_SENT_EXTENSION)
            }
        )

        val nextUploader = RecordingEventUploader()
        eventEngine(
            storage = EventStorageImpl(context, dispatcher),
            uploader = nextUploader,
            dispatcher = dispatcher
        )
        advanceUntilIdle()

        assertEquals(listOf("pending-event"), nextUploader.uploadedEventNames)
        assertTrue(
            eventsDirectory.listFiles().orEmpty().none {
                it.name.endsWith(EventStorageImpl.READY_TO_SENT_EXTENSION)
            }
        )
    }

    private fun eventEngine(
        storage: EventStorage,
        uploader: EventSenderUploader,
        dispatcher: CoroutineDispatcher
    ) = EventSenderEngineImpl(
        eventStorage = storage,
        clientSecret = "secret",
        uploader = uploader,
        dispatcher = dispatcher,
        sdkMetadata = SdkMetadata("e2e-test", "1.0"),
        debugLogger = null
    )

    private class RecordingEventUploader : EventSenderUploader {
        val uploadedEventNames = mutableListOf<String>()

        override suspend fun upload(events: EventBatchRequest): Boolean {
            uploadedEventNames += events.events.map {
                it.eventDefinition.removePrefix("eventDefinitions/")
            }
            return true
        }
    }
}
