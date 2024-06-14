package com.spotify.confidence

import com.spotify.confidence.client.ResolveResponse
import com.spotify.confidence.client.Sdk
import com.spotify.confidence.client.SdkMetadata
import com.spotify.confidence.client.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.Headers
import okhttp3.HttpUrl
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
    private var sdkMetadata: SdkMetadata,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseUrl: HttpUrl? = null
) : FlagResolver {
    private val headers = Headers.headersOf(
        "Content-Type",
        "application/json",
        "Accept",
        "application/json"
    )
    internal fun setSdk(sdk: SdkMetadata) {
        sdkMetadata = sdk
    }
    override suspend fun resolve(flags: List<String>, context: Map<String, ConfidenceValue>): Result<FlagResolution> {
        val sdk = Sdk(sdkMetadata.sdkId, sdkMetadata.sdkVersion)
        val request = ResolveFlagsRequest(flags.map { "flags/$it" }, context, clientSecret, false, sdk)

        val response = withContext(dispatcher) {
            val jsonRequest = Json.encodeToString(request)
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

            is ResolveResponse.NotModified -> {
                Result.Success(FlagResolution.EMPTY)
            }
        }
    }

    private fun baseUrl() = baseUrl ?: when (region) {
        ConfidenceRegion.GLOBAL -> "https://resolver.confidence.dev"
        ConfidenceRegion.EUROPE -> "https://resolver.eu.confidence.dev"
        ConfidenceRegion.USA -> "https://resolver.us.confidence.dev"
    }
}

@Serializable
private data class ResolveFlagsRequest(
    val flags: List<String>,
    val evaluationContext: Map<String, @Serializable(NetworkConfidenceValueSerializer::class) ConfidenceValue>,
    val clientSecret: String,
    val apply: Boolean,
    val sdk: Sdk
)

private fun Response.toResolveFlags(): ResolveResponse {
    body?.let { body ->
        val bodyString = body.string()

        // building the json class responsible for serializing the object
        val networkJson = Json {
            serializersModule = SerializersModule {
                ignoreUnknownKeys = true
            }
        }
        return ResolveResponse.Resolved(networkJson.decodeFromString(bodyString))
    } ?: throw ConfidenceError.ParseError("Response body is null", listOf())
}