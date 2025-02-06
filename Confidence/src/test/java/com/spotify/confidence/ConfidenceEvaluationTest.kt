package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.ConfidenceError.ErrorCode
import com.spotify.confidence.apply.EventStatus
import com.spotify.confidence.apply.FlagsAppliedMap
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.Flags
import com.spotify.confidence.client.ResolvedFlag
import junit.framework.TestCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
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
internal class ConfidenceEvaluationTest {
    private val flagApplierClient: com.spotify.confidence.client.FlagApplierClient = mock()
    private val flagResolverClient: FlagResolver = mock()
    private val mockContext: Context = mock()
    private val instant = Instant.parse("2023-03-01T14:01:46.645Z")
    private val blueStringValues = mutableMapOf(
        "mystring" to ConfidenceValue.String("blue")
    )
    private val resolvedValueAsMap: com.spotify.confidence.client.ConfidenceValueMap = mutableMapOf(
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
                ResolveReason.RESOLVE_REASON_MATCH,
                shouldApply = true
            )
        )
    )
    private val resolvedFlagsNoApply = Flags(
        listOf(
            ResolvedFlag(
                "test-kotlin-flag-2",
                "flags/test-kotlin-flag-2/variants/variant-1",
                resolvedValueAsMap,
                ResolveReason.RESOLVE_REASON_MATCH,
                shouldApply = false
            )
        )
    )

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    private fun getConfidence(
        dispatcher: CoroutineDispatcher,
        cache: ProviderCache = InMemoryCache(),
        initialContext: Map<String, ConfidenceValue> = mapOf(),
        flagResolver: FlagResolver? = null,
        debugLogger: DebugLoggerFake? = null
    ): Confidence =
        Confidence(
            clientSecret = "",
            dispatcher = dispatcher,
            eventSenderEngine = mock(),
            initialContext = initialContext,
            cache = cache,
            flagResolver = flagResolver ?: flagResolverClient,
            flagApplierClient = flagApplierClient,
            diskStorage = FileDiskStorage.create(mockContext),
            region = ConfidenceRegion.EUROPE,
            debugLogger = debugLogger
        )

    @Test
    fun testMatching() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("targeting_key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(
            testDispatcher,
            initialContext = context
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(
            flagResolverClient.resolve(
                eq(listOf()),
                eq(context)
            )
        ).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        verify(flagResolverClient, times(1))
            .resolve(
                any(),
                eq(context)
            )
        val evalString = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        val evalBool = mockConfidence.getFlag(
            "test-kotlin-flag-1.myboolean",
            true
        )
        val evalInteger = mockConfidence.getFlag(
            "test-kotlin-flag-1.myinteger",
            1
        )
        val evalDouble = mockConfidence.getFlag(
            "test-kotlin-flag-1.mydouble",
            7.28
        )
        val evalDate = mockConfidence.getFlag(
            "test-kotlin-flag-1.mydate",
            "error"
        )
        val evalObject = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystruct",
            ConfidenceValue.Struct(mapOf())
        )
        val evalNested = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystruct.innerString",
            "error"
        )
        val evalNull = mockConfidence.getFlag(
            "test-kotlin-flag-1.mynull",
            "error"
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))

        TestCase.assertEquals("red", evalString.value)
        TestCase.assertEquals(false, evalBool.value)
        TestCase.assertEquals(7, evalInteger.value)
        TestCase.assertEquals(3.14, evalDouble.value)
        TestCase.assertEquals("2023-03-01T14:01:46.645Z", evalDate.value)
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

        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalBool.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalInteger.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDouble.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDate.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalObject.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNested.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNull.variant)

        Assert.assertNull(evalString.errorMessage)
        Assert.assertNull(evalBool.errorMessage)
        Assert.assertNull(evalInteger.errorMessage)
        Assert.assertNull(evalDouble.errorMessage)
        Assert.assertNull(evalDate.errorMessage)
        Assert.assertNull(evalObject.errorMessage)
        Assert.assertNull(evalNested.errorMessage)
        Assert.assertNull(evalNull.errorMessage)

        Assert.assertNull(evalString.errorCode)
        Assert.assertNull(evalBool.errorCode)
        Assert.assertNull(evalInteger.errorCode)
        Assert.assertNull(evalDouble.errorCode)
        Assert.assertNull(evalDate.errorCode)
        Assert.assertNull(evalObject.errorCode)
        Assert.assertNull(evalNested.errorCode)
        Assert.assertNull(evalNull.errorCode)
    }

    @Test
    fun testDelayedApply() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("targeting_key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
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

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), eq(context))

        val evalString = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        val evalBool = mockConfidence.getFlag(
            "test-kotlin-flag-1.myboolean",
            true
        )
        val evalInteger = mockConfidence.getFlag(
            "test-kotlin-flag-1.myinteger",
            1
        )
        val evalDouble = mockConfidence.getFlag(
            "test-kotlin-flag-1.mydouble",
            7.28
        )
        val evalDate = mockConfidence.getFlag(
            "test-kotlin-flag-1.mydate",
            "error"
        )
        val evalObject = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystruct",
            ConfidenceValue.Struct(mapOf())
        )
        val evalNested = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystruct.innerString",
            "error"
        )
        val evalNull = mockConfidence.getFlag(
            "test-kotlin-flag-1.mynull",
            "error"
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(8)).apply(any(), eq("token1"))
        val expectedStatus = Json.decodeFromString<FlagsAppliedMap>(cacheFile.readText())["token1"]
            ?.get("test-kotlin-flag-1")?.eventStatus
        TestCase.assertEquals(EventStatus.CREATED, expectedStatus)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        // Evaluate a flag property in order to trigger an apply
        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "empty"
        )

        advanceUntilIdle()
        val captor = argumentCaptor<List<AppliedFlag>>()
        verify(flagApplierClient, times(9)).apply(captor.capture(), eq("token1"))
        TestCase.assertEquals(1, captor.firstValue.count())
        TestCase.assertEquals("test-kotlin-flag-1", captor.firstValue.first().flag)

        TestCase.assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
        TestCase.assertEquals("red", evalString.value)
        TestCase.assertEquals(false, evalBool.value)
        TestCase.assertEquals(7, evalInteger.value)
        TestCase.assertEquals(3.14, evalDouble.value)
        TestCase.assertEquals("2023-03-01T14:01:46.645Z", evalDate.value)
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

        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalBool.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalInteger.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDouble.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalDate.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalObject.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNested.variant)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalNull.variant)

        Assert.assertNull(evalString.errorMessage)
        Assert.assertNull(evalBool.errorMessage)
        Assert.assertNull(evalInteger.errorMessage)
        Assert.assertNull(evalDouble.errorMessage)
        Assert.assertNull(evalDate.errorMessage)
        Assert.assertNull(evalObject.errorMessage)
        Assert.assertNull(evalNested.errorMessage)
        Assert.assertNull(evalNull.errorMessage)

        Assert.assertNull(evalString.errorCode)
        Assert.assertNull(evalBool.errorCode)
        Assert.assertNull(evalInteger.errorCode)
        Assert.assertNull(evalDouble.errorCode)
        Assert.assertNull(evalDate.errorCode)
        Assert.assertNull(evalObject.errorCode)
        Assert.assertNull(evalNested.errorCode)
        Assert.assertNull(evalNull.errorCode)
    }

    @Test
    fun testNewContextFetchValuesAgain() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context1 = mapOf("key" to ConfidenceValue.String("foo"))
        val context2 = mapOf("key" to ConfidenceValue.String("bar"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context1)
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

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        val evalString1 = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        TestCase.assertEquals("red", evalString1.value)
        mockConfidence.putContext(context2)
        advanceUntilIdle()
        val evalString2 = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        TestCase.assertEquals("blue", evalString2.value)
    }

    @Test
    fun testApplyOnMultipleEvaluations() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context1 = mapOf("key" to ConfidenceValue.String("foo"))
        val context2 = mapOf("key" to ConfidenceValue.String("bar"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context1)

        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context1,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        verify(flagResolverClient, times(1)).resolve(any(), eq(context1))

        val evalString1 = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        // Second evaluation shouldn't trigger apply
        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
        TestCase.assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)

        val captor1 = argumentCaptor<List<AppliedFlag>>()
        verify(flagApplierClient, times(1)).apply(captor1.capture(), eq("token1"))

        TestCase.assertEquals(1, captor1.firstValue.count())
        TestCase.assertEquals("test-kotlin-flag-1", captor1.firstValue.first().flag)
        TestCase.assertEquals("red", evalString1.value)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalString1.reason)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString1.variant)
        Assert.assertNull(evalString1.errorMessage)
        Assert.assertNull(evalString1.errorCode)

        whenever(
            flagResolverClient.resolve(
                eq(listOf()),
                eq(context2)
            )
        ).thenReturn(
            Result.Success(
                FlagResolution(
                    context2,
                    resolvedFlags.list,
                    "token2"
                )
            )
        )
        mockConfidence.putContext(context2)
        advanceUntilIdle()
        verify(flagResolverClient, times(1))
            .resolve(any(), eq(context2))

        // Third evaluation with different context should trigger apply
        val evalString2 = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token2"))
        TestCase.assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
        val captor = argumentCaptor<List<AppliedFlag>>()
        verify(flagApplierClient, times(1)).apply(captor.capture(), eq("token2"))

        TestCase.assertEquals(1, captor.firstValue.count())
        TestCase.assertEquals("test-kotlin-flag-1", captor.firstValue.first().flag)
        TestCase.assertEquals("red", evalString2.value)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalString2.reason)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalString2.variant)
        Assert.assertNull(evalString2.errorMessage)
        Assert.assertNull(evalString2.errorCode)
    }

    @Test
    fun testWithSlowResolvesWeCancelTheFirstResolveOnNewContextChangesOfConfidence() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flagResolver = object : FlagResolver {
            var callCount = 0
            var returnCount = 0
            var latestCalledContext = mapOf<String, ConfidenceValue>()
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                latestCalledContext = context
                callCount++
                delay(2000)
                return Result.Success(
                    FlagResolution(
                        context,
                        resolvedFlags.list,
                        "token1"
                    )
                ).also {
                    returnCount++
                }
            }
        }
        val mockConfidence = getConfidence(
            testDispatcher,
            flagResolver = flagResolver
        )

        val context1 = mapOf("key" to ConfidenceValue.String("foo"))
        val context2 = mapOf("key" to ConfidenceValue.String("foo2"))
        val context3 = mapOf("key" to ConfidenceValue.String("foo3"))
        val context4 = mapOf("key" to ConfidenceValue.String("foo4"))
        mockConfidence.putContext(context1)
        mockConfidence.putContext(context2)
        mockConfidence.putContext(context3)
        mockConfidence.putContext(context4)
        advanceUntilIdle()
        TestCase.assertEquals(mockConfidence.getContext(), context4)
        TestCase.assertEquals(1, flagResolver.returnCount)
        TestCase.assertEquals(4, flagResolver.callCount)
        TestCase.assertEquals(context4, flagResolver.latestCalledContext)
    }

    @Test
    fun testStaleValueReturnValueAndStaleReason() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)
        whenever(flagResolverClient.resolve(eq(listOf()), eq(context))).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), eq(context))

        val evalString = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        TestCase.assertEquals(evalString.reason, ResolveReason.RESOLVE_REASON_MATCH)
        TestCase.assertEquals(evalString.value, "red")

        mockConfidence.putContext("hello", ConfidenceValue.String("new context"))
        val newContextEval = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        TestCase.assertEquals(newContextEval.reason, ResolveReason.RESOLVE_REASON_STALE)
        TestCase.assertEquals(newContextEval.value, "red")
        verify(flagResolverClient, times(2)).resolve(any(), any())
    }

    @Test
    fun testApplyFromStoredCache() = runTest {
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"CREATED\"}}}"
        )

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "empty"
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testApplyFromStoredCacheSendingStatus() = runTest {
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"SENDING\"}}}"
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "empty"
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testNotSendDuplicateWhileSending() = runTest {
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"CREATED\"}}}"
        )
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)
        whenever(flagApplierClient.apply(any(), any())).then { }

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "empty"
        )
        advanceUntilIdle()
        mockConfidence.getFlag(
            "test-kotlin-flag-1.myboolean",
            "false"
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(1)).apply(any(), eq("token1"))
    }

    @Test
    fun testDoSendAgainWhenNetworkRequestFailed() = runTest {
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        cacheFile.writeText(
            "{\"token1\":{\"test-kotlin-flag-1\":{\"time\":\"2023-06-26T11:55:33.184774Z\",\"eventStatus\":\"CREATED\"}}}"
        )

        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Failure())
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockConfidence = getConfidence(testDispatcher)
        val context = mapOf("key" to ConfidenceValue.String("foo"))

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        verify(flagResolverClient, times(1)).resolve(any(), any())

        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "empty"
        )
        advanceUntilIdle()
        mockConfidence.getFlag(
            "test-kotlin-flag-1.myboolean",
            "false"
        )
        advanceUntilIdle()
        verify(flagApplierClient, times(3)).apply(any(), eq("token1"))
    }

    @Test
    fun testOnProcessBatchOnInitAndEval() = runTest {
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        cacheFile.writeText(cacheFileData)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)

        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token2"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )

        advanceUntilIdle()
        verify(flagApplierClient, times(0)).apply(any(), eq("token1"))
        verify(flagApplierClient, times(2)).apply(any(), eq("token2"))
        verify(flagApplierClient, times(1)).apply(any(), eq("token3"))
        TestCase.assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testOnProcessBatchOnInit() = runTest {
        val cacheFile = File(mockContext.filesDir, "confidence_apply_cache.json")
        cacheFile.writeText(cacheFileData)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(
            testDispatcher,
            initialContext = context,
            cache = InMemoryCache()
        )

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        verify(flagApplierClient, times(0)).apply(any(), eq("token1"))
        verify(flagApplierClient, times(1)).apply(any(), eq("token2"))
        verify(flagApplierClient, times(1)).apply(any(), eq("token3"))
        TestCase.assertEquals(0, Json.parseToJsonElement(cacheFile.readText()).jsonObject.size)
    }

    @Test
    fun testMatchingRootObject() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        val evalRootObject = mockConfidence.getFlag(
            "test-kotlin-flag-1",
            ConfidenceValue.Struct(mapOf())
        )

        TestCase.assertEquals(
            ConfidenceValue.Struct(resolvedValueAsMap),
            evalRootObject.value
        )
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, evalRootObject.reason)
        TestCase.assertEquals("flags/test-kotlin-flag-1/variants/variant-1", evalRootObject.variant)
        Assert.assertNull(evalRootObject.errorMessage)
        Assert.assertNull(evalRootObject.errorCode)
    }

    @Test
    fun testError() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context, cache = cache)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        val evalString = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        val evalBool = mockConfidence.getFlag(
            "test-kotlin-flag-1.myboolean",
            true
        )
        val evalInteger = mockConfidence.getFlag(
            "test-kotlin-flag-1.myinteger",
            1
        )
        val evalDouble = mockConfidence.getFlag(
            "test-kotlin-flag-1.mydouble",
            7.28
        )
        val evalDate = mockConfidence.getFlag(
            "test-kotlin-flag-1.mydate",
            "default1"
        )
        val evalObject = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystruct",
            ConfidenceValue.Struct(mapOf())
        )
        val evalNested = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystruct.innerString",
            "default2"
        )
        val evalNull = mockConfidence.getFlag(
            "test-kotlin-flag-1.mynull",
            "default3"
        )

        TestCase.assertEquals("default", evalString.value)
        TestCase.assertEquals(true, evalBool.value)
        TestCase.assertEquals(1, evalInteger.value)
        TestCase.assertEquals(7.28, evalDouble.value)
        TestCase.assertEquals("default1", evalDate.value)
        TestCase.assertEquals(ConfidenceValue.Struct(mapOf()), evalObject.value)
        TestCase.assertEquals("default2", evalNested.value)
        TestCase.assertEquals("default3", evalNull.value)

        TestCase.assertEquals(ResolveReason.ERROR, evalString.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalBool.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalInteger.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalDouble.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalDate.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalObject.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalNested.reason)
        TestCase.assertEquals(ResolveReason.ERROR, evalNull.reason)

        Assert.assertNull(evalString.variant)
        Assert.assertNull(evalBool.variant)
        Assert.assertNull(evalInteger.variant)
        Assert.assertNull(evalDouble.variant)
        Assert.assertNull(evalDate.variant)
        Assert.assertNull(evalObject.variant)
        Assert.assertNull(evalNested.variant)
        Assert.assertNull(evalNull.variant)

        Assert.assertNull(evalString.errorMessage)
        Assert.assertNull(evalBool.errorMessage)
        Assert.assertNull(evalInteger.errorMessage)
        Assert.assertNull(evalDouble.errorMessage)
        Assert.assertNull(evalDate.errorMessage)
        Assert.assertNull(evalObject.errorMessage)
        Assert.assertNull(evalNested.errorMessage)
        Assert.assertNull(evalNull.errorMessage)

        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalString.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalBool.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalInteger.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalDouble.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalDate.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalObject.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalNested.errorCode)
        TestCase.assertEquals(ErrorCode.PROVIDER_NOT_READY, evalNull.errorCode)
    }

    @Test
    fun testNetworkErrorIsLogged() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val debugLoggerFake = DebugLoggerFake()
        val mockConfidence = getConfidence(
            testDispatcher,
            initialContext = context,
            cache = cache,
            debugLogger = debugLoggerFake
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(
            flagResolverClient.resolve(
                eq(listOf()),
                any()
            )
        ).thenThrow(Error("Something went terribly wrong on the Internetz"))

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        Assert.assertEquals(1, debugLoggerFake.messagesLogged.size)
        Assert.assertEquals("Network error", debugLoggerFake.messagesLogged.first().message)
        Assert.assertEquals(
            "Something went terribly wrong on the Internetz",
            debugLoggerFake.messagesLogged.first().throwable?.message
        )
    }

    @Test
    fun testInvalidTargetingKey() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(
            dispatcher = testDispatcher,
            cache = cache,
            initialContext = context
        )

        val resolvedFlagInvalidKey = Flags(
            listOf(
                ResolvedFlag(
                    "test-kotlin-flag-1",
                    "",
                    mapOf(),
                    ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR,
                    shouldApply = true
                )
            )
        )
        mockConfidence.fetchAndActivate()
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlagInvalidKey.list,
                    "token1"
                )
            )
        )

        val cacheData = FlagResolution(
            flags = resolvedFlagInvalidKey.list,
            resolveToken = "token",
            context = context
        )
        cache.refresh(cacheData)
        val evalString = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )
        TestCase.assertEquals("default", evalString.value)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR, evalString.reason)
        TestCase.assertEquals(evalString.errorMessage, "Invalid targeting key")
        TestCase.assertEquals(evalString.errorCode, ErrorCode.INVALID_CONTEXT)
    }

    @Test
    fun testNonMatching() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)

        val reason = ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH

        val resolvedNonMatchingFlags = Flags(
            listOf(
                ResolvedFlag(
                    flag = "test-kotlin-flag-1",
                    variant = "",
                    mapOf(),
                    reason,
                    true
                )
            )
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedNonMatchingFlags.list,
                    "token1"
                )
            )
        )

        mockConfidence.fetchAndActivate()
        advanceUntilIdle()

        val evalString = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring",
            "default"
        )

        Assert.assertNull(evalString.errorMessage)
        Assert.assertNull(evalString.errorCode)
        Assert.assertNull(evalString.variant)
        TestCase.assertEquals("default", evalString.value)
        TestCase.assertEquals(reason, evalString.reason)
    }

    @Test
    fun testFlagNotFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val cache = InMemoryCache()
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val context1 = mapOf("key" to ConfidenceValue.String("bar"))
        val mockConfidence = getConfidence(testDispatcher, cache = cache, initialContext = context)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )

        // Simulate a case where the context in the cache is not synced with the evaluation's context
        // This shouldn't have an effect in this test, given that not found values are priority over stale values
        val cacheData = FlagResolution(
            context1,
            resolvedFlags.list,
            "token2"
        )
        cache.refresh(cacheData)
        val ex = mockConfidence.getFlag(
            "test-kotlin-flag-2.mystring",
            "default"
        )
        TestCase.assertEquals(ErrorCode.FLAG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun testErrorInNetwork() = runTest {
        val cache = InMemoryCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, cache = cache, initialContext = context)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenThrow(Error(""))
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        val ex = mockConfidence.getFlag(
            "test-kotlin-flag-2.mystring",
            "default"
        )
        TestCase.assertEquals(ErrorCode.FLAG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun testValueNotFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(
            flagResolverClient.resolve(
                eq(listOf()),
                eq(context)
            )
        ).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        val ex = mockConfidence.getFlag(
            "test-kotlin-flag-1.wrongid",
            "default"
        )
        TestCase.assertEquals(ErrorCode.PARSE_ERROR, ex.errorCode)
    }

    @Test
    fun testValueNotFoundLongPath() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("key" to ConfidenceValue.String("foo"))
        val mockConfidence = getConfidence(testDispatcher, initialContext = context)
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))
        whenever(flagResolverClient.resolve(eq(listOf()), any())).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlags.list,
                    "token1"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        val ex = mockConfidence.getFlag(
            "test-kotlin-flag-1.mystring.extrapath",
            "default"
        )
        TestCase.assertEquals(ErrorCode.PARSE_ERROR, ex.errorCode)
    }

    @Test
    fun testShouldApplyFalse() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("targeting_key" to ConfidenceValue.String("foo"))
        val flagResolver: FlagResolver = mock()
        val mockConfidence = getConfidence(
            testDispatcher,
            initialContext = context,
            flagResolver = flagResolver
        )
        whenever(
            flagResolver.resolve(
                any(),
                eq(context)
            )
        ).thenReturn(
            Result.Success(
                FlagResolution(
                    context,
                    resolvedFlagsNoApply.list,
                    "token1"
                )
            )
        )
        mockConfidence.fetchAndActivate()
        advanceUntilIdle()
        val evalString = mockConfidence.getFlag("test-kotlin-flag-2.mystring", "default")

        // Resolve is correct, but no Apply sent
        TestCase.assertEquals("red", evalString.value)
        verify(flagApplierClient, times(0)).apply(any(), any())
    }
}

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