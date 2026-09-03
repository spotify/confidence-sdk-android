package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.apply.EventStatus
import com.spotify.confidence.apply.FlagApplierWithRetries
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.FlagApplierClient
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
import java.io.File
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

    @Test
    fun pendingAppliesFromPreviousSessionAreUploadedOnStartup() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = FileDiskStorage(
            flagsFile = File(temporaryFolder.root, "flags.json"),
            applyFile = File(temporaryFolder.root, "applies.json")
        )
        storage.writeApplyData(
            mapOf(
                "resolve-token" to mutableMapOf(
                    "pending-flag" to ApplyInstance(Date(0), EventStatus.SENDING)
                )
            )
        )

        val nextClient = RecordingFlagApplierClient()
        FlagApplierWithRetries(nextClient, dispatcher, storage)
        advanceUntilIdle()

        assertEquals(listOf("pending-flag"), nextClient.appliedFlagNames)
        assertTrue(storage.readApplyData().isEmpty())
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

    private class RecordingFlagApplierClient : FlagApplierClient {
        val appliedFlagNames = mutableListOf<String>()

        override suspend fun apply(flags: List<AppliedFlag>, resolveToken: String): Result<Unit> {
            appliedFlagNames += flags.map(AppliedFlag::flag)
            return Result.Success(Unit)
        }
    }
}
