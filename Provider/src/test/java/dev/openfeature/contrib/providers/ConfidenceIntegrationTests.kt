package dev.openfeature.contrib.providers

import android.content.Context
<<<<<<< HEAD
import dev.openfeature.contrib.providers.cache.FLAGS_FILE_NAME
=======
import dev.openfeature.contrib.providers.cache.CACHE_FILE_NAME
>>>>>>> 43375cb (Transfer codebase)
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
<<<<<<< HEAD
import dev.openfeature.sdk.Value
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
=======
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
>>>>>>> 43375cb (Transfer codebase)
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
<<<<<<< HEAD
import java.util.UUID

private const val clientSecret = "ldZWlt6ywPIiPNf16WINSTh0yoHzSQEc"
=======
import java.util.*

private const val clientSecret = "YZ2x7AClM1Rynl8HFfEZtNTSIWdWsNUS"
>>>>>>> 43375cb (Transfer codebase)
private val mockContext: Context = mock()

class ConfidenceIntegrationTests {
    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    @Test
    fun testSimpleResolveInMemoryCache() {
        runBlocking {
<<<<<<< HEAD
            OpenFeatureAPI.setProvider(
                ConfidenceFeatureProvider.create(mockContext, clientSecret, cache = InMemoryCache()),
                MutableContext(
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
=======
            OpenFeatureAPI.setProvider(ConfidenceFeatureProvider.Builder(mockContext, clientSecret)
                .cache(InMemoryCache())
                .build(),
                MutableContext(targetingKey = UUID.randomUUID().toString())
            )
        }
        val stringDetails = OpenFeatureAPI.getClient().getStringDetails("fdema-kotlin-flag-1.color", "default")
        assertNull(stringDetails.errorCode)
        assertNull(stringDetails.errorMessage)
        assertNotNull(stringDetails.value)
        assertNotEquals("default", stringDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, stringDetails.reason)
        assertNotNull(stringDetails.variant)
>>>>>>> 43375cb (Transfer codebase)
    }

    @Test
    fun testSimpleResolveStoredCache() {
<<<<<<< HEAD
        val cacheFile = File(mockContext.filesDir, FLAGS_FILE_NAME)
        assertEquals(0L, cacheFile.length())
        runBlocking {
            OpenFeatureAPI.setProvider(
                ConfidenceFeatureProvider.create(mockContext, clientSecret),
                MutableContext(
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
=======
        val cacheFile = File(mockContext.filesDir, CACHE_FILE_NAME)
        assertEquals(0L, cacheFile.length())
        runBlocking {
            OpenFeatureAPI.setProvider(ConfidenceFeatureProvider.Builder(mockContext, clientSecret)
                .build(),
                MutableContext(targetingKey = UUID.randomUUID().toString()))
        }
        assertNotEquals(0L, cacheFile.length())
        val stringDetails = OpenFeatureAPI.getClient().getStringDetails("fdema-kotlin-flag-1.color", "default")
        assertNull(stringDetails.errorCode)
        assertNull(stringDetails.errorMessage)
        assertNotNull(stringDetails.value)
        assertNotEquals("default", stringDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, stringDetails.reason)
        assertNotNull(stringDetails.variant)
>>>>>>> 43375cb (Transfer codebase)
    }
}