package com.spotify.confidence

import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.util.UUID

private const val CLIENT_SECRET = "ldZWlt6ywPIiPNf16WINSTh0yoHzSQEc"
private val mockContext: Context = mock()

class ConfidenceIntegrationTests {

    @get:Rule
    var tmpFile = TemporaryFolder()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
    }

    private val evaluationContext = ImmutableContext(
        targetingKey = UUID.randomUUID().toString(),
        attributes = mutableMapOf(
            "user" to Value.Structure(
                mapOf(
                    "country" to Value.String("SE")
                )
            )
        )
    )

    private val resolveToken =
        "AUi7izywlL0AEMyOVefLST+kYqzuO1PRJzXEEKNHTZCySkmuuEgg5J3rvZhwO+8/f/aOrWjTmVbby3lz2AWEQJjHqbmnvIo7OurF3buyxC7xWp7Ivn7N5+oZC/NoLF7mVEIHGo+dRWN/b0z1rTBXasMwV3HzPc03aRHb47WNG0A2asYsVERWBC9veXi8OSOPnx/aJrbBz7ROwdrr87Lp3C60GgO3P2RxVADZrI5BJzSlLv3jAyWFh563cdaqTCmjUp/iaWilYRqlXSGLkvUqdh40KlUpmIfdLvZ8gxbgq7muzzZuegTq6FMxMhxIvErO6quPN4MSPaoVX2cJ7601s5OZ0idsHvBH4TJPzOWOrn9BYJ9JXrdoblbyUfyXBOS0UsLh6O0ftD02TVd8VgWYNO8RrVDmtfsXkPhcSGIB3SuzgXgLhMZaGfy1Yd7U6EwQMx+Q0AY8fPfM9cGC9bz7N4/JvRJx2mRl+3I8ellH0VFzIhdMkzeRzE1T5Zo0NYvLPuf1n54FES10pEenrcjr2YJwm5uPzxNf+5sb0juD40jzzdVrSu5/CFP3i5orGyLWr0WOuCuQ1IbYl/lwWnjHLOuJfaOJJkcD6On2UpZkDrrt6Lis6I1Lt0QLOtxFugNHOTanRziexdtSqevehXC7JXNeCvdfAxNGbZd2AlH14rU+KMVMIvz77RbTS0t2FyHVufgb/nN6SAHfj7tC9TzRIQnlYLSzM3MMkK2VNtSpL8TW9OM4RG0Xuby0AU6KvBY4Wz++f+iC6pRI/1GKh4XzcUPFXnyh2hYz97A2t3WCnN+tWHdit2ozL+KNm/Ac3dfBkuonZhyTXpSV0Q=="

    @Test
    fun testActivateAndFetchReadsTheLastValue() {
        val storedValue = 10

        val storage =
            StorageFileCache.create(mockContext)
                .apply {
                    val flags = listOf(
                        ResolvedFlag(
                            "test-flag-1",
                            variant = "flags/test-flag-1/off",
                            reason = ResolveReason.RESOLVE_REASON_MATCH,
                            value = ImmutableStructure(
                                mapOf("my-integer" to Value.Integer(storedValue))
                            )
                        )
                    )

                    store(
                        flags,
                        resolveToken,
                        evaluationContext
                    )
                }

        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                mockContext,
                CLIENT_SECRET,
                storage = storage,
                initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync,
                eventsPublisher = eventsHandler
            ),
            evaluationContext
        )
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetails = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "test-flag-1.my-integer",
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
    fun testFetchAndActivateReadsValueFromBackend() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        // empty storage
        val storage = StorageFileCache.create(mockContext)
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                mockContext,
                CLIENT_SECRET,
                initialisationStrategy = InitialisationStrategy.FetchAndActivate,
                storage = storage,
                eventsPublisher = eventsHandler
            ),
            evaluationContext
        )
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetails = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "test-flag-1.my-integer",
                0
            )
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertNotEquals(0, intDetails.value)
        assertEquals(7, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
        assertNotNull(intDetails.variant)
    }

    @Test
    fun testNoMatch() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        val cacheFileStore = tmpFile.newFile("testSimpleResolveStoredCache")
        val storage = StorageFileCache.forFiles(cacheFileStore, tmpFile.newFile("testSimpleResolveApplyCache"))
        assertEquals(0L, cacheFileStore.length())
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                mockContext,
                CLIENT_SECRET,
                eventsPublisher = eventsHandler,
                storage = storage
            ),
            evaluationContext
        )

        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        assertNotEquals(0L, cacheFileStore.length())
        val intDetails = OpenFeatureAPI.getClient().getIntegerDetails("test-flag-1.my-non-existing-prop", 0)
        assertNotNull(intDetails.errorCode)
        assertNotNull(intDetails.errorMessage)
        assertEquals(0, intDetails.value)
        assertEquals(Reason.ERROR.name, intDetails.reason)
        assertNull(intDetails.variant)
    }

    @Test
    fun testUpdateEvalContextToGetValues() {
        val eventsHandler = EventHandler(Dispatchers.IO).apply {
            publish(OpenFeatureEvents.ProviderStale)
        }
        // empty storage
        val storage = StorageFileCache.create(mockContext)
        // incorrect context that does get assignments
        val incorrectContext = ImmutableContext(
            targetingKey = UUID.randomUUID().toString(),
            attributes = mutableMapOf(
                "user" to Value.Structure(
                    mapOf(
                        "country" to Value.String("DE")
                    )
                )
            )
        )
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                mockContext,
                CLIENT_SECRET,
                initialisationStrategy = InitialisationStrategy.FetchAndActivate,
                storage = storage,
                eventsPublisher = eventsHandler
            ),
            incorrectContext
        )
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetailsDefault = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "test-flag-1.my-integer",
                0
            )
        assertNull(intDetailsDefault.errorCode)
        assertNull(intDetailsDefault.errorMessage)
        assertNotNull(intDetailsDefault.value)
        assertEquals(0, intDetailsDefault.value)
        assertEquals(Reason.DEFAULT.name, intDetailsDefault.reason)

        // Update to use evaluation context that get assignments.
        OpenFeatureAPI.setEvaluationContext(evaluationContext)
        assertFalse(eventsHandler.isProviderReady())
        runBlocking {
            awaitProviderReady(eventsHandler = eventsHandler)
        }

        val intDetails = OpenFeatureAPI.getClient()
            .getIntegerDetails(
                "test-flag-1.my-integer",
                0
            )
        assertNull(intDetails.errorCode)
        assertNull(intDetails.errorMessage)
        assertNotNull(intDetails.value)
        assertEquals(7, intDetails.value)
        assertEquals(Reason.TARGETING_MATCH.name, intDetails.reason)
    }
}