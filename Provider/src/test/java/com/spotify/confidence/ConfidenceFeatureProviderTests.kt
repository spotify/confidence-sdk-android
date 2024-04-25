@file:OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class
)

package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.apply.EventStatus
import com.spotify.confidence.apply.FlagsAppliedMap
import com.spotify.confidence.cache.APPLY_FILE_NAME
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.ConfidenceRegion
import com.spotify.confidence.client.ConfidenceValueMap
import com.spotify.confidence.client.FlagApplierClient
import com.spotify.confidence.client.Flags
import com.spotify.confidence.client.ResolveReason
import com.spotify.confidence.client.ResolvedFlag
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.exceptions.ErrorCode
import dev.openfeature.sdk.exceptions.OpenFeatureError
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.time.Instant

private const val cacheFileData = "{\n" +
    "  \"token1\": {\n" +
    "    \"test-kotlin-flag-0\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.443Z\",\n" +
    "      \"eventStatus\": \"SENT\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"token2\": {\n" +
    "    \"test-kotlin-flag-2\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.444Z\",\n" +
    "      \"eventStatus\": \"SENT\"\n" +
    "    },\n" +
    "    \"test-kotlin-flag-3\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.445Z\",\n" +
    "      \"eventStatus\": \"CREATED\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"token3\": {\n" +
    "    \"test-kotlin-flag-4\": {\n" +
    "      \"time\": \"2023-06-26T11:55:33.446Z\",\n" +
    "      \"eventStatus\": \"CREATED\"\n" +
    "    }\n" +
    "  }\n" +
    "}\n"

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConfidenceFeatureProviderTests {
    private val flagApplierClient: FlagApplierClient = mock()
    private val flagResolverClient: FlagResolver = mock()
    private val mockContext: Context = mock()
    private val instant = Instant.parse("2023-03-01T14:01:46.645Z")
    private val blueStringValues = mutableMapOf(
        "mystring" to ConfidenceValue.String("blue")
    )
    private val resolvedValueAsMap: ConfidenceValueMap = mutableMapOf(
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
    )
    private val resolvedFlags = Flags(
        listOf(
            ResolvedFlag(
                "test-kotlin-flag-1",
                "flags/test-kotlin-flag-1/variants/variant-1",
                resolvedValueAsMap,
                ResolveReason.RESOLVE_REASON_MATCH
            )
        )
    )

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    private fun getConfidence(dispatcher: CoroutineDispatcher): Confidence = Confidence(
        clientSecret = "",
        dispatcher = dispatcher,
        eventSenderEngine = mock(),
        flagResolver = flagResolverClient,
        flagApplierClient = flagApplierClient,
        diskStorage = FileDiskStorage.create(mockContext),
        region = ConfidenceRegion.EUROPE
    )

    @Test
    fun testMatching() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val eventHandler = EventHandler(testDispatcher)
        val mockConfidence = getConfidence(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(
            flagResolverClient.resolve(
                eq(listOf()),
                eq(ImmutableContext("foo").toConfidenceContext().map)
            )
        ).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        confidenceFeatureProvider.initialize(ImmutableContext("foo"))
        advanceUntilIdle()
        verify(flagResolverClient, times(1))
            .resolve(
                any(),
                eq(ImmutableContext("foo").toConfidenceContext().map)
            )
        val evalString = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            ImmutableContext("foo")
        )
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation(
            "test-kotlin-flag-1.myboolean",
            true,
            ImmutableContext("foo")
        )
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation(
            "test-kotlin-flag-1.myinteger",
            1,
            ImmutableContext("foo")
        )
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation(
            "test-kotlin-flag-1.mydouble",
            7.28,
            ImmutableContext("foo")
        )
        val evalDate = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mydate",
            "error",
            ImmutableContext("foo")
        )
        val evalObject = confidenceFeatureProvider.getObjectEvaluation(
            "test-kotlin-flag-1.mystruct",
            Value.Structure(mapOf()),
            ImmutableContext("foo")
        )
        val evalNested = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystruct.innerString",
            "error",
            ImmutableContext("foo")
        )
        val evalNull = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mynull",
            "error",
            ImmutableContext("foo")
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))

        assertEquals("red", evalString.value)
        assertEquals(false, evalBool.value)
        assertEquals(7, evalInteger.value)
        assertEquals(3.14, evalDouble.value)
        assertEquals("2023-03-01T14:01:46.645Z", evalDate.value)
        assertEquals(
            Value.Structure(mapOf("innerString" to Value.String("innerValue"))),
            evalObject.value
        )
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

        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalBool.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalInteger.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDouble.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDate.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalObject.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNested.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNull.variant)

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
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            confidence = mockConfidence,
            dispatcher = testDispatcher
        )
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        val context = ImmutableContext("foo").toConfidenceContext().map
        whenever(flagResolverClient.resolve(eq(listOf()), eq(context))).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Failure())

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), eq(context))

        val evalString = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation(
            "test-kotlin-flag-1.myboolean",
            true,
            evaluationContext
        )
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation(
            "test-kotlin-flag-1.myinteger",
            1,
            evaluationContext
        )
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation(
            "test-kotlin-flag-1.mydouble",
            7.28,
            evaluationContext
        )
        val evalDate = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mydate",
            "error",
            evaluationContext
        )
        val evalObject = confidenceFeatureProvider.getObjectEvaluation(
            "test-kotlin-flag-1.mystruct",
            Value.Structure(mapOf()),
            evaluationContext
        )
        val evalNested = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystruct.innerString",
            "error",
            evaluationContext
        )
        val evalNull = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mynull",
            "error",
            evaluationContext
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(8)).apply(any(), eq("token1"))
        val expectedStatus = Json.decodeFromString<FlagsAppliedMap>(cacheFile.readText())["token1"]
            ?.get("test-kotlin-flag-1")?.eventStatus
        assertEquals(EventStatus.CREATED, expectedStatus)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        // Evaluate a flag property in order to trigger an apply
        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "empty",
            evaluationContext
        )

        advanceUntilIdle()
        val captor = argumentCaptor<List<AppliedFlag>>()
        verify(flagApplierClient, times(9)).apply(captor.capture(), eq("token1"))
        assertEquals(1, captor.firstValue.count())
        assertEquals("test-kotlin-flag-1", captor.firstValue.first().flag)

        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
        assertEquals("red", evalString.value)
        assertEquals(false, evalBool.value)
        assertEquals(7, evalInteger.value)
        assertEquals(3.14, evalDouble.value)
        assertEquals("2023-03-01T14:01:46.645Z", evalDate.value)
        assertEquals(
            Value.Structure(mapOf("innerString" to Value.String("innerValue"))),
            evalObject.value
        )
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

        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalBool.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalInteger.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDouble.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDate.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalObject.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNested.variant)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNull.variant)

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
    fun testNewContextFetchValuesAgain() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        val evaluationContext1 = ImmutableContext("foo")
        val evaluationContext2 = ImmutableContext("bar")
        val context1 = ImmutableContext("foo").toConfidenceContext().map
        val context2 = ImmutableContext("bar").toConfidenceContext().map
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), eq(context1))).thenReturn(
            Result.Success(
                FlagResolution(
                    context1,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        val newExpectedValue =
            resolvedFlags.list[0].copy(value = blueStringValues)
        whenever(flagResolverClient.resolve(eq(listOf()), eq(context2))).thenReturn(
            Result.Success(
                FlagResolution(
                    context2,
                    listOf(newExpectedValue),
                    "token1"
                )
            )
        )

        confidenceFeatureProvider.initialize(evaluationContext1)
        advanceUntilIdle()

        val evalString1 = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext1
        )
        assertEquals("red", evalString1.value)
        confidenceFeatureProvider.onContextSet(evaluationContext1, evaluationContext2)
        advanceUntilIdle()
        val evalString2 = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext2
        )
        assertEquals("blue", evalString2.value)
    }

    @Test
    fun testApplyOnMultipleEvaluations() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        val evaluationContext1 = ImmutableContext("foo")
        val evaluationContext2 = ImmutableContext("bar")

        val context1 = ImmutableContext("foo").toConfidenceContext().map

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    evaluationContext1.toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        confidenceFeatureProvider.initialize(evaluationContext1)
        advanceUntilIdle()
        verify(flagResolverClient, times(1)).resolve(any(), eq(context1))

        val evalString1 = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext1
        )
        // Second evaluation shouldn't trigger apply
        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext1
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)

        val captor1 = argumentCaptor<List<AppliedFlag>>()
        verify(flagApplierClient, times(1)).apply(captor1.capture(), eq("token1"))

        assertEquals(1, captor1.firstValue.count())
        assertEquals("test-kotlin-flag-1", captor1.firstValue.first().flag)
        assertEquals("red", evalString1.value)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalString1.reason)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString1.variant)
        assertNull(evalString1.errorMessage)
        assertNull(evalString1.errorCode)

        whenever(flagResolverClient.resolve(eq(listOf()), eq(evaluationContext2.toConfidenceContext().map))).thenReturn(
            Result.Success(
                FlagResolution(
                    evaluationContext2.toConfidenceContext().map,
                    resolvedFlags.list,
                    "token2"
                )
            )
        )
        confidenceFeatureProvider.onContextSet(evaluationContext1, evaluationContext2)
        advanceUntilIdle()
        verify(flagResolverClient, times(1))
            .resolve(any(), eq(evaluationContext2.toConfidenceContext().map))

        // Third evaluation with different context should trigger apply
        val evalString2 = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext2
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token2"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
        val captor = argumentCaptor<List<AppliedFlag>>()
        verify(flagApplierClient, times(1)).apply(captor.capture(), eq("token2"))

        assertEquals(1, captor.firstValue.count())
        assertEquals("test-kotlin-flag-1", captor.firstValue.first().flag)
        assertEquals("red", evalString2.value)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalString2.reason)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString2.variant)
        assertNull(evalString2.errorMessage)
        assertNull(evalString2.errorCode)
    }

    @Test
    fun confidenceContextRemovedWorks() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            confidence = mockConfidence,
            dispatcher = testDispatcher
        )
        val evaluationContext = ImmutableContext("foo", mapOf("hello" to Value.String("world")))
        val context = evaluationContext.toConfidenceContext().map
        whenever(flagResolverClient.resolve(eq(listOf()), eq(context))).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()
        assertEquals(mockConfidence.getContext(), context)
        verify(flagResolverClient, times(1)).resolve(any(), eq(context))
        val newContext = ImmutableContext("foo").toConfidenceContext().map
        confidenceFeatureProvider.onContextSet(evaluationContext, ImmutableContext("foo"))
        advanceUntilIdle()
        assertEquals(mockConfidence.getContext(), newContext)
        verify(flagResolverClient, times(1)).resolve(any(), eq(newContext))
    }

    @Test
    fun testStaleValueReturnValueAndStaleReason() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            confidence = mockConfidence,
            dispatcher = testDispatcher
        )
        val context = ImmutableContext("foo").toConfidenceContext().map
        whenever(flagResolverClient.resolve(eq(listOf()), eq(context))).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), eq(context))

        val evalString = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )
        assertEquals(evalString.reason, Reason.TARGETING_MATCH.name)
        assertEquals(evalString.value, "red")

        mockConfidence.putContext("hello", ConfidenceValue.String("new context"))
        val newContextEval = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )
        assertEquals(newContextEval.reason, Reason.STALE.name)
        assertEquals(newContextEval.value, "red")
        verify(flagResolverClient, times(2)).resolve(any(), any())
    }

    @Test
    fun testApplyFromStoredCache() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"CREATED\"}}}"
        )

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "empty",
            evaluationContext
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testApplyFromStoredCacheSendingStatus() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"SENDING\"}}}"
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "empty",
            evaluationContext
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testNotSendDuplicateWhileSending() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"CREATED\"}}}"
        )
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        whenever(flagApplierClient.apply(any(), any())).then { }
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "empty",
            evaluationContext
        )
        advanceUntilIdle()
        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.myboolean",
            "false",
            evaluationContext
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testDoSendAgainWhenNetworkRequestFailed() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"CREATED\"}}}"
        )

        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Failure())
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        val evaluationContext = ImmutableContext("foo")
        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "empty",
            evaluationContext
        )
        advanceUntilIdle()
        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.myboolean",
            "false",
            evaluationContext
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(3)).apply(any(), eq("token1"))
    }

    @Test
    fun testOnProcessBatchOnInitAndEval() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(cacheFileData)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token2"
                )
            )
        )

        val evaluationContext = ImmutableContext("foo")

        confidenceFeatureProvider.initialize(evaluationContext)
        advanceUntilIdle()
        confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            evaluationContext
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(0)).apply(any(), eq("token1"))
        verify(flagApplierClient, times(2)).apply(any(), eq("token2"))
        verify(flagApplierClient, times(1)).apply(any(), eq("token3"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testOnProcessBatchOnInit() = runTest {
        val cacheFile = File(mockContext.filesDir, APPLY_FILE_NAME)
        cacheFile.writeText(cacheFileData)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        ConfidenceFeatureProvider.create(
            context = mockContext,
            confidence = mockConfidence,
            cache = InMemoryCache(),
            eventHandler = eventHandler,
            dispatcher = testDispatcher
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(0)).apply(any(), eq("token1"))
        verify(flagApplierClient, times(1)).apply(any(), eq("token2"))
        verify(flagApplierClient, times(1)).apply(any(), eq("token3"))
        assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testMatchingRootObject() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("foo").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        confidenceFeatureProvider.initialize(ImmutableContext("foo"))
        advanceUntilIdle()
        val evalRootObject = confidenceFeatureProvider
            .getObjectEvaluation(
                "test-kotlin-flag-1",
                Value.Structure(mapOf()),
                ImmutableContext("foo")
            )

        assertEquals(ConfidenceValue.Struct(resolvedValueAsMap).toValue(), evalRootObject.value)
        assertEquals(Reason.TARGETING_MATCH.toString(), evalRootObject.reason)
        assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalRootObject.variant)
        assertNull(evalRootObject.errorMessage)
        assertNull(evalRootObject.errorCode)
    }

    @Test
    fun testError() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val eventHandler = EventHandler(testDispatcher)
        val mockConfidence = getConfidence(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("user1").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        // Simulate a case where the context in the cache is not synced with the evaluation's context
        val cacheData = FlagResolution(
            flags = resolvedFlags.list,
            resolveToken = "token2",
            context = ImmutableContext("user1").toConfidenceContext().map
        )
        cache.refresh(cacheData)
        val evalString = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            ImmutableContext("user2")
        )
        val evalBool = confidenceFeatureProvider.getBooleanEvaluation(
            "test-kotlin-flag-1.myboolean",
            true,
            ImmutableContext("user2")
        )
        val evalInteger = confidenceFeatureProvider.getIntegerEvaluation(
            "test-kotlin-flag-1.myinteger",
            1,
            ImmutableContext("user2")
        )
        val evalDouble = confidenceFeatureProvider.getDoubleEvaluation(
            "test-kotlin-flag-1.mydouble",
            7.28,
            ImmutableContext("user2")
        )
        val evalDate = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mydate",
            "default1",
            ImmutableContext("user2")
        )
        val evalObject = confidenceFeatureProvider.getObjectEvaluation(
            "test-kotlin-flag-1.mystruct",
            Value.Structure(mapOf()),
            ImmutableContext("user2")
        )
        val evalNested = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystruct.innerString",
            "default2",
            ImmutableContext("user2")
        )
        val evalNull = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mynull",
            "default3",
            ImmutableContext("user2")
        )

        assertEquals("default", evalString.value)
        assertEquals(true, evalBool.value)
        assertEquals(1, evalInteger.value)
        assertEquals(7.28, evalDouble.value)
        assertEquals("default1", evalDate.value)
        assertEquals(Value.Structure(mapOf()), evalObject.value)
        assertEquals("default2", evalNested.value)
        assertEquals("default3", evalNull.value)

        assertEquals(Reason.ERROR.toString(), evalString.reason)
        assertEquals(Reason.ERROR.toString(), evalBool.reason)
        assertEquals(Reason.ERROR.toString(), evalInteger.reason)
        assertEquals(Reason.ERROR.toString(), evalDouble.reason)
        assertEquals(Reason.ERROR.toString(), evalDate.reason)
        assertEquals(Reason.ERROR.toString(), evalObject.reason)
        assertEquals(Reason.ERROR.toString(), evalNested.reason)
        assertEquals(Reason.ERROR.toString(), evalNull.reason)

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

        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalString.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalBool.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalInteger.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalDouble.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalDate.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalObject.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalNested.errorCode)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, evalNull.errorCode)
    }

    @Test
    fun testInvalidTargetingKey() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val eventHandler = EventHandler(testDispatcher)
        val mockConfidence = getConfidence(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            cache = cache,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )

        val resolvedFlagInvalidKey = Flags(
            listOf(
                ResolvedFlag(
                    "test-kotlin-flag-1",
                    "",
                    mapOf(),
                    ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR
                )
            )
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("user1").toConfidenceContext().map,
                    resolvedFlagInvalidKey.list,
                    "token1"
                )
            )
        )

        val cacheData = FlagResolution(
            flags = resolvedFlagInvalidKey.list,
            resolveToken = "token",
            context = ImmutableContext("user1").toConfidenceContext().map
        )
        cache.refresh(cacheData)
        val evalString = confidenceFeatureProvider.getStringEvaluation(
            "test-kotlin-flag-1.mystring",
            "default",
            ImmutableContext("user1")
        )
        assertEquals("default", evalString.value)
        assertEquals(Reason.ERROR.toString(), evalString.reason)
        assertEquals(evalString.errorMessage, "Invalid targeting key")
        assertEquals(evalString.errorCode, ErrorCode.INVALID_CONTEXT)
    }

    @Test
    fun testNonMatching() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            cache = cache,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )

        val resolvedNonMatchingFlags = Flags(
            listOf(
                ResolvedFlag(
                    flag = "test-kotlin-flag-1",
                    variant = "",
                    mapOf(),
                    ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH
                )
            )
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("user1").toConfidenceContext().map,
                    resolvedNonMatchingFlags.list,
                    "token1"
                )
            )
        )

        confidenceFeatureProvider.initialize(ImmutableContext("user1"))
        advanceUntilIdle()

        val evalString = confidenceFeatureProvider
            .getStringEvaluation(
                "test-kotlin-flag-1.mystring",
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
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val cache = InMemoryCache()
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            cache = cache,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("user1").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        // Simulate a case where the context in the cache is not synced with the evaluation's context
        // This shouldn't have an effect in this test, given that not found values are priority over stale values
        val cacheData = FlagResolution(
            ImmutableContext("user1").toConfidenceContext().map,
            resolvedFlags.list,
            "token2"
        )
        cache.refresh(cacheData)
        val ex = assertThrows(OpenFeatureError.FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "test-kotlin-flag-2.mystring",
                "default",
                ImmutableContext("user2")
            )
        }
        assertEquals("Could not find flag named: test-kotlin-flag-2", ex.message)
    }

    @Test
    fun testErrorInNetwork() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            cache = cache,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenThrow(Error(""))
        confidenceFeatureProvider.initialize(ImmutableContext("user1"))
        advanceUntilIdle()
        val ex = assertThrows(OpenFeatureError.FlagNotFoundError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "test-kotlin-flag-2.mystring",
                "default",
                ImmutableContext("user1")
            )
        }
        assertEquals("Could not find flag named: test-kotlin-flag-2", ex.message)
    }

    @Test
    fun testValueNotFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(
            flagResolverClient.resolve(
                eq(listOf()),
                eq(ImmutableContext("user2").toConfidenceContext().map)
            )
        ).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("user2").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        confidenceFeatureProvider.initialize(ImmutableContext("user2"))
        advanceUntilIdle()
        val ex = assertThrows(OpenFeatureError.ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "test-kotlin-flag-1.wrongid",
                "default",
                ImmutableContext("user2")
            )
        }
        assertEquals("Unable to parse flag value: [wrongid]", ex.message)
    }

    @Test
    fun testValueNotFoundLongPath() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val eventHandler = EventHandler(testDispatcher)
        val confidenceFeatureProvider = ConfidenceFeatureProvider.create(
            context = mockContext,
            eventHandler = eventHandler,
            dispatcher = testDispatcher,
            confidence = mockConfidence
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    ImmutableContext("user2").toConfidenceContext().map,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        confidenceFeatureProvider.initialize(ImmutableContext("user2"))
        advanceUntilIdle()
        val ex = assertThrows(OpenFeatureError.ParseError::class.java) {
            confidenceFeatureProvider.getStringEvaluation(
                "test-kotlin-flag-1.mystring.extrapath",
                "default",
                ImmutableContext("user2")
            )
        }
        assertEquals("Unable to parse flag value: [mystring, extrapath]", ex.message)
    }
}