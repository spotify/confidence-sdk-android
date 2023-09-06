package dev.openfeature.contrib.providers.cache

import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.sdk.EvaluationContext

interface DiskStorage {
    fun store(
        resolvedFlags: List<ResolvedFlag>,
        resolveToken: String,
        evaluationContext: EvaluationContext
    ): CacheData
    fun read(): CacheData?

    fun clear()
}