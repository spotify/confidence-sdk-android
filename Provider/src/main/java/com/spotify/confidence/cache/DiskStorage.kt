package com.spotify.confidence.cache

import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.client.ResolvedFlag
import dev.openfeature.sdk.EvaluationContext

interface DiskStorage {
    fun store(
        resolvedFlags: List<ResolvedFlag>,
        resolveToken: String,
        evaluationContext: EvaluationContext
    ): CacheData
    fun read(): CacheData?

    fun clear()
    fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>)
    fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>>
}