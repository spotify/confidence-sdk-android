package com.spotify.confidence.openfeature

import android.content.Context
import com.spotify.confidence.ConfidenceFactory
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.ImmutableStructure
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.TrackingEventDetails
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.ErrorCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @After
    fun tearDown() = runTest {
        OpenFeatureAPI.shutdown()
    }

    @Test
    fun testSimpleResolveInMemoryCache() = runTest {
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret)
        OpenFeatureAPI.setProviderAndWait(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence,
                initialisationStrategy = InitialisationStrategy.FetchAndActivate
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
    fun testSimpleResolveStoredCache() = runTest {
        val cacheFile = File(mockContext.filesDir, flagsFileName)
        assertEquals(0L, cacheFile.length())
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret)
        OpenFeatureAPI.setProviderAndWait(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence
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
    fun testSimpleResolveWithFetchAndActivateInMemoryCache() = runTest {
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret)

        OpenFeatureAPI.setProviderAndWait(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence,
                initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync
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

    @Test
    fun testEventTracking() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cacheDir = mockContext.getDir("events", Context.MODE_PRIVATE)
        assertTrue(cacheDir.isDirectory)
        assertTrue(cacheDir.listFiles().isEmpty())
        val mockConfidence = ConfidenceFactory.create(mockContext, clientSecret, dispatcher = testDispatcher)

        OpenFeatureAPI.setProviderAndWait(
            ConfidenceFeatureProvider.create(
                confidence = mockConfidence,
                initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync
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

        assertEquals(1, cacheDir.listFiles()?.size)
        assertEquals(0, cacheDir.listFiles()?.first()?.readLines()?.size)

        OpenFeatureAPI.getClient().track("MyEventName", TrackingEventDetails(33.0, ImmutableStructure("key" to Value.String("value"))))
        testScheduler.advanceUntilIdle()
        val lines = cacheDir.listFiles()?.first()?.readLines() ?: emptyList()
        assertEquals(1, lines.size)
        val jsonString = lines.first()
        assertTrue(jsonString.contains("\"eventDefinition\":\"MyEventName\""))
        assertTrue(jsonString.contains("\"payload\":{\"value\":{\"double\":33.0},\"key\":{\"string\":\"value\"}"))
        val regex = Regex(
            "\"context\":\\{\"map\":\\{\"visitor_id\":\\{\"string\":\"[a-f0-9\\-]+\"}," +
                "\"targeting_key\":\\{\"string\":\"[a-f0-9\\-]+\"}," +
                "\"user\":\\{\"map\":\\{\"country\":\\{\"string\":\"SE\"}}}}}"
        )
        assertTrue(
            "Expected the context map to match the regex for visitor_id and targeting_key. Actual JSON:\n$jsonString",
            regex.containsMatchIn(jsonString)
        )
    }

    private val flagsFileName = "confidence_flags_cache.json"
    private val eventsFileName = "confidence_flags_cache.json"
}