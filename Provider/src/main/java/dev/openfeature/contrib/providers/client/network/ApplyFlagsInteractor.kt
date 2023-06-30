package dev.openfeature.contrib.providers.client.network

import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.await
import dev.openfeature.contrib.providers.client.serializers.InstantSerializer
import dev.openfeature.contrib.providers.client.serializers.StructureSerializer
import dev.openfeature.contrib.providers.client.serializers.UUIDSerializer
import kotlinx.coroutines.CoroutineDispatcher
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
import java.time.Instant

internal interface ApplyFlagsInteractor : suspend (ApplyFlagsRequest) -> (Response)

internal class ApplyFlagsInteractorImpl(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val dispatcher: CoroutineDispatcher
) : ApplyFlagsInteractor {

    private val headers by lazy {
        Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
    }
    override suspend fun invoke(request: ApplyFlagsRequest): Response =
        withContext(dispatcher) {
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/flags:apply")
                .headers(headers)
                .post(json.encodeToString(request).toRequestBody())
                .build()

            return@withContext httpClient.newCall(httpRequest).await()
        }
}

@Serializable
internal data class ApplyFlagsRequest(
    val flags: List<AppliedFlag>,
    @Contextual
    val sendTime: Instant,
    val clientSecret: String,
    val resolveToken: String
)

private val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(InstantSerializer)
        contextual(StructureSerializer)
    }
}