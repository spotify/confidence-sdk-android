@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.SdkMetadata
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
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

    @Before
    fun setup() = runTest {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
        mockWebServer.start()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(resolveResponsePayload)
        )

        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        val flagResolver = RemoteFlagResolver(
            clientSecret = "test-secret",
            region = ConfidenceRegion.GLOBAL,
            baseUrl = mockWebServer.url(""),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            sdkMetadata = SdkMetadata("test", "0.0.0")
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
            debugLogger = null
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
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
}
