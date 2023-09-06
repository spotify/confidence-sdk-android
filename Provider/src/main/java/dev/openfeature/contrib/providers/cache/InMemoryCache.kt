package dev.openfeature.contrib.providers.cache

import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveEntry
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.sdk.EvaluationContext

open class InMemoryCache : ProviderCache {
    private var data: CacheData? = null

    override fun refresh(cacheData: CacheData) {
        data = cacheData
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
}