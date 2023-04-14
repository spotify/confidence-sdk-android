package dev.openfeature.contrib.providers.client

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceClient {
    fun resolve(flags: List<String>, ctx: EvaluationContext): ResolveFlagsResponse
    fun apply(flags: List<AppliedFlag>, resolveToken: String)
}