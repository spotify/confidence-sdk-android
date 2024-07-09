package com.spotify.confidence.openfeature

import android.content.Context
import com.spotify.confidence.ConfidenceFactory
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import dev.openfeature.sdk.exceptions.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class ProviderIntegrationTest {

    @get:Rule
    var tmpFile = TemporaryFolder()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
        whenever(mockContext.getDir(any(), any())).thenReturn(Files.createTempDirectory("events").toFile())
        whenever(mockContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(InMemorySharedPreferences())
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
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }

    @Test
    fun testSimpleResolveStoredCache() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        val cacheFile = File(mockContext.filesDir, flagsFileName)
        assertEquals(0L, cacheFile.length())
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

        assertNotEquals(0L, cacheFile.length())
        val intDetails = OpenFeatureAPI.getClient().getIntegerDetails("kotlin-test-flag.my-integer", 0)
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }

    @Test
    fun testSimpleResolveWithFetchAndActivateInMemoryCache() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret)

        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence,
                initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync,
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

        val flagNotFoundDetails = OpenFeatureAPI.getClient()
            .getObjectDetails(
                "kotlin-test-flag",
                Value.Structure(emptyMap())
            )
        assertEquals(ErrorCode.FLAG_NOT_FOUND, flagNotFoundDetails.errorCode)
        assertNull(flagNotFoundDetails.errorMessage)
        assertEquals(Value.Structure(emptyMap()), flagNotFoundDetails.value)
        assertEquals(Reason.ERROR.name, flagNotFoundDetails.reason)
        assertNull(flagNotFoundDetails.variant)

        runBlocking {
            mockConfidence.fetchAndActivate()
        }

        val evaluationDetails = OpenFeatureAPI.getClient()
            .getObjectDetails(
                "kotlin-test-flag",
                Value.Structure(emptyMap())
            )
        assertNull(evaluationDetails.errorCode)
        assertNull(evaluationDetails.errorMessage)
        assertNotNull(evaluationDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, evaluationDetails.reason)
        assertNotNull(evaluationDetails.variant)

        assertEquals(4, evaluationDetails.value.asStructure()?.getOrDefault("my-integer", Value.Integer(-1))?.asInteger())
    }
    private val flagsFileName = "confidence_flags_cache.json"
}