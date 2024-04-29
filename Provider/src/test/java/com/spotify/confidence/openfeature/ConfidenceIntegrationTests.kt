package com.spotify.confidence.openfeature

import android.content.Context
import com.spotify.confidence.Confidence
import com.spotify.confidence.ConfidenceFactory
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.UUID

private const val clientSecret = "21wxcxXpU6tKBRFtEFTXYiH7nDqL86Mm"
private val mockContext: Context = mock()

class ConfidenceIntegrationTests {

    @get:Rule
    var tmpFile = TemporaryFolder()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
        whenever(mockContext.getDir(any(), any())).thenReturn(Files.createTempDirectory("events").toFile())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun confidenceContextRemovedWorks() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            eventHandler = eventHandler,
            confidence = mockConfidence,
            dispatcher = testDispatcher
        )
        val evaluationContext = ImmutableContext("foo", mapOf("hello" to Value.String("world")))
        val context = evaluationContext.toConfidenceContext().map
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()
        assertEquals(mockConfidence.getContext(), context)
        val newContext = ImmutableContext("foo").toConfidenceContext().map
        confidenceFeatureProvider.onContextSet(evaluationContext, ImmutableContext("foo"))
        advanceUntilIdle()
        assertEquals(mockConfidence.getContext(), newContext)
    }

    @Test
    fun testSimpleResolveInMemoryCache() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret)
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence,
                initialisationStrategy = InitialisationStrategy.FetchAndActivate,
                eventHandler = eventsHandler
            ),
            ImmutableContext(
                targetingKey = UUID.randomUUID().toString(),
                attributes = mutableMapOf(
                    "user" to Value.Structure(
                        mapOf(
                            "country" to Value.String("SE")
                        )
                    )
                )
            )
        )
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetails = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "kotlin-test-flag.my-integer",
                0
            )
        Assert.assertNull(intDetails.errorCode)
        Assert.assertNull(intDetails.errorMessage)
        Assert.assertNotNull(intDetails.value)
        Assert.assertNotEquals(0, intDetails.value)
        Assert.assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        Assert.assertNotNull(intDetails.variant)
    }

    @Test
    fun testSimpleResolveStoredCache() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        val cacheFile = File(mockContext.filesDir, FLAGS_FILE_NAME)
        Assert.assertEquals(0L, cacheFile.length())
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret)
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence,
                eventHandler = eventsHandler
            ),
            ImmutableContext(
                targetingKey = UUID.randomUUID().toString(),
                attributes = mutableMapOf(
                    "user" to Value.Structure(
                        mapOf(
                            "country" to Value.String("SE")
                        )
                    )
                )
            )
        )

        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        Assert.assertNotEquals(0L, cacheFile.length())
        val intDetails = OpenFeatureAPI.getClient().getIntegerDetails("kotlin-test-flag.my-integer", 0)
        Assert.assertNull(intDetails.errorCode)
        Assert.assertNull(intDetails.errorMessage)
        Assert.assertNotNull(intDetails.value)
        Assert.assertNotEquals(0, intDetails.value)
        Assert.assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        Assert.assertNotNull(intDetails.variant)
    }
}

private fun getConfidence(dispatcher: CoroutineDispatcher): Confidence = ConfidenceFactory.create(
    context = mockContext,
    clientSecret = clientSecret,
    dispatcher = dispatcher
)

internal const val FLAGS_FILE_NAME = "confidence_flags_cache.json"