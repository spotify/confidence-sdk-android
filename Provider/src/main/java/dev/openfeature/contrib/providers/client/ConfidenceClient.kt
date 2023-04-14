package dev.openfeature.contrib.providers.client

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceClient {
<<<<<<< HEAD
    suspend fun resolve(flags: List<String>, ctx: EvaluationContext): ResolveFlags
    suspend fun apply(flags: List<AppliedFlag>, resolveToken: String)
=======
    fun resolve(flags: List<String>, ctx: EvaluationContext): ResolveFlagsResponse
    fun apply(flags: List<AppliedFlag>, resolveToken: String)
>>>>>>> 43375cb (Transfer codebase)
}