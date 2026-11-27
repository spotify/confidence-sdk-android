package com.spotify.confidence.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotify.confidence.ConfidenceFactory
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.EventSender
import com.spotify.confidence.LoggingLevel
import com.spotify.confidence.ResolveReason
import com.spotify.confidence.Result
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ConfidenceSdkE2ETest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var backend: TestBackend
    private var confidence: EventSender? = null

    @Before
    fun setUp() {
        context.filesDir.deleteRecursively()
        context.getDir("events", Context.MODE_PRIVATE).deleteRecursively()
        backend = TestBackend().also { it.start() }
    }

    @After
    fun tearDown() {
        confidence?.stop()
        backend.close()
    }

    @Test
    fun confidenceApiSupportsResolutionCachingAndApply() = runBlocking {
        val sdk = createConfidence()
        confidence = sdk

        assertTrue(sdk.isStorageEmpty())
        sdk.fetchAndActivate()
        assertFalse(sdk.isStorageEmpty())

        assertEquals(true, sdk.getValue("e2e-flag.boolean", false))
        assertEquals("hello", sdk.getValue("e2e-flag.string", "fallback"))
        assertEquals(42, sdk.getValue("e2e-flag.integer", 0))
        assertEquals(3.14, sdk.getValue("e2e-flag.double", 0.0), 0.0)

        val details = sdk.getFlag("e2e-flag.string", "fallback")
        assertEquals("hello", details.value)
        assertEquals("flags/e2e-flag/variants/enabled", details.variant)
        assertEquals(ResolveReason.RESOLVE_REASON_MATCH, details.reason)
        assertNull(details.errorCode)
        assertNull(details.errorMessage)

        val root = sdk.getValue("e2e-flag", ConfidenceValue.Struct(emptyMap()))
        assertEquals(
            ConfidenceValue.Struct(
                mapOf(
                    "nested" to ConfidenceValue.String("value"),
                    "count" to ConfidenceValue.Integer(2)
                )
            ),
            root.map["object"]
        )

        sdk.apply("e2e-flag", "e2e-resolve-token")
        val applyRequest = backend.awaitRequest("/v1/flags:apply")
        assertTrue(applyRequest.body.readUtf8().contains("\"flag\":\"flags/e2e-flag\""))
    }

    @Test
    fun confidenceApiSupportsContextsAndTracking() = runBlocking {
        val sdk = createConfidence()
        confidence = sdk
        val fields = mapOf(
            "string" to ConfidenceValue.String("value"),
            "boolean" to ConfidenceValue.Boolean(true),
            "integer" to ConfidenceValue.Integer(1),
            "double" to ConfidenceValue.Double(1.5),
            "list" to ConfidenceValue.stringList(listOf("one", "two")),
            "date" to ConfidenceValue.Date(Date(0)),
            "timestamp" to ConfidenceValue.Timestamp(Date(0)),
            "null" to ConfidenceValue.Null
        )

        sdk.putContextLocal(fields)
        sdk.putContext("country", ConfidenceValue.String("SE"))
        sdk.awaitReconciliation()
        assertEquals(ConfidenceValue.String("SE"), sdk.getContext()["country"])

        val update = sdk.putContextAndWait(
            context = mapOf("plan" to ConfidenceValue.String("premium")),
            removedKeys = listOf("country")
        )
        assertTrue(update is Result.Success)
        assertFalse(sdk.getContext().containsKey("country"))

        val removal = sdk.removeContextAndWait(listOf("plan"))
        assertTrue(removal is Result.Success)
        assertFalse(sdk.getContext().containsKey("plan"))

        val child = sdk.withContext(mapOf("scope" to ConfidenceValue.String("child")))
        assertEquals(ConfidenceValue.String("child"), child.getContext()["scope"])
        assertFalse(sdk.getContext().containsKey("scope"))

        sdk.track("e2e-event", fields)
        assertTrue(awaitEventPersisted(context))
        sdk.flush()

        val resolveRequest = backend.awaitRequest("/v1/flags:resolve")
        val body = resolveRequest.body.readUtf8()
        assertTrue(body.contains("\"boolean\":true"))
        assertTrue(body.contains("\"list\":[\"one\",\"two\"]"))
        assertTrue(body.contains("\"date\":\"1970-01-01\""))
        assertTrue(body.contains("\"timestamp\":\"1970-01-01T00:00:00.000Z\""))
        assertTrue(body.contains("\"visitor_id\""))
    }

    private fun createConfidence() = ConfidenceFactory.create(
        context = context,
        clientSecret = "e2e-client-secret",
        resolveBaseUrl = backend.baseUrl,
        initialContext = mapOf("targeting_key" to ConfidenceValue.String("e2e-user")),
        loggingLevel = LoggingLevel.NONE
    )
}
