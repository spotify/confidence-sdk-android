package com.spotify.confidence

import com.spotify.confidence.client.Clock
import java.util.Date

/** The status of the cached flag resolution on disk. */
sealed interface ResolveStorageStatus {
    /** No cached flag resolution is available. */
    object Empty : ResolveStorageStatus

    /** The cached flag resolution is stale according to the supplied [ResolveStorageCheck]. */
    data class Stale(val lastFetchedAt: Date?) : ResolveStorageStatus

    /** The cached flag resolution is fresh according to the supplied [ResolveStorageCheck]. */
    data class Fresh(val lastFetchedAt: Date?) : ResolveStorageStatus
}

/** Determines resolve storage status from its metadata. */
fun interface ResolveStorageCheck {
    /** Returns the storage status according to [metadata]. */
    fun check(metadata: ResolveStorageMetadata): ResolveStorageStatus
}

/** Metadata available when checking resolve storage. */
data class ResolveStorageMetadata(
    val isEmpty: Boolean,
    val lastFetchedAt: Date?,
    val context: Map<String, ConfidenceValue>
)

/** Considers resolve storage stale when its fetch time is unknown or at least [maxAgeMillis] old. */
class MaxAgeStorageCheck internal constructor(
    val maxAgeMillis: Long,
    private val clock: Clock
) : ResolveStorageCheck {
    constructor(maxAgeMillis: Long) : this(maxAgeMillis, Clock.CalendarBacked.systemUTC())

    init {
        require(maxAgeMillis > 0) { "maxAgeMillis must be positive" }
    }

    override fun check(metadata: ResolveStorageMetadata): ResolveStorageStatus {
        if (metadata.isEmpty) return ResolveStorageStatus.Empty
        val fetchedAt = metadata.lastFetchedAt?.let { Date(it.time) }
        val isStale = fetchedAt == null || clock.currentTime().time - fetchedAt.time >= maxAgeMillis
        return if (isStale) {
            ResolveStorageStatus.Stale(fetchedAt)
        } else {
            ResolveStorageStatus.Fresh(fetchedAt)
        }
    }
}
