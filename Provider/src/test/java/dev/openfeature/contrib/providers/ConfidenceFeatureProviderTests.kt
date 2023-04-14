<<<<<<< HEAD
@file:OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class
)

package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.apply.APPLY_FILE_NAME
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.Flags
import dev.openfeature.contrib.providers.client.ResolveFlags
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolvedFlag
=======
package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.contrib.providers.client.*
>>>>>>> 43375cb (Transfer codebase)
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
<<<<<<< HEAD
import dev.openfeature.sdk.exceptions.OpenFeatureError.FlagNotFoundError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    private val instant = Instant.parse("2023-03-01T14:01:46.645Z")
=======
import dev.openfeature.sdk.exceptions.OpenFeatureError.*
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

internal class ConfidenceFeatureProviderTests {
    private val mockClient: ConfidenceClient = mock()
    private val mockContext: Context = mock()
    private val instant = Instant.parse("2023-03-01T14:01:46Z")
>>>>>>> 43375cb (Transfer codebase)
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
<<<<<<< HEAD
    private val resolvedFlags = Flags(
        listOf(
            ResolvedFlag(
                "fdema-kotlin-flag-1",
                "flags/fdema-kotlin-flag-1/variants/variant-1",
                MutableStructure(resolvedValueAsMap),
                ResolveReason.RESOLVE_REASON_MATCH
            )
        )
    )

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    @Test
    fun testMatching() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            dispatcher = testDispatcher
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
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
        verify(mockClient, times(1)).apply(any(), eq("token1"))
=======
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

    @Test
    fun testMatching() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("foo"))
        }
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default")
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true)
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1)
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28)
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "error")
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "error")
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "error")
>>>>>>> 43375cb (Transfer codebase)

        assertEquals("red", evalString.value)
        assertEquals(false, evalBool.value)
        assertEquals(7, evalInteger.value)
        assertEquals(3.14, evalDouble.value)
<<<<<<< HEAD
        assertEquals("2023-03-01T14:01:46.645Z", evalDate.value)
=======
        assertEquals("2023-03-01T14:01:46Z", evalDate.value)
>>>>>>> 43375cb (Transfer codebase)
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
<<<<<<< HEAD
    fun testDelayedApply() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            dispatcher = testDispatcher
        )
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
        whenever(mockClient.apply(any(), any())).thenThrow(Error())

        val evaluationContext = MutableContext("foo")
        runBlocking {
            confidenceFeatureProvider.initialize(evaluationContext)
        }

        verify(mockClient, times(1)).resolve(any(), eq(evaluationContext))

        val evalString = confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation(
            "fdema-kotlin-flag-1.myboolean",
            true,
            evaluationContext
        )
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation(
            "fdema-kotlin-flag-1.myinteger",
            1,
            evaluationContext
        )
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation(
            "fdema-kotlin-flag-1.mydouble",
            7.28,
            evaluationContext
        )
        val evalDate = confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mydate",
            "error",
            evaluationContext
        )
        val evalObject = confidenceFeatureProvider.getObjectEvaluation(
            "fdema-kotlin-flag-1.mystruct",
            Value.Structure(mapOf()),
            evaluationContext
        )
        val evalNested = confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystruct.innerString",
            "error",
            evaluationContext
        )
        val evalNull = confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mynull",
            "error",
            evaluationContext
        )

        advanceUntilIdle()
        verify(mockClient, times(8)).apply(any(), eq("token1"))
        assertEquals(false, Json.parseToJsonElement(cacheFile.readText()).jsonObject["token1"]?.jsonObject?.get("fdema-kotlin-flag-1")?.jsonObject?.get("sent")?.jsonPrimitive?.boolean)
        whenever(mockClient.apply(any(), any())).then {}

        // Evaluate a flag property in order to trigger an apply
        confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "empty", evaluationContext)

        advanceUntilIdle()
        val captor = argumentCaptor<List<AppliedFlag>>()
        verify(mockClient, times(9)).apply(captor.capture(), eq("token1"))
        assertEquals(1, captor.firstValue.count())
        assertEquals("fdema-kotlin-flag-1", captor.firstValue.first().flag)

        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
        assertEquals("red", evalString.value)
        assertEquals(false, evalBool.value)
        assertEquals(7, evalInteger.value)
        assertEquals(3.14, evalDouble.value)
        assertEquals("2023-03-01T14:01:46.645Z", evalDate.value)
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
    fun testApplyOnMultipleEvaluations() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            dispatcher = testDispatcher
        )
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        whenever(mockClient.apply(any(), any())).then {}

        val evaluationContext1 = MutableContext("foo")
        val evaluationContext2 = MutableContext("bar")

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(evaluationContext1)
        }
        verify(mockClient, times(1)).resolve(any(), eq(evaluationContext1))

        val evalString1 = confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystring",
            "default",
            evaluationContext1
        )
        // Second evaluation shouldn't trigger apply
        confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystring",
            "default",
            evaluationContext1
        )

        advanceUntilIdle()
        verify(mockClient, times(1)).apply(any(), eq("token1"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)

        val captor1 = argumentCaptor<List<AppliedFlag>>()
        verify(mockClient, times(1)).apply(captor1.capture(), eq("token1"))

        assertEquals(1, captor1.firstValue.count())
        assertEquals("fdema-kotlin-flag-1", captor1.firstValue.first().flag)
        assertEquals("red", evalString1.value)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalString1.reason)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalString1.variant)
        assertNull(evalString1.errorMessage)
        assertNull(evalString1.errorCode)

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token2"))
        runBlocking {
            confidenceFeatureProvider.onContextSet(evaluationContext1, evaluationContext2)
        }
        verify(mockClient, times(1)).resolve(any(), eq(evaluationContext2))

        // Third evaluation with different context should trigger apply
        val evalString2 = confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystring",
            "default",
            evaluationContext2
        )

        advanceUntilIdle()
        verify(mockClient, times(1)).apply(any(), eq("token2"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
        val captor = argumentCaptor<List<AppliedFlag>>()
        verify(mockClient, times(1)).apply(captor.capture(), eq("token2"))

        assertEquals(1, captor.firstValue.count())
        assertEquals("fdema-kotlin-flag-1", captor.firstValue.first().flag)
        assertEquals("red", evalString2.value)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalString2.reason)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalString2.variant)
        assertNull(evalString2.errorMessage)
        assertNull(evalString2.errorCode)
    }

    @Test
    fun testApplyFromStoredCache() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(
            "{\"token1\":{\"fdema-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"sent\":false}}}"
        )

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            dispatcher = testDispatcher
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
        whenever(mockClient.apply(any(), any())).then {}

        val evaluationContext = MutableContext("foo")
        runBlocking {
            confidenceFeatureProvider.initialize(evaluationContext)
        }

        verify(mockClient, times(1)).resolve(any(), eq(evaluationContext))

        confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "empty", evaluationContext)
        advanceUntilIdle()
        verify(mockClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testApplyCacheRemovesSentResolveTokens() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(
            "{\n" +
                "  \"token1\": {\n" +
                "    \"fdema-kotlin-flag-0\": {\n" +
                "      \"time\": \"2023-06-26T11:55:33.443Z\",\n" +
                "      \"sent\": true\n" +
                "    }\n" +
                "  },\n" +
                "  \"token2\": {\n" +
                "    \"fdema-kotlin-flag-2\": {\n" +
                "      \"time\": \"2023-06-26T11:55:33.444Z\",\n" +
                "      \"sent\": true\n" +
                "    },\n" +
                "    \"fdema-kotlin-flag-3\": {\n" +
                "      \"time\": \"2023-06-26T11:55:33.445Z\",\n" +
                "      \"sent\": false\n" +
                "    }\n" +
                "  },\n" +
                "  \"token3\": {\n" +
                "    \"fdema-kotlin-flag-4\": {\n" +
                "      \"time\": \"2023-06-26T11:55:33.446Z\",\n" +
                "      \"sent\": false\n" +
                "    }\n" +
                "  }\n" +
                "}\n"
        )

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            dispatcher = testDispatcher
        )

        whenever(mockClient.apply(any(), any())).then {}
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token2"))

        val evaluationContext = MutableContext("foo")

        runBlocking {
            confidenceFeatureProvider.initialize(evaluationContext)
        }

        confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )

        advanceUntilIdle()
        verify(mockClient, times(0)).apply(any(), eq("token1"))
        verify(mockClient, times(1)).apply(any(), eq("token2"))
        verify(mockClient, times(1)).apply(any(), eq("token3"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testMatchingRootObject() = runTest {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("foo"))
        }
        val evalRootObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1", Value.Structure(mapOf()), MutableContext("foo"))
=======
    fun testMatchingRootObject() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("foo"))
        }
        val evalRootObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1", Value.Structure(mapOf()))
>>>>>>> 43375cb (Transfer codebase)

        assertEquals(resolvedValueAsMap, evalRootObject.value.asStructure())
        assertEquals(Reason.TARGETING_MATCH.toString(), evalRootObject.reason)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalRootObject.variant)
        assertNull(evalRootObject.errorMessage)
        assertNull(evalRootObject.errorCode)
    }

    @Test
<<<<<<< HEAD
    fun testStale() = runTest {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            client = mockClient
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))

        // Simulate a case where the context in the cache is not synced with the evaluation's context
        cache.refresh(resolvedFlags.list, "token2", MutableContext("user1"))
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", MutableContext("user2"))
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true, MutableContext("user2"))
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1, MutableContext("user2"))
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28, MutableContext("user2"))
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "default1", MutableContext("user2"))
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()), MutableContext("user2"))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "default2", MutableContext("user2"))
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "default3", MutableContext("user2"))
=======
    fun testStale() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user2"))
        }
        // Simulate a case where the context in the cache is not synced with the evaluation's context
        cache.refresh(resolvedFlags, "token2", MutableContext("user1"))

        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default")
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true)
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1)
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28)
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "default1")
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "default2")
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "default3")
>>>>>>> 43375cb (Transfer codebase)

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
<<<<<<< HEAD
    fun testNonMatching() = runTest {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            client = mockClient
        )

        val resolvedNonMatchingFlags = Flags(
            listOf(
                ResolvedFlag(
                    flag = "fdema-kotlin-flag-1",
                    variant = "",
                    MutableStructure(mutableMapOf()),
                    ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH
                )
            )
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedNonMatchingFlags, "token1"))
=======
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
>>>>>>> 43375cb (Transfer codebase)
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user1"))
        }

<<<<<<< HEAD
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", MutableContext("user1"))
=======
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default")
>>>>>>> 43375cb (Transfer codebase)

        assertNull(evalString.errorMessage)
        assertNull(evalString.errorCode)
        assertNull(evalString.variant)
        assertEquals("default", evalString.value)
        assertEquals(Reason.DEFAULT.toString(), evalString.reason)
    }

    @Test
<<<<<<< HEAD
    fun testFlagNotFound() = runTest {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            client = mockClient
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
        // Simulate a case where the context in the cache is not synced with the evaluation's context
        // This shouldn't have an effect in this test, given that not found values are priority over stale values
        cache.refresh(resolvedFlags.list, "token2", MutableContext("user1"))
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default", MutableContext("user2"))
=======
    fun testFlagNotFound() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user2"))
        }
        // Simulate a case where the context in the cache is not synced with the evaluation's context
        // This shouldn't have an effect in this test, given that not found values are priority over stale values
        cache.refresh(resolvedFlags, "token2", MutableContext("user1"))
        // TODO Should flagNotFound throw?
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default")
>>>>>>> 43375cb (Transfer codebase)
        }
        assertEquals("Could not find flag named: fdema-kotlin-flag-2", ex.message)
    }

    @Test
<<<<<<< HEAD
    fun testErrorInNetwork() = runTest {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            client = mockClient
        )
=======
    fun testErrorInNetwork() {
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(cache)
            .client(mockClient)
            .build()

>>>>>>> 43375cb (Transfer codebase)
        whenever(mockClient.resolve(eq(listOf()), any())).thenThrow(Error())
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user1"))
        }
<<<<<<< HEAD
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default", MutableContext("user1"))
=======
        // TODO Should flagNotFound throw?
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default")
>>>>>>> 43375cb (Transfer codebase)
        }
        assertEquals("Could not find flag named: fdema-kotlin-flag-2", ex.message)
    }

    @Test
<<<<<<< HEAD
    fun testValueNotFound() = runTest {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
=======
    fun testValueNotFound() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
>>>>>>> 43375cb (Transfer codebase)
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user2"))
        }
        val ex = assertThrows(ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "fdema-kotlin-flag-1.wrongid",
<<<<<<< HEAD
                "default",
                MutableContext("user2")
=======
                "default"
>>>>>>> 43375cb (Transfer codebase)
            )
        }
        assertEquals("Unable to parse flag value: wrongid", ex.message)
    }

    @Test
<<<<<<< HEAD
    fun testValueNotFoundLongPath() = runTest {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlags(resolvedFlags, "token1"))
=======
    fun testValueNotFoundLongPath() {
        val confidenceFeatureProvider = ConfidenceFeatureProvider.Builder(mockContext, "")
            .cache(InMemoryCache())
            .client(mockClient)
            .build()

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveFlagsResponse(resolvedFlags, "token1"))
>>>>>>> 43375cb (Transfer codebase)
        runBlocking {
            confidenceFeatureProvider.initialize(MutableContext("user2"))
        }
        val ex = assertThrows(ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "fdema-kotlin-flag-1.mystring.extrapath",
<<<<<<< HEAD
                "default",
                MutableContext("user2")
=======
                "default"
>>>>>>> 43375cb (Transfer codebase)
            )
        }
        assertEquals("Unable to parse flag value: mystring/extrapath", ex.message)
    }
}