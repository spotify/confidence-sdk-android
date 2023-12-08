package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FLAGS_FILE_NAME
import com.spotify.confidence.cache.StorageFileCache
import com.spotify.confidence.client.ResolveReason
import com.spotify.confidence.client.ResolvedFlag
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.ImmutableStructure
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.UUID

private const val clientSecret = "21wxcxXpU6tKBRFtEFTXYiH7nDqL86Mm"
private val mockContext: Context = mock()

class ConfidenceIntegrationTests {

    @get:Rule
    var tmpFile = TemporaryFolder()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    @Test
    fun testActivateAndFetchReadsTheLastValue() {
        val resolveToken =
            "AUi7izywlL0AEMyOVefLST+kYqzuO1PRJzXEEKNHTZCySkmuuEgg5J3rvZhwO+8/f/aOrWjTmVbby3lz2AWEQJjHqbmnvIo7OurF3buyxC7xWp7Ivn7N5+oZC/NoLF7mVEIHGo+dRWN/b0z1rTBXasMwV3HzPc03aRHb47WNG0A2asYsVERWBC9veXi8OSOPnx/aJrbBz7ROwdrr87Lp3C60GgO3P2RxVADZrI5BJzSlLv3jAyWFh563cdaqTCmjUp/iaWilYRqlXSGLkvUqdh40KlUpmIfdLvZ8gxbgq7muzzZuegTq6FMxMhxIvErO6quPN4MSPaoVX2cJ7601s5OZ0idsHvBH4TJPzOWOrn9BYJ9JXrdoblbyUfyXBOS0UsLh6O0ftD02TVd8VgWYNO8RrVDmtfsXkPhcSGIB3SuzgXgLhMZaGfy1Yd7U6EwQMx+Q0AY8fPfM9cGC9bz7N4/JvRJx2mRl+3I8ellH0VFzIhdMkzeRzE1T5Zo0NYvLPuf1n54FES10pEenrcjr2YJwm5uPzxNf+5sb0juD40jzzdVrSu5/CFP3i5orGyLWr0WOuCuQ1IbYl/lwWnjHLOuJfaOJJkcD6On2UpZkDrrt6Lis6I1Lt0QLOtxFugNHOTanRziexdtSqevehXC7JXNeCvdfAxNGbZd2AlH14rU+KMVMIvz77RbTS0t2FyHVufgb/nN6SAHfj7tC9TzRIQnlYLSzM3MMkK2VNtSpL8TW9OM4RG0Xuby0AU6KvBY4Wz++f+iC6pRI/1GKh4XzcUPFXnyh2hYz97A2t3WCnN+tWHdit2ozL+KNm/Ac3dfBkuonZhyTXpSV0Q=="

        val storedValue = 10

        val context = ImmutableContext(
            targetingKey = UUID.randomUUID().toString(),
            attributes = mutableMapOf(
                "user" to Value.Structure(
                    mapOf(
                        "country" to Value.String("SE")
                    )
                )
            )
        )

        val storage = StorageFileCache.create(mockContext).apply {
            val flags = listOf(
                ResolvedFlag(
                    "kotlin-test-flag",
                    variant = "flags/kotlin-test-flag/off",
                    reason = ResolveReason.RESOLVE_REASON_MATCH,
                    value = ImmutableStructure(
                        mapOf("my-integer" to Value.Integer(storedValue))
                    )
                )
            )

            store(
                flags,
                resolveToken,
                context
            )
        }

        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                mockContext,
                clientSecret,
                storage = storage,
                initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync,
                eventsPublisher = eventsHandler
            ),
            context
        )
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetails = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "kotlin-test-flag.my-integer",
                0
            )
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertEquals(storedValue, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }

    @Test
    fun testSimpleResolveInMemoryCache() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                mockContext,
                clientSecret,
                initialisationStrategy = InitialisationStrategy.FetchAndActivate,
                eventsPublisher = eventsHandler
            ),
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
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetails = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "kotlin-test-flag.my-integer",
                0
            )
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }

    @Test
    fun testSimpleResolveStoredCache() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        val cacheFile = File(mockContext.filesDir, FLAGS_FILE_NAME)
        assertEquals(0L, cacheFile.length())
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(mockContext, clientSecret, eventsPublisher = eventsHandler),
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

        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        assertNotEquals(0L, cacheFile.length())
        val intDetails = OpenFeatureAPI.getClient().getIntegerDetails("kotlin-test-flag.my-integer", 0)
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }
}