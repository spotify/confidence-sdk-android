package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.cache.FLAGS_FILE_NAME
import dev.openfeature.contrib.providers.cache.InMemoryCache
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
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

private const val clientSecret = "YZ2x7AClM1Rynl8HFfEZtNTSIWdWsNUS"
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
                ConfidenceFeatureProvider.Builder(mockContext, clientSecret)
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
    }

    @Test
    fun testSimpleResolveStoredCache() {
        val cacheFile = File(mockContext.filesDir, FLAGS_FILE_NAME)
        assertEquals(0L, cacheFile.length())
        runBlocking {
            OpenFeatureAPI.setProvider(
                ConfidenceFeatureProvider.Builder(mockContext, clientSecret)
                    .build(),
                MutableContext(targetingKey = UUID.randomUUID().toString())
            )
        }
        assertNotEquals(0L, cacheFile.length())
        val stringDetails = OpenFeatureAPI.getClient().getStringDetails("fdema-kotlin-flag-1.color", "default")
        assertNull(stringDetails.errorCode)
        assertNull(stringDetails.errorMessage)
        assertNotNull(stringDetails.value)
        assertNotEquals("default", stringDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, stringDetails.reason)
        assertNotNull(stringDetails.variant)
    }
}