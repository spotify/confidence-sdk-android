@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.FlagApplierClientImpl
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.time.Instant
import java.util.Date

class ResolveBaseUrlTest {
    private val mockWebServer = MockWebServer()
    private val mockContext: Context = mock()

    @Before
    fun setUp() {
        mockWebServer.start()
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("flags").toFile())
        whenever(mockContext.getDir(any(), any())).thenReturn(Files.createTempDirectory("events").toFile())
        whenever(mockContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE))
            .thenReturn(InMemorySharedPreferences())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun customResolveBaseUrlIsNormalizedWithoutTrailingSlash() {
        assertEquals(
            mockWebServer.url(""),
            getResolveBaseUrl(ConfidenceRegion.USA, "${mockWebServer.url("")}/")
        )
    }

    @Test
    fun customResolveBaseUrlWithoutTrailingSlashIsUsedAsIs() {
        assertEquals(
            mockWebServer.url(""),
            getResolveBaseUrl(
                ConfidenceRegion.USA,
                mockWebServer.url("").toString().trimEnd('/')
            )
        )
    }

    @Test
    fun resolverUsesCustomResolveBaseUrl() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"resolvedFlags": [], "resolveToken": "token"}""")
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidence = ConfidenceFactory.create(
            context = mockContext,
            clientSecret = "secret",
            dispatcher = dispatcher,
            resolveBaseUrl = "${mockWebServer.url("")}/"
        )

        confidence.fetchAndActivate()

        assertEquals("/v1/flags:resolve", mockWebServer.takeRequest().path)
        confidence.stop()
    }

    @Test
    fun applierUsesCustomResolveBaseUrl() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val client = FlagApplierClientImpl(
            clientSecret = "secret",
            telemetry = Telemetry("test", Telemetry.Library.CONFIDENCE, "test"),
            region = ConfidenceRegion.USA,
            dispatcher = dispatcher,
            resolveBaseUrl = mockWebServer.url("")
        )

        client.apply(
            listOf(AppliedFlag("flag", Date.from(Instant.parse("2026-08-18T11:00:00Z")))),
            "token"
        )

        assertEquals("/v1/flags:apply", mockWebServer.takeRequest().path)
    }

    @Test
    fun regionalResolveBaseUrlIsUsedByDefault() {
        assertEquals(
            "https://resolver.us.confidence.dev/",
            getResolveBaseUrl(ConfidenceRegion.USA).toString()
        )
    }
}
