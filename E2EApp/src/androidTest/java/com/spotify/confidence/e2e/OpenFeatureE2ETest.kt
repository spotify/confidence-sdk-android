@file:OptIn(ExperimentalTime::class)

package com.spotify.confidence.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotify.confidence.ConfidenceFactory
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.LoggingLevel
import com.spotify.confidence.openfeature.ConfidenceFeatureProvider
import com.spotify.confidence.openfeature.InitialisationStrategy
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class OpenFeatureE2ETest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var backend: TestBackend

    @Before
    fun setUp() = runBlocking {
        OpenFeatureAPI.shutdown()
        context.filesDir.deleteRecursively()
        context.getDir("events", Context.MODE_PRIVATE).deleteRecursively()
        backend = TestBackend().also { it.start() }
    }

    @After
    fun tearDown() = runBlocking {
        OpenFeatureAPI.shutdown()
        backend.close()
    }

    @Test
    fun confidenceProviderSupportsTheOpenFeatureApi() = runBlocking {
        val confidence = ConfidenceFactory.create(
            context = context,
            clientSecret = "e2e-client-secret",
            resolveBaseUrl = backend.baseUrl,
            loggingLevel = LoggingLevel.NONE
        )
        val provider = ConfidenceFeatureProvider.create(
            confidence = confidence,
            initialisationStrategy = InitialisationStrategy.FetchAndActivate
        )

        assertEquals(OpenFeatureStatus.NotReady, OpenFeatureAPI.getStatus())
        OpenFeatureAPI.setProviderAndWait(
            provider,
            ImmutableContext(
                targetingKey = "openfeature-user",
                attributes = mapOf(
                    "country" to Value.String("SE"),
                    "subscriber" to Value.Boolean(true),
                    "age" to Value.Integer(30),
                    "score" to Value.Double(4.5),
                    "roles" to Value.List(listOf(Value.String("reader"))),
                    "profile" to Value.Structure(mapOf("plan" to Value.String("premium"))),
                    "created" to Value.Instant(Instant.fromEpochSeconds(0)),
                    "nothing" to Value.Null
                )
            )
        )

        assertEquals(OpenFeatureStatus.Ready, OpenFeatureAPI.getStatus())
        assertTrue(provider.observe().first() is OpenFeatureProviderEvents.ProviderReady)
        assertEquals("SDK_ID_KOTLIN_CONFIDENCE", provider.metadata.name)
        val initialResolveBody = backend.awaitRequest("/v1/flags:resolve").body.clone().readUtf8()
        assertTrue(initialResolveBody.contains("\"roles\":[\"reader\"]"))
        assertTrue(initialResolveBody.contains("\"created\":\"1970-01-01T00:00:00.000Z\""))
        assertTrue(initialResolveBody.contains("\"nothing\":null"))

        val client = OpenFeatureAPI.getClient()
        assertEquals(true, client.getBooleanValue("e2e-flag.boolean", false))
        assertEquals("hello", client.getStringValue("e2e-flag.string", "fallback"))
        assertEquals(42, client.getIntegerValue("e2e-flag.integer", 0))
        assertEquals(3.14, client.getDoubleValue("e2e-flag.double", 0.0), 0.0)

        val objectValue = client.getObjectValue("e2e-flag.object", Value.Structure(emptyMap()))
        assertEquals("value", objectValue.asStructure()?.get("nested")?.asString())
        assertEquals(2, objectValue.asStructure()?.get("count")?.asInteger())

        val details = client.getStringDetails("e2e-flag.string", "fallback")
        assertEquals("hello", details.value)
        assertEquals("flags/e2e-flag/variants/enabled", details.variant)
        assertEquals(Reason.TARGETING_MATCH.name, details.reason)
        assertNull(details.errorCode)
        assertNull(details.errorMessage)

        OpenFeatureAPI.setEvaluationContextAndWait(
            ImmutableContext(
                targetingKey = "updated-user",
                attributes = mapOf("country" to Value.String("NO"))
            )
        )
        assertTrue(
            backend.requests
                .filter { it.path == "/v1/flags:resolve" }
                .any { it.body.clone().readUtf8().contains("updated-user") }
        )

        client.track(
            "openfeature-e2e-event",
            TrackingEventDetails(
                9.99,
                ImmutableStructure("item" to Value.String("coffee"))
            )
        )
        assertTrue(awaitEventPersisted(context))
    }

    @Test
    fun providerSupportsFastStartupInitialization() = runBlocking {
        val confidence = ConfidenceFactory.create(
            context = context,
            clientSecret = "e2e-client-secret",
            resolveBaseUrl = backend.baseUrl,
            initialContext = mapOf("targeting_key" to ConfidenceValue.String("fast-start-user")),
            loggingLevel = LoggingLevel.NONE
        )

        OpenFeatureAPI.setProviderAndWait(
            ConfidenceFeatureProvider.create(
                confidence = confidence,
                initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync
            )
        )

        assertEquals(OpenFeatureStatus.Ready, OpenFeatureAPI.getStatus())
        backend.awaitRequest("/v1/flags:resolve")
        confidence.fetchAndActivate()
        assertEquals("hello", OpenFeatureAPI.getClient().getStringValue("e2e-flag.string", "fallback"))
    }
}
