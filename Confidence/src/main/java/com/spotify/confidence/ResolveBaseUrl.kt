package com.spotify.confidence

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun getResolveBaseUrl(
    region: ConfidenceRegion,
    resolveBaseUrl: String? = null
): HttpUrl {
    val baseUrl = resolveBaseUrl ?: when (region) {
        ConfidenceRegion.GLOBAL -> "https://resolver.confidence.dev"
        ConfidenceRegion.EUROPE -> "https://resolver.eu.confidence.dev"
        ConfidenceRegion.USA -> "https://resolver.us.confidence.dev"
    }
    return baseUrl.trimEnd('/').toHttpUrl()
}

internal fun HttpUrl.resolveEndpoint(operation: String): HttpUrl =
    newBuilder()
        .addPathSegments("v1/flags:$operation")
        .build()
