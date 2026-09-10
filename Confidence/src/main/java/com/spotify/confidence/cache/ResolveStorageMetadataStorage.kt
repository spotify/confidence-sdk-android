package com.spotify.confidence.cache

import com.spotify.confidence.FlagResolution
import com.spotify.confidence.ResolveStorageMetadata
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
internal data class StoredResolveStorageMetadata(
    val lastFetchedAtMillis: Long
)

internal interface ResolveStorageMetadataStorage {
    fun markFetched()

    fun getLastFetchedAt(): Date?
}

internal fun DiskStorage.getResolveStorageMetadata(): ResolveStorageMetadata = ResolveStorageMetadata(
    isEmpty = read() == FlagResolution.EMPTY,
    lastFetchedAt = (this as? ResolveStorageMetadataStorage)?.getLastFetchedAt()?.let { Date(it.time) }
)
