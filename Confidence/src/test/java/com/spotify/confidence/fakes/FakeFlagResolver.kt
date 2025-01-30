package com.spotify.confidence.fakes

import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.FlagResolution
import com.spotify.confidence.FlagResolver
import com.spotify.confidence.Result

class FakeFlagResolver : FlagResolver {
    override suspend fun resolve(flags: List<String>, context: Map<String, ConfidenceValue>): Result<FlagResolution> {
        TODO("Not yet implemented")
    }
}