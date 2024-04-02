package com.spotify.confidence

import com.spotify.confidence.client.ConfidenceValueMap
import com.spotify.confidence.client.ResolveReason
import com.spotify.confidence.client.ResolvedFlag
import kotlinx.serialization.Serializable

data class Evaluation<T>(
    val value: T,
    val variant: String? = null,
    val reason: ResolveReason,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null
)

@Serializable
data class FlagResolution(
    val context: ConfidenceValueMap,
    val flags: List<ResolvedFlag>,
    val resolveToken: String
)

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: Throwable = Throwable()) : Result<Nothing>()
}