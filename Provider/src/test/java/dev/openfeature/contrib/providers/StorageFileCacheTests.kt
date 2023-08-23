package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.Flags
import dev.openfeature.contrib.providers.client.ResolveFlags
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolveResponse
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.ImmutableStructure
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.async.awaitProviderReady
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
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
        val cache1 = StorageFileCache.create(mockContext)
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveResponse.Resolved(ResolveFlags(resolvedFlags, "token1")))
        val provider1 = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            client = mockClient,
            cache = cache1
        )
        runBlocking {
            provider1.initialize(ImmutableContext(targetingKey = "user1"))
            awaitProviderReady()
        }
        // Simulate offline scenario
        whenever(mockClient.resolve(eq(listOf()), any())).thenThrow(Error())
        // Create new cache to force reading cache data from storage
        val cache2 = StorageFileCache.create(mockContext)
        val provider2 = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            client = mockClient,
            cache = cache2
        )
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