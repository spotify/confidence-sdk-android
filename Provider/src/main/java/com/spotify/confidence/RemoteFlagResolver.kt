package com.spotify.confidence

import com.spotify.confidence.client.ConfidenceRegion
import com.spotify.confidence.client.ResolveResponse
import com.spotify.confidence.client.Sdk
import com.spotify.confidence.client.SdkMetadata
import com.spotify.confidence.client.await
import com.spotify.confidence.client.serializers.ConfidenceValueSerializer
import com.spotify.confidence.client.serializers.FlagsSerializer
import com.spotify.confidence.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal interface FlagResolver {
    suspend fun resolve(flags: List<String>, context: Map<String, ConfidenceValue>): Result<FlagResolution>
}

internal class RemoteFlagResolver(
    private val clientSecret: String,
    private val region: ConfidenceRegion,
    private val httpClient: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sdkMetadata: SdkMetadata
) : FlagResolver {
    private val headers = Headers.headersOf(
        "Content-Type",
        "application/json",
        "Accept",
        "application/json"
    )
    override suspend fun resolve(flags: List<String>, context: Map<String, ConfidenceValue>): Result<FlagResolution> {
        val sdk = Sdk(sdkMetadata.sdkId, sdkMetadata.sdkVersion)
        val request = ResolveFlagsRequest(flags, context, clientSecret, false, sdk)

        val response = withContext(dispatcher) {
            val jsonRequest = json.encodeToString(request)
            val httpRequest = Request.Builder()
                .url("${baseUrl()}/v1/flags:resolve")
                .headers(headers)
                .post(jsonRequest.toRequestBody())
                .build()

            httpClient.newCall(httpRequest).await().toResolveFlags()
        }

        return when (response) {
            is ResolveResponse.Resolved -> {
                val (flagList, resolveToken) = response.flags
                Result.Success(FlagResolution(context, flagList.list, resolveToken))
            }

            else -> {
                Result.Failure(Error("could not return flag resolution"))
            }
        }
    }

    private fun baseUrl() = when (region) {
        ConfidenceRegion.GLOBAL -> "https://resolver.confidence.dev"
        ConfidenceRegion.EUROPE -> "https://resolver.eu.confidence.dev"
        ConfidenceRegion.USA -> "https://resolver.us.confidence.dev"
    }
}

private val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
        contextual(ConfidenceValueSerializer)
    }
}

@Serializable
private data class ResolveFlagsRequest(
    val flags: List<String>,
    val evaluationContext: Map<String, @Contextual ConfidenceValue>,
    val clientSecret: String,
    val apply: Boolean,
    val sdk: Sdk
)

private fun Response.toResolveFlags(): ResolveResponse {
    val bodyString = body!!.string()

    // building the json class responsible for serializing the object
    val networkJson = Json {
        serializersModule = SerializersModule {
            contextual(FlagsSerializer)
            ignoreUnknownKeys = true
        }
    }
    return ResolveResponse.Resolved(networkJson.decodeFromString(bodyString))
}