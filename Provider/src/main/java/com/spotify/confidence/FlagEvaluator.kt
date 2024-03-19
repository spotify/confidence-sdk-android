package com.spotify.confidence

import com.spotify.confidence.client.ResolvedFlag
import kotlinx.serialization.Serializable

interface FlagEvaluator: Contextual {
    suspend fun <T> getValue(flag: String, defaultValue: T): T
}

data class Evaluation<T>(
    val reason: String,
    val value: T,
)

@Serializable
data class FlagResolution(
    val context: Map<String,@kotlinx.serialization.Contextual  ConfidenceValue>,
    val flags: List<@kotlinx.serialization.Contextual ResolvedFlag>,
    val resolveToken: String
)

fun <T> FlagResolution.getEvaluation(flag: String, defaultValue: T): Evaluation<T> {
    TODO()
}
fun <T> FlagResolution.getValue(flag: String, defaultValue: T): T {
    return getEvaluation(flag, defaultValue).value
}