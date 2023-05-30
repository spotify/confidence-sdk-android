package dev.openfeature.contrib.providers.cache

import dev.openfeature.contrib.providers.cache.ProviderCache.CacheEntry
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveEntry
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.Value
import kotlinx.serialization.Serializable

open class InMemoryCache : ProviderCache {
    var data: CacheData? = null

    override fun refresh(
        resolvedFlags: List<ResolvedFlag>,
        resolveToken: String,
        evaluationContext: EvaluationContext
    ) {
        data = CacheData(
            values = resolvedFlags.associate {
                it.flag to CacheEntry(
                    it.variant,
                    Value.Structure(it.value?.asMap() ?: mapOf()),
                    it.reason
                )
            },
            evaluationContextHash = evaluationContext.hashCode(),
            resolveToken = resolveToken
        )
    }

    override fun resolve(flagName: String, ctx: EvaluationContext): CacheResolveResult {
        val dataSnapshot = data ?: return CacheResolveResult.NotFound
        val cacheEntry = dataSnapshot.values[flagName] ?: return CacheResolveResult.NotFound
        if (ctx.hashCode() != dataSnapshot.evaluationContextHash) {
            return CacheResolveResult.Stale
        }
        return CacheResolveResult.Found(
            CacheResolveEntry(cacheEntry.variant, cacheEntry.value, dataSnapshot.resolveToken, cacheEntry.reason)
        )
    }

    override fun clear() {
        data = null
    }

    @Serializable
    data class CacheData(
        val resolveToken: String,
        val evaluationContextHash: Int,
        val values: Map<String, CacheEntry>
    )
}