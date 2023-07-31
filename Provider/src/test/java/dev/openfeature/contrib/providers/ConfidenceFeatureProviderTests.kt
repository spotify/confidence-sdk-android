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
import dev.openfeature.contrib.providers.apply.EventStatus
import dev.openfeature.contrib.providers.apply.FlagsAppliedMap
import dev.openfeature.contrib.providers.apply.json
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.Flags
import dev.openfeature.contrib.providers.client.ResolveFlags
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolveResponse
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.contrib.providers.client.Result
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.ImmutableStructure
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.exceptions.OpenFeatureError.FlagNotFoundError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.time.Instant

private const val cacheFileData = "{\n" +
    "  \"token1\": {\n" +
    "    \"fdema-kotlin-flag-0\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.443Z\",\n" +
    "      \"sent\": \"SENT\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"token2\": {\n" +
    "    \"fdema-kotlin-flag-2\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.444Z\",\n" +
    "      \"sent\": \"SENT\"\n" +
    "    },\n" +
    "    \"fdema-kotlin-flag-3\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.445Z\",\n" +
    "      \"sent\": \"CREATED\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"token3\": {\n" +
    "    \"fdema-kotlin-flag-4\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.446Z\",\n" +
    "      \"sent\": \"CREATED\"\n" +
    "    }\n" +
    "  }\n" +
    "}\n"

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConfidenceFeatureProviderTests {
    private val mockClient: ConfidenceClient = mock()
    private val mockContext: Context = mock()
    private val instant = Instant.parse("2023-03-01T14:01:46.645Z")
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
    private val resolvedFlags = Flags(
        listOf(
            ResolvedFlag(
                "fdema-kotlin-flag-1",
                "flags/fdema-kotlin-flag-1/variants/variant-1",
                ImmutableStructure(resolvedValueAsMap),
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
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveResponse.Resolved(ResolveFlags(resolvedFlags, "token1")))
        confidenceFeatureProvider.initialize(ImmutableContext("foo"))
        advanceUntilIdle()
        verify(mockClient, times(1)).resolve(any(), eq(ImmutableContext("foo")))
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", ImmutableContext("foo"))
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true, ImmutableContext("foo"))
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1, ImmutableContext("foo"))
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28, ImmutableContext("foo"))
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "error", ImmutableContext("foo"))
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()), ImmutableContext("foo"))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "error", ImmutableContext("foo"))
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "error", ImmutableContext("foo"))

        advanceUntilIdle()
        verify(mockClient, times(1)).apply(any(), eq("token1"))

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
    fun testDelayedApply() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            client = mockClient,
            dispatcher = testDispatcher
        )
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveResponse.Resolved(ResolveFlags(resolvedFlags, "token1")))
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Failure)

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

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
        val expectedStatus = json.decodeFromString<FlagsAppliedMap>(cacheFile.readText())["token1"]
            ?.get("fdema-kotlin-flag-1")?.sent
        assertEquals(EventStatus.CREATED, expectedStatus)
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success)

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
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher
        )
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success)

        val evaluationContext1 = ImmutableContext("foo")
        val evaluationContext2 = ImmutableContext("bar")

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )
        confidenceFeatureProvider.initialize(evaluationContext1)
        advanceUntilIdle()
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

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token2")
            )
        )
        confidenceFeatureProvider.onContextSet(evaluationContext1, evaluationContext2)
        advanceUntilIdle()
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
            "{\"token1\":{\"fdema-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"sent\":\"CREATED\"}}}"
        )

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success)

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(mockClient, times(1)).resolve(any(), eq(evaluationContext))

        confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "empty", evaluationContext)
        advanceUntilIdle()
        verify(mockClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testOnProcessBatchOnInitAndEval() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(cacheFileData)
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token2")
            )
        )

        val evaluationContext = ImmutableContext("foo")

        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()
        confidenceFeatureProvider.getStringEvaluation(
            "fdema-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )

        advanceUntilIdle()
        verify(mockClient, times(0)).apply(any(), eq("token1"))
        verify(mockClient, times(2)).apply(any(), eq("token2"))
        verify(mockClient, times(1)).apply(any(), eq("token3"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testOnProcessBatchOnInit() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(cacheFileData)
        whenever(mockClient.apply(any(), any())).thenReturn(Result.Success)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val test = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            client = mockClient,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher
        )

        advanceUntilIdle()
        verify(mockClient, times(0)).apply(any(), eq("token1"))
        verify(mockClient, times(1)).apply(any(), eq("token2"))
        verify(mockClient, times(1)).apply(any(), eq("token3"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testMatchingRootObject() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )
        confidenceFeatureProvider.initialize(ImmutableContext("foo"))
        advanceUntilIdle()
        val evalRootObject = confidenceFeatureProvider
            .getObjectEvaluation(
                "fdema-kotlin-flag-1",
                Value.Structure(mapOf()),
                ImmutableContext("foo")
            )

        assertEquals(resolvedValueAsMap, evalRootObject.value.asStructure())
        assertEquals(Reason.TARGETING_MATCH.toString(), evalRootObject.reason)
        assertEquals("flags/fdema-kotlin-flag-1/variants/variant-1", evalRootObject.variant)
        assertNull(evalRootObject.errorMessage)
        assertNull(evalRootObject.errorCode)
    }

    @Test
    fun testStale() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )

        // Simulate a case where the context in the cache is not synced with the evaluation's context
        cache.refresh(resolvedFlags.list, "token2", ImmutableContext("user1"))
        val evalString = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystring", "default", ImmutableContext("user2"))
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation("fdema-kotlin-flag-1.myboolean", true, ImmutableContext("user2"))
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation("fdema-kotlin-flag-1.myinteger", 1, ImmutableContext("user2"))
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation("fdema-kotlin-flag-1.mydouble", 7.28, ImmutableContext("user2"))
        val evalDate = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mydate", "default1", ImmutableContext("user2"))
        val evalObject = confidenceFeatureProvider.getObjectEvaluation("fdema-kotlin-flag-1.mystruct", Value.Structure(mapOf()), ImmutableContext("user2"))
        val evalNested = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mystruct.innerString", "default2", ImmutableContext("user2"))
        val evalNull = confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-1.mynull", "default3", ImmutableContext("user2"))

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
    fun testNonMatching() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )

        val resolvedNonMatchingFlags = Flags(
            listOf(
                ResolvedFlag(
                    flag = "fdema-kotlin-flag-1",
                    variant = "",
                    ImmutableStructure(mutableMapOf()),
                    ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH
                )
            )
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedNonMatchingFlags, "token1")
            )
        )

        confidenceFeatureProvider.initialize(ImmutableContext("user1"))
        advanceUntilIdle()

        val evalString = confidenceFeatureProvider
            .getStringEvaluation(
                "fdema-kotlin-flag-1.mystring",
                "default",
                ImmutableContext("user1")
            )

        assertNull(evalString.errorMessage)
        assertNull(evalString.errorCode)
        assertNull(evalString.variant)
        assertEquals("default", evalString.value)
        assertEquals(Reason.DEFAULT.toString(), evalString.reason)
    }

    @Test
    fun testFlagNotFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )

        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )
        // Simulate a case where the context in the cache is not synced with the evaluation's context
        // This shouldn't have an effect in this test, given that not found values are priority over stale values
        cache.refresh(resolvedFlags.list, "token2", ImmutableContext("user1"))
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default", ImmutableContext("user2"))
        }
        assertEquals("Could not find flag named: fdema-kotlin-flag-2", ex.message)
    }

    @Test
    fun testErrorInNetwork() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenThrow(Error())
        confidenceFeatureProvider.initialize(ImmutableContext("user1"))
        advanceUntilIdle()
        val ex = assertThrows(FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation("fdema-kotlin-flag-2.mystring", "default", ImmutableContext("user1"))
        }
        assertEquals("Could not find flag named: fdema-kotlin-flag-2", ex.message)
    }

    @Test
    fun whenResolveIsNotModifiedDoNotUpdateCache() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val cache = mock<InMemoryCache>()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = cache,
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveResponse.NotModified)
        confidenceFeatureProvider.initialize(ImmutableContext("user1"))
        advanceUntilIdle()
        verify(cache, never()).refresh(any(), any(), any())
    }

    @Test
    fun testValueNotFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(
            ResolveResponse.Resolved(
                ResolveFlags(resolvedFlags, "token1")
            )
        )
        confidenceFeatureProvider.initialize(ImmutableContext("user2"))
        advanceUntilIdle()
        val ex = assertThrows(ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "fdema-kotlin-flag-1.wrongid",
                "default",
                ImmutableContext("user2")
            )
        }
        assertEquals("Unable to parse flag value: wrongid", ex.message)
    }

    @Test
    fun testValueNotFoundLongPath() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            clientSecret = "",
            cache = InMemoryCache(),
            eventsPublisher = EventHandler.eventsPublisher(testDispatcher),
            dispatcher = testDispatcher,
            client = mockClient
        )
        whenever(mockClient.resolve(eq(listOf()), any())).thenReturn(ResolveResponse.Resolved(ResolveFlags(resolvedFlags, "token1")))
        confidenceFeatureProvider.initialize(ImmutableContext("user2"))
        advanceUntilIdle()
        val ex = assertThrows(ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "fdema-kotlin-flag-1.mystring.extrapath",
                "default",
                ImmutableContext("user2")
            )
        }
        assertEquals("Unable to parse flag value: mystring/extrapath", ex.message)
    }
}