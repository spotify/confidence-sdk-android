package dev.openfeature.contrib.providers.client

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceClient {
    suspend fun resolve(flags: List<String>, ctx: EvaluationContext): ResolveResponse
    suspend fun apply(flags: List<AppliedFlag>, resolveToken: String): Result
}

sealed class Result {
    object Success : Result()
    object Failure : Result()
}