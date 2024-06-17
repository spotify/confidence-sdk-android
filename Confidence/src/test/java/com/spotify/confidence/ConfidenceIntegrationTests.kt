
package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ResolvedFlag
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        whenever(mockContext.getDir(any(), any())).thenReturn(Files.createTempDirectory("events").toFile())
        whenever(mockContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(InMemorySharedPreferences())
    }

    @Test
    fun testActivateAndFetchReadsTheLastValue() {
        val resolveToken =
            "AUi7izywlL0AEMyOVefLST+kYqzuO1PRJzXEEKNHTZCySkmuuEgg5J3rvZhwO+8/f/aOrWjTmVbby3lz2AWEQJjHqbmnvIo7OurF3buyxC7xWp7Ivn7N5+oZC/NoLF7mVEIHGo+dRWN/b0z1rTBXasMwV3HzPc03aRHb47WNG0A2asYsVERWBC9veXi8OSOPnx/aJrbBz7ROwdrr87Lp3C60GgO3P2RxVADZrI5BJzSlLv3jAyWFh563cdaqTCmjUp/iaWilYRqlXSGLkvUqdh40KlUpmIfdLvZ8gxbgq7muzzZuegTq6FMxMhxIvErO6quPN4MSPaoVX2cJ7601s5OZ0idsHvBH4TJPzOWOrn9BYJ9JXrdoblbyUfyXBOS0UsLh6O0ftD02TVd8VgWYNO8RrVDmtfsXkPhcSGIB3SuzgXgLhMZaGfy1Yd7U6EwQMx+Q0AY8fPfM9cGC9bz7N4/JvRJx2mRl+3I8ellH0VFzIhdMkzeRzE1T5Zo0NYvLPuf1n54FES10pEenrcjr2YJwm5uPzxNf+5sb0juD40jzzdVrSu5/CFP3i5orGyLWr0WOuCuQ1IbYl/lwWnjHLOuJfaOJJkcD6On2UpZkDrrt6Lis6I1Lt0QLOtxFugNHOTanRziexdtSqevehXC7JXNeCvdfAxNGbZd2AlH14rU+KMVMIvz77RbTS0t2FyHVufgb/nN6SAHfj7tC9TzRIQnlYLSzM3MMkK2VNtSpL8TW9OM4RG0Xuby0AU6KvBY4Wz++f+iC6pRI/1GKh4XzcUPFXnyh2hYz97A2t3WCnN+tWHdit2ozL+KNm/Ac3dfBkuonZhyTXpSV0Q=="

        val storedValue = 10
        val map: MutableMap<String, ConfidenceValue> =
            mutableMapOf("targeting_key" to ConfidenceValue.String(UUID.randomUUID().toString()))
        map["user"] =
            ConfidenceValue.Struct(mapOf("country" to ConfidenceValue.String("SE")))

        val oldConfidence = ConfidenceFactory.create(mockContext, clientSecret, initialContext = map)
        val context = oldConfidence.getContext()

        val storage = FileDiskStorage.create(mockContext)
        val flags = listOf(
            ResolvedFlag(
                "kotlin-test-flag",
                variant = "flags/kotlin-test-flag/off",
                reason = ResolveReason.RESOLVE_REASON_MATCH,
                value = mapOf(
                    "my-integer" to ConfidenceValue.Integer(
                        storedValue
                    )
                )
            )
        )

        storage.store(
            FlagResolution(
                context,
                flags,
                resolveToken
            )
        )

        val mockConfidence = ConfidenceFactory.create(
            mockContext,
            clientSecret,
            initialContext = map
        )
        mockConfidence.activate()

        val intDetails = mockConfidence.getFlag("kotlin-test-flag.my-integer", 0)
        Assert.assertNull(intDetails.errorCode)
        Assert.assertNull(intDetails.errorMessage)
        Assert.assertNotNull(intDetails.value)
        Assert.assertEquals(storedValue, intDetails.value)
        Assert.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, intDetails.reason)
        Assert.assertNotNull(intDetails.variant)
    }
}