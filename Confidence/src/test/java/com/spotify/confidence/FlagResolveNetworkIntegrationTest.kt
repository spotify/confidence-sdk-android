@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import android.util.Base64
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.telemetry.v1.Types.LibraryTraces
import com.spotify.telemetry.v1.Types.Monitoring
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import com.spotify.telemetry.v1.Types.LibraryTraces.Trace.EvaluationTrace.EvaluationReason as ProtoReason

private val resolveResponsePayload = """
{
  "resolvedFlags": [
    {
      "flag": "flags/test-flag",
      "variant": "flags/test-flag/variants/treatment",
      "value": {
        "str_property": "test-value",
        "int_property": 400.0,
        "bool_property": null
      },
      "flagSchema": {
        "schema": {
          "str_property": {
            "stringSchema": {}
          },
          "int_property": {
            "intSchema": {}
          },
          "bool_property": {
            "boolSchema": {}
          }
        }
      },
      "reason": "RESOLVE_REASON_MATCH",
      "shouldApply": true
    }
  ],
  "resolveToken": "token-1"
}
""".trimIndent()

internal class FlagResolveNetworkIntegrationTest {
    private val mockWebServer = MockWebServer()
    private val flagApplierClient: com.spotify.confidence.client.FlagApplierClient = mock()
    private val mockContext: Context = mock()
    private lateinit var confidence: Confidence
    private lateinit var telemetry: Telemetry

    @Before
    fun setup() = runTest {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
        mockWebServer.start()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(resolveResponsePayload)
        )

        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        telemetry = Telemetry("test", Telemetry.Library.CONFIDENCE, "0.0.0")
        val flagResolver = RemoteFlagResolver(
            clientSecret = "test-secret",
            region = ConfidenceRegion.GLOBAL,
            baseUrl = mockWebServer.url(""),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = telemetry
        )

        val context = mapOf("targeting_key" to ConfidenceValue.String("test-user"))
        confidence = Confidence(
            clientSecret = "test-secret",
            dispatcher = testDispatcher,
            eventSenderEngine = mock(),
            initialContext = context,
            cache = InMemoryCache(),
            flagResolver = flagResolver,
            flagApplierClient = flagApplierClient,
            diskStorage = FileDiskStorage.create(mockContext),
            region = ConfidenceRegion.GLOBAL,
            debugLogger = null,
            telemetry = telemetry
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkStatic(Base64::class)
    }

    @Test
    fun testFlagResolvedFromNetwork() = runTest {
        confidence.fetchAndActivate()
        advanceUntilIdle()

        // String field
        val strProperty = confidence.getFlag("test-flag.str_property", "default")
        assertEquals("test-value", strProperty.value)
        assertEquals(ResolveReason.RESOLVE_REASON_MATCH, strProperty.reason)
        assertEquals("flags/test-flag/variants/treatment", strProperty.variant)
        assertNull(strProperty.errorCode)
        assertNull(strProperty.errorMessage)

        // Integer field (value sent as 400.0 with intSchema)
        val intProperty = confidence.getFlag("test-flag.int_property", 0)
        assertEquals(400, intProperty.value)
        assertEquals(ResolveReason.RESOLVE_REASON_MATCH, intProperty.reason)
        assertNull(intProperty.errorCode)

        // Null boolean field — schema says bool but value is null, so default is returned
        val boolProperty = confidence.getFlag("test-flag.bool_property", false)
        assertEquals(false, boolProperty.value)
        assertEquals(ResolveReason.RESOLVE_REASON_MATCH, boolProperty.reason)
        assertEquals("flags/test-flag/variants/treatment", boolProperty.variant)

        // Full flag as struct
        val fullFlag = confidence.getFlag(
            "test-flag",
            ConfidenceValue.Struct(mapOf())
        )
        assertEquals(ResolveReason.RESOLVE_REASON_MATCH, fullFlag.reason)
        assertNull(fullFlag.errorCode)
        val struct = fullFlag.value
        assertEquals(ConfidenceValue.String("test-value"), struct.map["str_property"])
        assertEquals(ConfidenceValue.Integer(400), struct.map["int_property"])
        assertEquals(ConfidenceValue.Null, struct.map["bool_property"])
    }

    @Test
    fun testGetFlagTracksEvaluationTelemetry() = runTest {
        confidence.fetchAndActivate()
        advanceUntilIdle()

        // Flush any pending telemetry from the resolve call
        telemetry.encodedHeaderValue()

        // Evaluate flags - each getFlag should track an evaluation
        confidence.getFlag("test-flag.str_property", "default")
        confidence.getFlag("test-flag.int_property", 0)
        confidence.getFlag("nonexistent-flag", "default") // FLAG_NOT_FOUND

        // Snapshot the telemetry and decode with real protobuf
        val headerValue = telemetry.encodedHeaderValue()
        assertNotNull("Expected telemetry after flag evaluations", headerValue)

        val monitoring = Monitoring.parseFrom(java.util.Base64.getDecoder().decode(headerValue))
        val traces = monitoring.getLibraryTraces(0).tracesList

        assertEquals(3, traces.size)

        // First two evaluations: RESOLVE_REASON_MATCH, no error → TARGETING_MATCH
        assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, traces[0].id)
        assertEquals(
            ProtoReason.EVALUATION_REASON_TARGETING_MATCH,
            traces[0].evaluationTrace.reason
        )

        assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, traces[1].id)
        assertEquals(
            ProtoReason.EVALUATION_REASON_TARGETING_MATCH,
            traces[1].evaluationTrace.reason
        )

        // Third evaluation: nonexistent flag → ERROR with FLAG_NOT_FOUND
        assertEquals(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION, traces[2].id)
        assertEquals(ProtoReason.EVALUATION_REASON_ERROR, traces[2].evaluationTrace.reason)
    }

    @Test
    fun testGetFlagTelemetryAppearsOnNextResolve() = runTest {
        confidence.fetchAndActivate()
        advanceUntilIdle()

        // Consume the initial resolve request
        mockWebServer.takeRequest()

        // Evaluate a flag → this should track telemetry
        confidence.getFlag("test-flag.str_property", "default")

        // Enqueue a second resolve response, then trigger it
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(resolveResponsePayload)
        )
        confidence.fetchAndActivate()
        advanceUntilIdle()

        // The second resolve request should carry the evaluation + latency telemetry
        val secondRequest = mockWebServer.takeRequest()
        val header = secondRequest.getHeader(Telemetry.HEADER_NAME)
        assertNotNull("Second resolve should carry telemetry from getFlag", header)

        val monitoring = Monitoring.parseFrom(java.util.Base64.getDecoder().decode(header))
        val traces = monitoring.getLibraryTraces(0).tracesList

        // Should have at least the evaluation trace and the latency from the first resolve
        val evalTraces = traces.filter {
            it.id == LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION
        }
        val latencyTraces = traces.filter {
            it.id == LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY
        }
        assertEquals("Expected 1 evaluation trace", 1, evalTraces.size)
        assertEquals(
            ProtoReason.EVALUATION_REASON_TARGETING_MATCH,
            evalTraces[0].evaluationTrace.reason
        )
        assertEquals("Expected 1 latency trace", 1, latencyTraces.size)
    }

    @Test
    fun testMultipleResolvesAccumulateLatencyTraces() = runTest {
        // First fetchAndActivate (setup already enqueued a response)
        confidence.fetchAndActivate()
        advanceUntilIdle()
        mockWebServer.takeRequest()

        // Flush the initial telemetry so we start clean
        telemetry.encodedHeaderValue()

        // Do 3 more fetches - each adds a resolve latency trace.
        // Each subsequent resolve flushes the previous one's latency via the header,
        // so we verify the 4th request carries the 3rd resolve's latency.
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(resolveResponsePayload)
            )
            confidence.fetchAndActivate()
            advanceUntilIdle()
        }

        // Consume the 3 resolve requests and inspect the last one
        mockWebServer.takeRequest() // resolve #1: header carries nothing (we flushed)
        mockWebServer.takeRequest() // resolve #2: header carries latency from #1
        val thirdRequest = mockWebServer.takeRequest() // resolve #3: header carries latency from #2

        val header = thirdRequest.getHeader(Telemetry.HEADER_NAME)
        assertNotNull("Third resolve should carry latency from second resolve", header)

        val monitoring = Monitoring.parseFrom(java.util.Base64.getDecoder().decode(header))
        val traces = monitoring.getLibraryTraces(0).tracesList
        val latencyTraces = traces.filter {
            it.id == LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY
        }
        assertEquals("Expected 1 latency trace (from previous resolve)", 1, latencyTraces.size)
        assertTrue(
            "Latency should be non-negative",
            latencyTraces[0].requestTrace.millisecondDuration >= 0
        )

        // The 3rd resolve's own latency is still pending in telemetry
        val remaining = telemetry.encodedHeaderValue()
        assertNotNull("Third resolve latency should still be pending", remaining)
    }
}
