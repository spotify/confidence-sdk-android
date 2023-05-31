package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.ResolveFlagsResponse
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.contrib.providers.client.SchemaType
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
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
    private val instant = Instant.parse("2023-03-01T14:01:46Z")
    private val resolvedFlags = listOf(
        ResolvedFlag(
            "fdema-kotlin-flag-1",
            "flags/fdema-kotlin-flag-1/variants/variant-1",
            MutableStructure(
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
            SchemaType.SchemaStruct(
                mapOf(
                    "mystring" to SchemaType.StringSchema,
                    "myboolean" to SchemaType.BoolSchema,
                    "myinteger" to SchemaType.IntSchema,
                    "mydouble" to SchemaType.DoubleSchema,
                    "mydate" to SchemaType.StringSchema,
                    "mystruct" to SchemaType.SchemaStruct(
                        mapOf(
                            "innerString" to SchemaType.StringSchema
                        )
                    ),
                    "mynull" to SchemaType.StringSchema
                )
            ),
            ResolveReason.RESOLVE_REASON_MATCH
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
        val cache1 = StorageFileCache(mockContext)
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        val provider1 = ConfidenceFeatureProvider.Builder(mockContext, "")
            .client(mockClient)
            .cache(cache1)
            .build()
        runBlocking {
            provider1.initialize(MutableContext(targetingKey = "user1"))
        }

        // Simulate offline scenario
        whenever(mockClient.resolve(eq(listOf()), any())).thenThrow(Error())
        // Create new cache to force reading cache data from storage
        val cache2 = StorageFileCache(mockContext)
        val provider2 = ConfidenceFeatureProvider.Builder(mockContext, "")
            .client(mockClient)
            .cache(cache2)
            .build()
        val evalString = provider2.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", MutableContext("user1"))
        val evalBool = provider2.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true, MutableContext("user1"))
        val evalInteger = provider2.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1, MutableContext("user1"))
        val evalDouble = provider2.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28, MutableContext("user1"))
        val evalDate = provider2.getStringEvaluation("fdema-kotlin-flag-1.mydate", "error", MutableContext("user1"))
        val evalObject = provider2.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()), MutableContext("user1"))
        val evalNested = provider2.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "error", MutableContext("user1"))
        val evalNull = provider2.getStringEvaluation("fdema-kotlin-flag-1.mynull", "error", MutableContext("user1"))

        TestCase.assertEquals("red", evalString.value)
        TestCase.assertEquals(false, evalBool.value)
        TestCase.assertEquals(7, evalInteger.value)
        TestCase.assertEquals(3.14, evalDouble.value)
        TestCase.assertEquals("2023-03-01T14:01:46Z", evalDate.value)
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