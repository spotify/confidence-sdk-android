@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.util.Base64
import com.spotify.telemetry.v1.Types.LibraryTraces
import com.spotify.telemetry.v1.Types.Monitoring
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import com.spotify.telemetry.v1.Types.LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode as ProtoErrorCode
import com.spotify.telemetry.v1.Types.LibraryTraces.Trace.EvaluationTrace.EvaluationReason as ProtoReason
import com.spotify.telemetry.v1.Types.LibraryTraces.Trace.RequestTrace.Status as ProtoStatus
import com.spotify.telemetry.v1.Types.Platform as ProtoPlatform

private fun decodeMonitoring(headerValue: String): Monitoring {
    val bytes = java.util.Base64.getDecoder().decode(headerValue)
    return Monitoring.parseFrom(bytes)
}

class TelemetryTest {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    // --- Reason mapping tests ---

    @Test
    fun testMapMatch() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_MATCH,
            null
        )
        assertEquals(Telemetry.EvaluationReason.TARGETING_MATCH, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    @Test
    fun testMapNoSegmentMatch() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH,
            null
        )
        assertEquals(Telemetry.EvaluationReason.DEFAULT, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    @Test
    fun testMapNoTreatmentMatch() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH,
            null
        )
        assertEquals(Telemetry.EvaluationReason.DEFAULT, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    @Test
    fun testMapStale() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_STALE,
            null
        )
        assertEquals(Telemetry.EvaluationReason.STALE, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    @Test
    fun testMapFlagArchived() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_FLAG_ARCHIVED,
            null
        )
        assertEquals(Telemetry.EvaluationReason.DISABLED, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    @Test
    fun testMapTargetingKeyError() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR,
            null
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.TARGETING_KEY_MISSING, errorCode)
    }

    @Test
    fun testMapError() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.ERROR,
            null
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.GENERAL, errorCode)
    }

    @Test
    fun testMapUnspecified() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_UNSPECIFIED,
            null
        )
        assertEquals(Telemetry.EvaluationReason.UNSPECIFIED, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    @Test
    fun testMapDefault() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.DEFAULT,
            null
        )
        assertEquals(Telemetry.EvaluationReason.UNSPECIFIED, reason)
        assertEquals(Telemetry.EvaluationErrorCode.UNSPECIFIED, errorCode)
    }

    // --- Error code mapping takes priority over reason ---

    @Test
    fun testMapErrorCodeFlagNotFound() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_MATCH,
            ConfidenceError.ErrorCode.FLAG_NOT_FOUND
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.FLAG_NOT_FOUND, errorCode)
    }

    @Test
    fun testMapErrorCodeParseError() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_MATCH,
            ConfidenceError.ErrorCode.PARSE_ERROR
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.PARSE_ERROR, errorCode)
    }

    @Test
    fun testMapErrorCodeInvalidContext() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_MATCH,
            ConfidenceError.ErrorCode.INVALID_CONTEXT
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.INVALID_CONTEXT, errorCode)
    }

    @Test
    fun testMapErrorCodeProviderNotReady() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_MATCH,
            ConfidenceError.ErrorCode.PROVIDER_NOT_READY
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.PROVIDER_NOT_READY, errorCode)
    }

    @Test
    fun testMapErrorCodeResolveStale() {
        val (reason, errorCode) = Telemetry.mapEvaluationReason(
            ResolveReason.RESOLVE_REASON_MATCH,
            ConfidenceError.ErrorCode.RESOLVE_STALE
        )
        assertEquals(Telemetry.EvaluationReason.ERROR, reason)
        assertEquals(Telemetry.EvaluationErrorCode.GENERAL, errorCode)
    }

    // --- Snapshot and clear ---

    @Test
    fun testEncodedHeaderValueReturnsNullWhenEmpty() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        assertNull(telemetry.encodedHeaderValue())
    }

    @Test
    fun testSnapshotAndClear() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        telemetry.trackEvaluation(
            Telemetry.EvaluationReason.TARGETING_MATCH,
            Telemetry.EvaluationErrorCode.UNSPECIFIED
        )
        telemetry.trackResolveLatency(150, Telemetry.RequestStatus.SUCCESS)

        assertNotNull(telemetry.encodedHeaderValue())
        assertNull(telemetry.encodedHeaderValue())
    }

    // --- Protobuf round-trip: encode then parse with generated code ---

    @Test
    fun testEvaluationTraceRoundTrip() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        telemetry.trackEvaluation(
            Telemetry.EvaluationReason.TARGETING_MATCH,
            Telemetry.EvaluationErrorCode.UNSPECIFIED
        )

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())

        assertEquals(ProtoPlatform.PLATFORM_KOTLIN, monitoring.platform)
        assertEquals(1, monitoring.libraryTracesCount)

        val lib = monitoring.getLibraryTraces(0)
        assertEquals(LibraryTraces.Library.LIBRARY_CONFIDENCE, lib.library)
        assertEquals("1.0.0", lib.libraryVersion)
        assertEquals(1, lib.tracesCount)

        val trace = lib.getTraces(0)
        assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, trace.id)
        assertTrue(trace.hasEvaluationTrace())

        val eval = trace.evaluationTrace
        assertEquals(ProtoReason.EVALUATION_REASON_TARGETING_MATCH, eval.reason)
        assertEquals(ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED, eval.errorCode)
    }

    @Test
    fun testResolveLatencyRoundTrip() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "2.0.0")
        telemetry.trackResolveLatency(142, Telemetry.RequestStatus.SUCCESS)

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())

        assertEquals(ProtoPlatform.PLATFORM_KOTLIN, monitoring.platform)

        val lib = monitoring.getLibraryTraces(0)
        assertEquals(LibraryTraces.Library.LIBRARY_CONFIDENCE, lib.library)
        assertEquals("2.0.0", lib.libraryVersion)

        val trace = lib.getTraces(0)
        assertEquals(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY, trace.id)
        assertTrue(trace.hasRequestTrace())

        val req = trace.requestTrace
        assertEquals(142L, req.millisecondDuration)
        assertEquals(ProtoStatus.STATUS_SUCCESS, req.status)
    }

    @Test
    fun testOpenFeatureLibraryRoundTrip() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.OPEN_FEATURE, "3.0.0")
        telemetry.trackEvaluation(
            Telemetry.EvaluationReason.ERROR,
            Telemetry.EvaluationErrorCode.FLAG_NOT_FOUND
        )

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())
        val lib = monitoring.getLibraryTraces(0)

        assertEquals(LibraryTraces.Library.LIBRARY_OPEN_FEATURE, lib.library)
        assertEquals("3.0.0", lib.libraryVersion)

        val eval = lib.getTraces(0).evaluationTrace
        assertEquals(ProtoReason.EVALUATION_REASON_ERROR, eval.reason)
        assertEquals(ProtoErrorCode.EVALUATION_ERROR_CODE_FLAG_NOT_FOUND, eval.errorCode)
    }

    @Test
    fun testMultipleTracesRoundTrip() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        telemetry.trackResolveLatency(100, Telemetry.RequestStatus.SUCCESS)
        telemetry.trackResolveLatency(500, Telemetry.RequestStatus.TIMEOUT)
        telemetry.trackEvaluation(
            Telemetry.EvaluationReason.TARGETING_MATCH,
            Telemetry.EvaluationErrorCode.UNSPECIFIED
        )
        telemetry.trackEvaluation(
            Telemetry.EvaluationReason.DISABLED,
            Telemetry.EvaluationErrorCode.UNSPECIFIED
        )

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())
        val traces = monitoring.getLibraryTraces(0).tracesList
        assertEquals(4, traces.size)

        // Resolve latency traces first
        assertEquals(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY, traces[0].id)
        assertEquals(100L, traces[0].requestTrace.millisecondDuration)
        assertEquals(ProtoStatus.STATUS_SUCCESS, traces[0].requestTrace.status)

        assertEquals(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY, traces[1].id)
        assertEquals(500L, traces[1].requestTrace.millisecondDuration)
        assertEquals(ProtoStatus.STATUS_TIMEOUT, traces[1].requestTrace.status)

        // Then evaluation traces
        assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, traces[2].id)
        assertEquals(ProtoReason.EVALUATION_REASON_TARGETING_MATCH, traces[2].evaluationTrace.reason)

        assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, traces[3].id)
        assertEquals(ProtoReason.EVALUATION_REASON_DISABLED, traces[3].evaluationTrace.reason)
    }

    @Test
    fun testResolveLatencyErrorStatus() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        telemetry.trackResolveLatency(250, Telemetry.RequestStatus.ERROR)

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())
        val req = monitoring.getLibraryTraces(0).getTraces(0).requestTrace
        assertEquals(250L, req.millisecondDuration)
        assertEquals(ProtoStatus.STATUS_ERROR, req.status)
    }

    @Test
    fun testAllEvaluationReasonsEncodeCorrectly() {
        data class Case(
            val reason: Telemetry.EvaluationReason,
            val errorCode: Telemetry.EvaluationErrorCode,
            val expectedReason: ProtoReason,
            val expectedErrorCode: ProtoErrorCode
        )

        val cases = listOf(
            Case(
                Telemetry.EvaluationReason.UNSPECIFIED,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_UNSPECIFIED,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.TARGETING_MATCH,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_TARGETING_MATCH,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.DEFAULT,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_DEFAULT,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.STALE,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_STALE,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.DISABLED,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_DISABLED,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.CACHED,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_CACHED,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.STATIC,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_STATIC,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.SPLIT,
                Telemetry.EvaluationErrorCode.UNSPECIFIED,
                ProtoReason.EVALUATION_REASON_SPLIT,
                ProtoErrorCode.EVALUATION_ERROR_CODE_UNSPECIFIED
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.GENERAL,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_GENERAL
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.PROVIDER_NOT_READY,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_PROVIDER_NOT_READY
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.FLAG_NOT_FOUND,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_FLAG_NOT_FOUND
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.PARSE_ERROR,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_PARSE_ERROR
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.INVALID_CONTEXT,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_INVALID_CONTEXT
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.TARGETING_KEY_MISSING,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_TARGETING_KEY_MISSING
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.TYPE_MISMATCH,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_TYPE_MISMATCH
            ),
            Case(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.PROVIDER_FATAL,
                ProtoReason.EVALUATION_REASON_ERROR,
                ProtoErrorCode.EVALUATION_ERROR_CODE_PROVIDER_FATAL
            )
        )

        for (case in cases) {
            val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
            telemetry.trackEvaluation(case.reason, case.errorCode)

            val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())
            val eval = monitoring.getLibraryTraces(0).getTraces(0).evaluationTrace

            assertEquals("reason for ${case.reason}", case.expectedReason, eval.reason)
            assertEquals("errorCode for ${case.errorCode}", case.expectedErrorCode, eval.errorCode)
        }
    }

    // --- Library attribution ---

    @Test
    fun testDefaultLibraryIsConfidence() {
        assertEquals(
            Telemetry.Library.CONFIDENCE,
            Telemetry("x", Telemetry.Library.CONFIDENCE, "x").library
        )
    }

    @Test
    fun testLibraryCanBeChangedToOpenFeature() {
        val telemetry = Telemetry("x", Telemetry.Library.CONFIDENCE, "x")
        telemetry.library = Telemetry.Library.OPEN_FEATURE
        assertEquals(Telemetry.Library.OPEN_FEATURE, telemetry.library)
    }

    @Test
    fun testSetTelemetryLibraryOpenFeatureViaReflection() {
        val confidence = Confidence(
            clientSecret = "",
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            eventSenderEngine = mock(),
            diskStorage = mock(),
            flagResolver = mock(),
            flagApplierClient = mock(),
            debugLogger = null
        )

        assertEquals(Telemetry.Library.CONFIDENCE, confidence.telemetry.library)

        val method = confidence.javaClass.getDeclaredMethod("setTelemetryLibraryOpenFeature")
        method.isAccessible = true
        method.invoke(confidence)

        assertEquals(Telemetry.Library.OPEN_FEATURE, confidence.telemetry.library)

        confidence.telemetry.trackEvaluation(
            Telemetry.EvaluationReason.DEFAULT,
            Telemetry.EvaluationErrorCode.UNSPECIFIED
        )
        val monitoring = decodeMonitoring(confidence.telemetry.encodedHeaderValue()!!)
        assertEquals(
            LibraryTraces.Library.LIBRARY_OPEN_FEATURE,
            monitoring.getLibraryTraces(0).library
        )
    }

    @Test
    fun testWithContextSharesTelemetryInstance() {
        val confidence = Confidence(
            clientSecret = "",
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            eventSenderEngine = mock(),
            diskStorage = mock(),
            flagResolver = mock(),
            flagApplierClient = mock(),
            debugLogger = null
        )

        val child = confidence.withContext(
            mapOf("key" to ConfidenceValue.String("value"))
        ) as Confidence

        // Should be the exact same instance
        assertTrue(
            "withContext should share telemetry instance",
            confidence.telemetry === child.telemetry
        )
    }

    // --- SDK property ---

    @Test
    fun testSdkProperty() {
        val sdk = Telemetry("my-sdk-id", Telemetry.Library.CONFIDENCE, "2.0.0").sdk
        assertEquals("my-sdk-id", sdk.id)
        assertEquals("2.0.0", sdk.version)
    }

    // --- Thread safety ---

    @Test
    fun testConcurrentWrites() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        val threadCount = 10
        val iterationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (j in 0 until iterationsPerThread) {
                        telemetry.trackEvaluation(
                            Telemetry.EvaluationReason.TARGETING_MATCH,
                            Telemetry.EvaluationErrorCode.UNSPECIFIED
                        )
                        telemetry.trackResolveLatency(50, Telemetry.RequestStatus.SUCCESS)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())
        val traces = monitoring.getLibraryTraces(0).tracesList
        // Each list is capped at 100, so max 200 total (100 eval + 100 latency)
        assertTrue("Traces should be capped at 200", traces.size <= 200)
        assertTrue("Should have some traces", traces.size > 0)

        assertNull(telemetry.encodedHeaderValue())
    }

    @Test
    fun testTracesAreCappedAt100() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        repeat(150) {
            telemetry.trackEvaluation(
                Telemetry.EvaluationReason.TARGETING_MATCH,
                Telemetry.EvaluationErrorCode.UNSPECIFIED
            )
        }
        repeat(150) {
            telemetry.trackResolveLatency(50, Telemetry.RequestStatus.SUCCESS)
        }

        val monitoring = decodeMonitoring(telemetry.encodedHeaderValue())
        val traces = monitoring.getLibraryTraces(0).tracesList
        val evalTraces = traces.filter {
            it.id == LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION
        }
        val latencyTraces = traces.filter {
            it.id == LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY
        }
        assertEquals("Evaluation traces capped at 100", 100, evalTraces.size)
        assertEquals("Latency traces capped at 100", 100, latencyTraces.size)
    }

    @Test
    fun testConcurrentWritesAndReads() {
        val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")
        val threadCount = 5
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount * 2)
        val latch = CountDownLatch(threadCount * 2)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (j in 0 until iterationsPerThread) {
                        telemetry.trackEvaluation(
                            Telemetry.EvaluationReason.DEFAULT,
                            Telemetry.EvaluationErrorCode.UNSPECIFIED
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (j in 0 until iterationsPerThread) {
                        telemetry.encodedHeaderValue()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()
    }

    // --- Wire-level: header on HTTP resolve requests ---

    private val emptyResolveResponse = """
        { "resolvedFlags": [], "resolveToken": "token1" }
    """.trimIndent()

    @Test
    fun testResolveRequestIncludesTelemetryHeader() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        try {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")

            telemetry.trackEvaluation(
                Telemetry.EvaluationReason.TARGETING_MATCH,
                Telemetry.EvaluationErrorCode.UNSPECIFIED
            )

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(emptyResolveResponse))

            RemoteFlagResolver(
                clientSecret = "",
                region = ConfidenceRegion.GLOBAL,
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher,
                httpClient = OkHttpClient(),
                telemetry = telemetry
            ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))

            val recorded = mockWebServer.takeRequest()
            val headerValue = recorded.getHeader(Telemetry.HEADER_NAME)
            assertNotNull("Expected ${Telemetry.HEADER_NAME} header", headerValue)

            val monitoring = decodeMonitoring(headerValue!!)

            assertEquals(ProtoPlatform.PLATFORM_KOTLIN, monitoring.platform)

            val lib = monitoring.getLibraryTraces(0)
            assertEquals(LibraryTraces.Library.LIBRARY_CONFIDENCE, lib.library)
            assertEquals("1.0.0", lib.libraryVersion)

            val trace = lib.getTraces(0)
            assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, trace.id)
            assertEquals(
                ProtoReason.EVALUATION_REASON_TARGETING_MATCH,
                trace.evaluationTrace.reason
            )
        } finally {
            mockWebServer.shutdown()
        }
    }

    @Test
    fun testResolveRequestOmitsHeaderWhenNoTelemetry() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        try {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(emptyResolveResponse))

            RemoteFlagResolver(
                clientSecret = "",
                region = ConfidenceRegion.GLOBAL,
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher,
                httpClient = OkHttpClient(),
                telemetry = telemetry
            ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))

            assertNull(mockWebServer.takeRequest().getHeader(Telemetry.HEADER_NAME))
        } finally {
            mockWebServer.shutdown()
        }
    }

    @Test
    fun testResolveFlushLifecycle() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        try {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val telemetry = Telemetry("test-sdk", Telemetry.Library.CONFIDENCE, "1.0.0")

            telemetry.trackEvaluation(
                Telemetry.EvaluationReason.ERROR,
                Telemetry.EvaluationErrorCode.FLAG_NOT_FOUND
            )

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(emptyResolveResponse))
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(emptyResolveResponse))

            val resolver = RemoteFlagResolver(
                clientSecret = "",
                region = ConfidenceRegion.GLOBAL,
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher,
                httpClient = OkHttpClient(),
                telemetry = telemetry
            )

            // Call 1: carries the evaluation trace
            resolver.resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
            val m1 = decodeMonitoring(mockWebServer.takeRequest().getHeader(Telemetry.HEADER_NAME)!!)
            val traces1 = m1.getLibraryTraces(0).tracesList
            assertEquals(1, traces1.size)
            assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, traces1[0].id)

            // Call 2: evaluation was flushed; latency from call 1 is now pending
            resolver.resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
            val m2 = decodeMonitoring(mockWebServer.takeRequest().getHeader(Telemetry.HEADER_NAME)!!)
            val traces2 = m2.getLibraryTraces(0).tracesList
            assertEquals(1, traces2.size)
            assertEquals(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY, traces2[0].id)
            assertEquals(ProtoStatus.STATUS_SUCCESS, traces2[0].requestTrace.status)
        } finally {
            mockWebServer.shutdown()
        }
    }
}
