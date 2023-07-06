package dev.openfeature.contrib.providers.client.network

import dev.openfeature.contrib.providers.client.ResolveFlagsRequest
import dev.openfeature.contrib.providers.client.await
import dev.openfeature.contrib.providers.client.serializers.StructureSerializer
import dev.openfeature.contrib.providers.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

interface ResolveFlagsInteractor : suspend (ResolveFlagsRequest) -> (Response)

internal class ResolveFlagsInteractorImpl(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val dispatcher: CoroutineDispatcher
) : ResolveFlagsInteractor {

    private val headers by lazy {
        Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
    }
    override suspend fun invoke(request: ResolveFlagsRequest): Response =
        withContext(dispatcher) {
            val jsonRequest = json.encodeToString(request)
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/flags:resolve")
                .headers(headers)
                .post(jsonRequest.toRequestBody())
                .build()

            return@withContext httpClient.newCall(httpRequest).await()
        }
}

private val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
        contextual(StructureSerializer)
    }
}