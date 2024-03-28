package com.spotify.confidence.client

interface FlagApplierClient {
    suspend fun apply(flags: List<AppliedFlag>, resolveToken: String): Result
}

sealed class Result {
    object Success : Result()
    object Failure : Result()
}