@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FileDiskStorage
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

    private val resolvedFlags = com.spotify.confidence.client.Flags(
        listOf(
            com.spotify.confidence.client.ResolvedFlag(
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
        val mockClient: com.spotify.confidence.client.FlagApplierClient = mock()
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val cache1 = InMemoryCache()
        val context = mapOf("user_id" to ConfidenceValue.String("user1"))
        val confidence1 = getConfidence(testDispatcher, initialContext = context, cache = cache1)
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        confidence1.fetchAndActivate()

        // Simulate offline scenario
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenThrow(Error())
        val confidence2 = getConfidence(testDispatcher, cache = InMemoryCache(), initialContext = context)
        confidence2.fetchAndActivate()

        val evalString = confidence2.getFlag("test-kotlin-flag-1.mystring", "default")
        val evalBool = confidence2.getFlag("test-kotlin-flag-1.myboolean", true)
        val evalInteger = confidence2.getFlag("test-kotlin-flag-1.myinteger", 1)
        val evalDouble = confidence2.getFlag("test-kotlin-flag-1.mydouble", 7.28)
        val evalDate = confidence2.getFlag("test-kotlin-flag-1.mydate", "error")
        val evalObject = confidence2.getFlag("test-kotlin-flag-1.mystruct", ConfidenceValue.Struct(mapOf()))
        val evalNested = confidence2.getFlag("test-kotlin-flag-1.mystruct.innerString", "error")
        val evalNull = confidence2.getFlag("test-kotlin-flag-1.mynull", "error")

        TestCase.assertEquals("red", evalString.value)
        TestCase.assertEquals(false, evalBool.value)
        TestCase.assertEquals(7, evalInteger.value)
        TestCase.assertEquals(3.14, evalDouble.value)
        TestCase.assertEquals("2023-03-01T14:01:46.999Z", evalDate.value)
        TestCase.assertEquals(
            ConfidenceValue.Struct(mapOf("innerString" to ConfidenceValue.String("innerValue"))),
            evalObject.value
        )
        TestCase.assertEquals("innerValue", evalNested.value)
        TestCase.assertEquals("error", evalNull.value)

        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalString.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalBool.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalInteger.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalDouble.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalDate.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalObject.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalNested.reason)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalNull.reason)
    }

    private fun getConfidence(
        dispatcher: CoroutineDispatcher,
        cache: ProviderCache = mock(),
        initialContext: Map<String, ConfidenceValue> = mapOf(),
        debugLogger: DebugLoggerFake = DebugLoggerFake(),
    ) = Confidence(
        clientSecret = "",
        dispatcher = dispatcher,
        eventSenderEngine = mock(),
        cache = cache,
        initialContext = initialContext,
        flagResolver = flagResolverClient,
        flagApplierClient = mock(),
        diskStorage = FileDiskStorage.create(mockContext),
        region = ConfidenceRegion.EUROPE,
        debugLogger = debugLogger
    )
}