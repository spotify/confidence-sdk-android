package com.spotify.confidence.cache

import com.spotify.confidence.client.ResolveReason
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.Value
import kotlinx.serialization.Serializable

interface ProviderCache {
    fun refresh(cacheData: CacheData)
    fun resolve(flagName: String, ctx: EvaluationContext): CacheResolveResult
    fun clear()

    data class CacheResolveEntry(
        val variant: String,
        val value: Value.Structure,
        val resolveToken: String,
        val resolveReason: ResolveReason
    )

    @Serializable
    data class CacheEntry(
        val variant: String,
        val value: Value.Structure,
        val reason: ResolveReason
    )

    sealed interface CacheResolveResult {
        data class Found(val entry: CacheResolveEntry) : CacheResolveResult
        object NotFound : CacheResolveResult
        object Stale : CacheResolveResult
    }
}