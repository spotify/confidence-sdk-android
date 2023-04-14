package dev.openfeature.contrib.providers.cache

<<<<<<< HEAD
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheEntry
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveEntry
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
=======
import dev.openfeature.contrib.providers.cache.ProviderCache.*
>>>>>>> 43375cb (Transfer codebase)
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.Value
import kotlinx.serialization.Serializable

open class InMemoryCache : ProviderCache {
    var data: CacheData? = null

<<<<<<< HEAD
    override fun refresh(
        resolvedFlags: List<ResolvedFlag>,
        resolveToken: String,
        evaluationContext: EvaluationContext
    ) {
=======
    override fun refresh(resolvedFlags: List<ResolvedFlag>, resolveToken: String, evaluationContext: EvaluationContext) {
>>>>>>> 43375cb (Transfer codebase)
        data = CacheData(
            values = resolvedFlags.associate {
                it.flag to CacheEntry(
                    it.variant,
<<<<<<< HEAD
                    Value.Structure(it.value.asMap()),
=======
                    Value.Structure(it.value?.asMap() ?: mapOf()),
>>>>>>> 43375cb (Transfer codebase)
                    it.reason
                )
            },
            evaluationContextHash = evaluationContext.hashCode(),
<<<<<<< HEAD
            resolveToken = resolveToken
        )
=======
            resolveToken = resolveToken)
>>>>>>> 43375cb (Transfer codebase)
    }

    override fun resolve(flagName: String, ctx: EvaluationContext): CacheResolveResult {
        val dataSnapshot = data ?: return CacheResolveResult.NotFound
        val cacheEntry = dataSnapshot.values[flagName] ?: return CacheResolveResult.NotFound
<<<<<<< HEAD
        if (ctx.hashCode() != dataSnapshot.evaluationContextHash) {
            return CacheResolveResult.Stale
        }
        return CacheResolveResult.Found(
            CacheResolveEntry(cacheEntry.variant, cacheEntry.value, dataSnapshot.resolveToken, cacheEntry.reason)
        )
=======
         if (ctx.hashCode() != dataSnapshot.evaluationContextHash) {
            return CacheResolveResult.Stale
        }
        return CacheResolveResult.Found(CacheResolveEntry(cacheEntry.variant, cacheEntry.value, dataSnapshot.resolveToken, cacheEntry.reason))
>>>>>>> 43375cb (Transfer codebase)
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