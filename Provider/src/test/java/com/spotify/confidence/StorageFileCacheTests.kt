@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.InMemoryCache
import com.spotify.confidence.client.ConfidenceClient
import com.spotify.confidence.client.Flags
import com.spotify.confidence.client.ResolveFlags
import com.spotify.confidence.client.ResolveReason
import com.spotify.confidence.client.ResolveResponse
import com.spotify.confidence.client.ResolvedFlag
import com.spotify.confidence.client.Result
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.ImmutableStructure
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.async.awaitReady
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import junit.framework.TestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.time.Instant

class StorageFileCacheTests {
    private val instant = Instant.parse("2023-03-01T14:01:46.999Z")
    private val resolvedFlags = Flags(
        listOf(
            ResolvedFlag(
                "test-kotlin-flag-1",
                "flags/test-kotlin-flag-1/variants/variant-1",
                ImmutableStructure(
                    mutableMapOf(
                        "mystring" to Value.String("red"),
                        "myboolean" to Value.Boolean(false),
                        "myinteger" to Value.Integer(7),
                        "mydouble" to Value.Double(3.14),
                        "mydate" to Value.String(instant.toString()),
                        "mystruct" to Value.Structure(
                            mapOf(
                                "innerString" to Value.String("innerValue")
                            )
                        ),
                        "mynull" to Value.Null
                    )
                ),
                ResolveReason.RESOLVE_REASON_MATCH
            )
        )
    )
    private val mockContext: Context = mock()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    @Test
    fun testOfflineScenarioLoadsStoredCache() = runTest {
        val mockClient: ConfidenceClient = mock()
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val eventPublisher = EventHandler(testDispatcher)
        eventPublisher.publish(OpenFeatureEvents.ProviderStale)
        val cache1 = InMemoryCache()
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )
        val provider1 = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            client = mockClient,
            eventHandler = eventPublisher,
            cache = cache1
        )
        provider1.initialize(ImmutableContext(targetingKey = "user1"))
        provider1.awaitReady(testDispatcher)

        // Simulate offline scenario
        whenever(mockClient.resolve(eq(listOf()), any())).thenThrow(Error())
        val provider2 = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            client = mockClient,
            cache = InMemoryCache()
        )
        provider2.initialize(ImmutableContext("user1"))

        val evalString = provider2.getStringEvaluation("test-kotlin-flag-1.mystring", "default", ImmutableContext("user1"))
        val evalBool = provider2.getBooleanEvaluation("test-kotlin-flag-1.myboolean", true, ImmutableContext("user1"))
        val evalInteger = provider2.getIntegerEvaluation("test-kotlin-flag-1.myinteger", 1, ImmutableContext("user1"))
        val evalDouble = provider2.getDoubleEvaluation("test-kotlin-flag-1.mydouble", 7.28, ImmutableContext("user1"))
        val evalDate = provider2.getStringEvaluation("test-kotlin-flag-1.mydate", "error", ImmutableContext("user1"))
        val evalObject = provider2.getObjectEvaluation("test-kotlin-flag-1.mystruct", Value.Structure(mapOf()), ImmutableContext("user1"))
        val evalNested = provider2.getStringEvaluation("test-kotlin-flag-1.mystruct.innerString", "error", ImmutableContext("user1"))
        val evalNull = provider2.getStringEvaluation("test-kotlin-flag-1.mynull", "error", ImmutableContext("user1"))

        TestCase.assertEquals("red", evalString.value)
        TestCase.assertEquals(false, evalBool.value)
        TestCase.assertEquals(7, evalInteger.value)
        TestCase.assertEquals(3.14, evalDouble.value)
        TestCase.assertEquals("2023-03-01T14:01:46.999Z", evalDate.value)
        TestCase.assertEquals(
            Value.Structure(mapOf("innerString" to Value.String("innerValue"))),
            evalObject.value
        )
        TestCase.assertEquals("innerValue", evalNested.value)
        TestCase.assertEquals("error", evalNull.value)

        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalString.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalBool.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalInteger.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalDouble.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalDate.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalObject.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalNested.reason)
        TestCase.assertEquals(Reason.TARGETING_MATCH.toString(), evalNull.reason)
    }
}