@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ConfidenceRegion
import com.spotify.confidence.client.FlagApplierClient
import com.spotify.confidence.client.Flags
import com.spotify.confidence.client.ResolveReason
import com.spotify.confidence.client.ResolvedFlag
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.awaitReadyOrError
import junit.framework.TestCase
import kotlinx.coroutines.CoroutineDispatcher
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
    private val flagResolverClient = mock<FlagResolver>()

    private val resolvedFlags = Flags(
        listOf(
            ResolvedFlag(
                "test-kotlin-flag-1",
                "flags/test-kotlin-flag-1/variants/variant-1",
                mutableMapOf(
                    "mystring" to ConfidenceValue.String("red"),
                    "myboolean" to ConfidenceValue.Boolean(false),
                    "myinteger" to ConfidenceValue.Integer(7),
                    "mydouble" to ConfidenceValue.Double(3.14),
                    "mydate" to ConfidenceValue.String(instant.toString()),
                    "mystruct" to ConfidenceValue.Struct(
                        mapOf(
                            "innerString" to ConfidenceValue.String("innerValue")
                        )
                    ),
                    "mynull" to ConfidenceValue.Null
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOfflineScenarioLoadsStoredCache() = runTest {
        val mockClient: FlagApplierClient = mock()
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val cache1 = InMemoryCache()
        val confidence = getConfidence(testDispatcher)
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext(targetingKey = "user1").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        val provider1 = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = confidence,
            cache = cache1
        )
        provider1.initialize(ImmutableContext(targetingKey = "user1"))
        provider1.awaitReadyOrError(testDispatcher)

        // Simulate offline scenario
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenThrow(Error())
        val provider2 = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = confidence,
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

    private fun getConfidence(dispatcher: CoroutineDispatcher) = Confidence(
        clientSecret = "",
        dispatcher = dispatcher,
        eventSenderEngine = mock(),
        flagResolver = flagResolverClient,
        flagApplierClient = mock(),
        diskStorage = FileDiskStorage.create(mockContext),
        region = ConfidenceRegion.EUROPE
    )
}