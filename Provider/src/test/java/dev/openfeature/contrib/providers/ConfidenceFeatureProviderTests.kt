@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class, ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class, ExperimentalCoroutinesApi::class
)

package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.apply.APPLY_FILE_NAME
import dev.openfeature.contrib.providers.apply.FlagApplierWithRetries
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.ResolveFlagsResponse
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.contrib.providers.client.SchemaType
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.OpenFeatureError.FlagNotFoundError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConfidenceFeatureProviderTests {
    private val mockClient: ConfidenceClient = mock()
    private val mockContext: Context = mock()
    private val instant = Instant.parse("2023-03-01T14:01:46Z")
    private val resolvedValueAsMap = mutableMapOf(
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
    private val resolvedFlags = listOf(
        ResolvedFlag(
            "fdema-kotlin-flag-1",
            "flags/fdema-kotlin-flag-1/variants/variant-1",
            MutableStructure(resolvedValueAsMap),
            SchemaType.SchemaStruct(mapOf(
                "mystring" to SchemaType.StringSchema,
                "myboolean" to SchemaType.BoolSchema,
                "myinteger" to SchemaType.IntSchema,
                "mydouble" to SchemaType.DoubleSchema,
                "mydate" to SchemaType.StringSchema,
                "mystruct" to SchemaType.SchemaStruct(mapOf(
                    "innerString" to SchemaType.StringSchema
                )),
                "mynull" to SchemaType.StringSchema
            )),
            ResolveReason.RESOLVE_REASON_MATCH
        )
    )
    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    @Test
    fun testMatching() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flagApplier = FlagApplierWithRetries(mockClient, testDispatcher, mockContext)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .flagApplier(flagApplier)
            .client(mockClient)
            .build()
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("foo"))
        }
        verify(mockClient, times(1)).resolve(any(), eq(MutableContext("foo")))
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", MutableContext("foo"))
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true, MutableContext("foo"))
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1, MutableContext("foo"))
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28, MutableContext("foo"))
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "error", MutableContext("foo"))
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()), MutableContext("foo"))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "error", MutableContext("foo"))
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "error", MutableContext("foo"))

        advanceUntilIdle()
        verify(mockClient, times(8)).apply(any(), eq("token1"))

        assertEquals("red", evalString.value)
        assertEquals(false, evalBool.value)
        assertEquals(7, evalInteger.value)
        assertEquals(3.14, evalDouble.value)
        assertEquals("2023-03-01T14:01:46Z", evalDate.value)
        assertEquals(Value.Structure(mapOf("innerString" to Value.String("innerValue"))), evalObject.value)
        assertEquals("innerValue", evalNested.value)
        assertEquals("error", evalNull.value)

        assertEquals(Reason.TARGETING_MATCH.toString(), evalString.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalBool.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalInteger.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalDouble.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalDate.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalObject.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalNested.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalNull.reason)

        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalString.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalBool.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalInteger.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalDouble.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalDate.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalObject.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalNested.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalNull.variant)

        assertNull(evalString.errorMessage)
        assertNull(evalBool.errorMessage)
        assertNull(evalInteger.errorMessage)
        assertNull(evalDouble.errorMessage)
        assertNull(evalDate.errorMessage)
        assertNull(evalObject.errorMessage)
        assertNull(evalNested.errorMessage)
        assertNull(evalNull.errorMessage)

        assertNull(evalString.errorCode)
        assertNull(evalBool.errorCode)
        assertNull(evalInteger.errorCode)
        assertNull(evalDouble.errorCode)
        assertNull(evalDate.errorCode)
        assertNull(evalObject.errorCode)
        assertNull(evalNested.errorCode)
        assertNull(evalNull.errorCode)
    }

    @Test
    fun testDelayedApply() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flagApplier = FlagApplierWithRetries(mockClient, testDispatcher, mockContext)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .flagApplier(flagApplier)
            .client(mockClient)
            .build()
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        whenever(mockClient.apply(any(), any())).thenThrow(Error())

        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("foo"))
        }

        verify(mockClient, times(1)).resolve(any(), eq(MutableContext("foo")))

        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default")
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true)
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1)
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28)
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "error")
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "error")
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "error")

        advanceUntilIdle()
        verify(mockClient, times(8)).apply(any(), eq("token1"))
        assertEquals(8, Json.parseToJsonElement(cacheFile.readText()).jsonObject["events"]?.jsonArray?.size)
        whenever(mockClient.apply(any(), any())).then {}

        flagApplier.triggerBatch()

        advanceUntilIdle()
        verify(mockClient, times(9)).apply(any(), eq("token1"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject["events"]?.jsonArray?.size)
        assertEquals("red", evalString.value)
        assertEquals(false, evalBool.value)
        assertEquals(7, evalInteger.value)
        assertEquals(3.14, evalDouble.value)
        assertEquals("2023-03-01T14:01:46Z", evalDate.value)
        assertEquals(Value.Structure(mapOf("innerString" to Value.String("innerValue"))), evalObject.value)
        assertEquals("innerValue", evalNested.value)
        assertEquals("error", evalNull.value)

        assertEquals(Reason.TARGETING_MATCH.toString(), evalString.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalBool.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalInteger.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalDouble.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalDate.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalObject.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalNested.reason)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalNull.reason)

        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalString.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalBool.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalInteger.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalDouble.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalDate.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalObject.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalNested.variant)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalNull.variant)

        assertNull(evalString.errorMessage)
        assertNull(evalBool.errorMessage)
        assertNull(evalInteger.errorMessage)
        assertNull(evalDouble.errorMessage)
        assertNull(evalDate.errorMessage)
        assertNull(evalObject.errorMessage)
        assertNull(evalNested.errorMessage)
        assertNull(evalNull.errorMessage)

        assertNull(evalString.errorCode)
        assertNull(evalBool.errorCode)
        assertNull(evalInteger.errorCode)
        assertNull(evalDouble.errorCode)
        assertNull(evalDate.errorCode)
        assertNull(evalObject.errorCode)
        assertNull(evalNested.errorCode)
        assertNull(evalNull.errorCode)
    }

    @Test
    fun testMatchingRootObject() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("foo"))
        }
        val evalRootObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1", Value.Structure(mapOf()), MutableContext("foo"))

        assertEquals(resolvedValueAsMap, evalRootObject.value.asStructure())
        assertEquals(Reason.TARGETING_MATCH.toString(), evalRootObject.reason)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalRootObject.variant)
        assertNull(evalRootObject.errorMessage)
        assertNull(evalRootObject.errorCode)
    }

    @Test
    fun testStale() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))

        // Simulate a case where the context in the cache is not synced with the evaluation's context
        cache.refresh(resolvedFlags, "token2", MutableContext("user1"))
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", MutableContext("user2"))
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true, MutableContext("user2"))
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1, MutableContext("user2"))
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28, MutableContext("user2"))
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "default1", MutableContext("user2"))
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()), MutableContext("user2"))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "default2", MutableContext("user2"))
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "default3", MutableContext("user2"))

        assertEquals("default", evalString.value)
        assertEquals(true, evalBool.value)
        assertEquals(1, evalInteger.value)
        assertEquals(7.28, evalDouble.value)
        assertEquals("default1", evalDate.value)
        assertEquals(Value.Structure(mapOf()), evalObject.value)
        assertEquals("default2", evalNested.value)
        assertEquals("default3", evalNull.value)

        assertEquals(Reason.STALE.toString(), evalString.reason)
        assertEquals(Reason.STALE.toString(), evalBool.reason)
        assertEquals(Reason.STALE.toString(), evalInteger.reason)
        assertEquals(Reason.STALE.toString(), evalDouble.reason)
        assertEquals(Reason.STALE.toString(), evalDate.reason)
        assertEquals(Reason.STALE.toString(), evalObject.reason)
        assertEquals(Reason.STALE.toString(), evalNested.reason)
        assertEquals(Reason.STALE.toString(), evalNull.reason)

        assertNull(evalString.variant)
        assertNull(evalBool.variant)
        assertNull(evalInteger.variant)
        assertNull(evalDouble.variant)
        assertNull(evalDate.variant)
        assertNull(evalObject.variant)
        assertNull(evalNested.variant)
        assertNull(evalNull.variant)

        assertNull(evalString.errorMessage)
        assertNull(evalBool.errorMessage)
        assertNull(evalInteger.errorMessage)
        assertNull(evalDouble.errorMessage)
        assertNull(evalDate.errorMessage)
        assertNull(evalObject.errorMessage)
        assertNull(evalNested.errorMessage)
        assertNull(evalNull.errorMessage)

        assertNull(evalString.errorCode)
        assertNull(evalBool.errorCode)
        assertNull(evalInteger.errorCode)
        assertNull(evalDouble.errorCode)
        assertNull(evalDate.errorCode)
        assertNull(evalObject.errorCode)
        assertNull(evalNested.errorCode)
        assertNull(evalNull.errorCode)
    }

    @Test
    fun testNonMatching() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

        val resolvedNonMatchingFlags = listOf(
            ResolvedFlag(
                "fdema-kotlin-flag-1",
                "",
                MutableStructure(mutableMapOf()),
                SchemaType.SchemaStruct(mapOf( )),
                ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH
            )
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedNonMatchingFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user1"))
        }

        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", MutableContext("user1"))

        assertNull(evalString.errorMessage)
        assertNull(evalString.errorCode)
        assertNull(evalString.variant)
        assertEquals("default", evalString.value)
        assertEquals(Reason.DEFAULT.toString(), evalString.reason)
    }

    @Test
    fun testFlagNotFound() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        // Simulate a case where the context in the cache is not synced with the evaluation's context
        // This shouldn't have an effect in this test, given that not found values are priority over stale values
        cache.refresh(resolvedFlags, "token2", MutableContext("user1"))
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default", MutableContext("user2"))
        }
        assertEquals("Could not find flag named: fdema-kotlin-flag-2", ex.message)
    }

    @Test
    fun testErrorInNetwork() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenThrow(Error())
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user1"))
        }
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default", MutableContext("user1"))
        }
        assertEquals("Could not find flag named: fdema-kotlin-flag-2", ex.message)
    }

    @Test
    fun testValueNotFound() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user2"))
        }
        val ex = assertThrows(ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "fdema-kotlin-flag-1.wrongid",
                "default",
                MutableContext("user2")
            )
        }
        assertEquals("Unable to parse flag value: wrongid", ex.message)
    }

    @Test
    fun testValueNotFoundLongPath() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user2"))
        }
        val ex = assertThrows(ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "fdema-kotlin-flag-1.mystring.extrapath",
                "default",
                MutableContext("user2")
            )
        }
        assertEquals("Unable to parse flag value: mystring/extrapath", ex.message)
    }
}