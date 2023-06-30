package dev.openfeature.contrib.providers.client

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceClient {
    suspend fun resolve(flags: List<String>, ctx: EvaluationContext): ResolveFlags
    suspend fun apply(flags: List<AppliedFlag>, resolveToken: String)
}