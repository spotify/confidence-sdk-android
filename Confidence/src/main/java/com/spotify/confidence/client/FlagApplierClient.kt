package com.spotify.confidence.client

import com.spotify.confidence.Result

interface FlagApplierClient {
    suspend fun apply(flags: List<AppliedFlag>, resolveToken: String): Result<Unit>
}