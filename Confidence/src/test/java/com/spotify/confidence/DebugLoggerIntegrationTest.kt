@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import android.util.Base64
import android.util.Log
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DebugLoggerIntegrationTest {

    @get:Rule
    var tmpFile = TemporaryFolder()

    private lateinit var filesDir: File
    private val mockContext: Context = mock()
    private val clientSecret = "test-client-secret"
    private val logMessageSlot = CapturingSlot<String>()
    private val capturedLogMessages = mutableListOf<String>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkStatic(Base64::class)

        // Capture debug log messages that contain base64 data
        every { Log.d("Confidence", capture(logMessageSlot)) } answers {
            val message = logMessageSlot.captured
            capturedLogMessages.add(message)
            0
        }
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Base64.encodeToString(any(), any()) } answers {
            val input = firstArg<ByteArray>()
            java.util.Base64.getEncoder().encodeToString(input)
        }

        filesDir = Files.createTempDirectory("tmpTests").toFile()
        whenever(mockContext.filesDir).thenReturn(filesDir)
        whenever(mockContext.getDir(any(), any())).thenReturn(Files.createTempDirectory("events").toFile())
        whenever(mockContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE))
            .thenReturn(InMemorySharedPreferences())
    }

    @After
    fun tearDown() {
        unmockkAll()
        filesDir.delete()
        capturedLogMessages.clear()
    }

    @Test
    fun testDebugLoggerBase64OutputWithVanillaConfidence() = runTest(UnconfinedTestDispatcher()) {
        val confidence = ConfidenceFactory.create(
            context = mockContext,
            clientSecret = clientSecret,
            loggingLevel = LoggingLevel.VERBOSE
        )

        // Set context for the flag evaluation
        confidence.putContext(
            mapOf(
                "visitor_id" to ConfidenceValue.String("myVistorId"),
                "targeting_key" to ConfidenceValue.String("test-user-123"),
                "user" to ConfidenceValue.Struct(
                    mapOf(
                        "country" to ConfidenceValue.String("SE"),
                        "age" to ConfidenceValue.Integer(25),
                        "product" to ConfidenceValue.String("premium"),
                        "fraud-score" to ConfidenceValue.Double(0.7)
                    )
                )
            )
        )

        // Get a flag through native Confidence, which should trigger debugLogger.logResolve
        // Even if the flag doesn't exist, it should still trigger logging
        val result = confidence.getFlag("test-flag.value", "default")

        // The flag doesn't exist, so we get the default value, but logging should still happen
        assertEquals("default", result.value)

        // Verify that debug logging was called with base64 data
        verify { Log.d("Confidence", any()) }

        // Find the log message containing base64 data
        val base64LogMessage = capturedLogMessages.find {
            it.contains("Check your flag evaluation") && it.contains("by copy pasting the payload")
        }
        assertTrue("Expected to find a log message with base64 data", base64LogMessage != null)

        // Extract the base64 data from the log message
        val base64Pattern = "'([A-Za-z0-9+/=]+)'$".toRegex()
        val matchResult = base64Pattern.find(base64LogMessage!!)
        assertTrue("Expected to find base64 data in log message", matchResult != null)

        val base64Data = matchResult!!.groupValues[1]
        assertTrue("Base64 data should not be empty", base64Data.isNotEmpty())

        // Decode and verify the JSON structure
        val decodedJson = String(java.util.Base64.getDecoder().decode(base64Data))

        // Expected JSON with clean format (no type wrappers)
        assertEquals(
            """{
  "flag": "flags/test-flag",
  "context": {
    "visitor_id": "myVistorId",
    "targeting_key": "test-user-123",
    "user": {
      "country": "SE",
      "age": 25,
      "product": "premium",
      "fraud-score": 0.7
    }
  },
  "clientKey": "test-client-secret"
}""".replace("\n", "").replace(" ", ""),
            decodedJson
        )
    }
}