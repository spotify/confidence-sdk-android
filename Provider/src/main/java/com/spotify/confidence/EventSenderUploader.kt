package com.spotify.confidence

import com.spotify.confidence.client.Sdk
import com.spotify.confidence.client.await
import com.spotify.confidence.client.serializers.DateTimeSerializer
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
import java.util.Date

internal interface EventSenderUploader {
    suspend fun upload(events: EventBatchRequest): Boolean
}

@Serializable
internal data class EventBatchRequest(
    val clientSecret: String,
    val sdk: Sdk,
    val events: List<Event>,
    @Serializable(DateTimeSerializer::class)
    val sendTime: Date
)

@Serializable
data class Event(
    val eventDefinition: String,
    @Serializable(DateTimeSerializer::class)
    val eventTime: Date,
    val payload: Map<String, @Contextual ConfidenceValue>
)

internal class EventSenderUploaderImpl(
    private val httpClient: OkHttpClient,
    private val dispatcher: CoroutineDispatcher
) : EventSenderUploader {
    private val headers by lazy {
        Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
    }

    override suspend fun upload(events: EventBatchRequest): Boolean = withContext(dispatcher) {
        val networkJson = Json {
            serializersModule = SerializersModule {
                contextual(NetworkConfidenceValueSerializer)
            }
        }
        val httpRequest = Request.Builder()
            .url(BASE_URL)
            .headers(headers)
            .post(networkJson.encodeToString(events).toRequestBody())
            .build()

        val response = httpClient.newCall(httpRequest).await()
        when (response.code) {
            // clean up in case of success
            200 -> true
            // we shouldn't cleanup for rate limiting
            // TODO("return retry-after")
            429 -> false
            // if batch couldn't be processed, we should clean it up
            in 401..499 -> true
            else -> false
        }
    }

    companion object {
        const val BASE_URL = "https://events.eu.confidence.dev/v1/events:publish"
    }
}