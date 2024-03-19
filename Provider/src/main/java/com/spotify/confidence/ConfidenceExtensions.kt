package com.spotify.confidence

import com.spotify.confidence.client.ResolveResponse

internal suspend fun Confidence.resolveFlags(flags: List<String>): ResolveResponse {
    return flagResolver.resolve(flags)
}