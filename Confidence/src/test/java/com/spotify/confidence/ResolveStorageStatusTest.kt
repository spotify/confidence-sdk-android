package com.spotify.confidence

import com.spotify.confidence.client.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

class ResolveStorageStatusTest {
    private val now = Date(10_000)
    private val clock = object : Clock {
        override fun currentTime(): Date = now
    }

    @Test
    fun unknownFetchTimeIsStale() {
        val check = MaxAgeStorageCheck(1_000, clock)

        assertEquals(
            ResolveStorageStatus.Stale(null),
            check.check(resolveStorageMetadata(lastFetchedAt = null))
        )
    }

    @Test
    fun fetchWithinMaxAgeIsFresh() {
        val check = MaxAgeStorageCheck(1_000, clock)
        val lastFetchedAt = Date(9_001)

        assertEquals(
            ResolveStorageStatus.Fresh(lastFetchedAt),
            check.check(resolveStorageMetadata(lastFetchedAt = lastFetchedAt))
        )
    }

    @Test
    fun fetchAtMaxAgeIsStale() {
        val check = MaxAgeStorageCheck(1_000, clock)
        val lastFetchedAt = Date(9_000)

        assertEquals(
            ResolveStorageStatus.Stale(lastFetchedAt),
            check.check(resolveStorageMetadata(lastFetchedAt = lastFetchedAt))
        )
    }

    @Test
    fun emptyStorageIsEmpty() {
        val check = MaxAgeStorageCheck(1_000, clock)

        assertEquals(
            ResolveStorageStatus.Empty,
            check.check(resolveStorageMetadata(isEmpty = true, lastFetchedAt = null))
        )
    }

    @Test
    fun maxAgeMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            MaxAgeStorageCheck(0)
        }
    }

    private fun resolveStorageMetadata(
        isEmpty: Boolean = false,
        lastFetchedAt: Date?
    ) = ResolveStorageMetadata(
        isEmpty = isEmpty,
        lastFetchedAt = lastFetchedAt,
        context = emptyMap()
    )
}
