package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.cache.FLAGS_FILE_NAME
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.UUID

private const val clientSecret = "ldZWlt6ywPIiPNf16WINSTh0yoHzSQEc"
private val mockContext: Context = mock()

class ConfidenceIntegrationTests {
    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    @Test
    fun testSimpleResolveInMemoryCache() {
        runBlocking {
            OpenFeatureAPI.setProvider(
                ConfidenceFeatureProvider.create(mockContext, clientSecret, cache = InMemoryCache()),
                ImmutableContext(
                    targetingKey = UUID.randomUUID().toString(),
                    attributes = mutableMapOf(
                        "user" to Value.Structure(
                            mapOf(
                                "country" to Value.String("SE")
                            )
                        )
                    )
                )
            )
        }
        val intDetails = OpenFeatureAPI.getClient().getIntegerDetails("test-flag-1.my-integer", 0)
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }

    @Test
    fun testSimpleResolveStoredCache() {
        val cacheFile = File(mockContext.filesDir, FLAGS_FILE_NAME)
        assertEquals(0L, cacheFile.length())
        runBlocking {
            OpenFeatureAPI.setProvider(
                ConfidenceFeatureProvider.create(mockContext, clientSecret),
                ImmutableContext(
                    targetingKey = UUID.randomUUID().toString(),
                    attributes = mutableMapOf(
                        "user" to Value.Structure(
                            mapOf(
                                "country" to Value.String("SE")
                            )
                        )
                    )
                )
            )
        }
        assertNotEquals(0L, cacheFile.length())
        val intDetails = OpenFeatureAPI.getClient().getIntegerDetails("test-flag-1.my-integer", 0)
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }
}