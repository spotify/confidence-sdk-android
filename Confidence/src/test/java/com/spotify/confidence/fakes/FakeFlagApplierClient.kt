package com.spotify.confidence.fakes

import com.spotify.confidence.Result
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.FlagApplierClient

class FakeFlagApplierClient : FlagApplierClient {
    override suspend fun apply(flags: List<AppliedFlag>, resolveToken: String): Result<Unit> {
        TODO("Not yet implemented")
    }
}