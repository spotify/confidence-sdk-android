package com.spotify.confidence

import com.spotify.confidence.client.ConfidenceValueMap
import com.spotify.confidence.client.Sdk
import com.spotify.confidence.client.await
import com.spotify.confidence.client.serializers.DateSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    @Serializable(DateSerializer::class)
    val sendTime: Date
)

@Serializable
data class Event(
    val eventDefinition: String,
    @Serializable(DateSerializer::class)
    val eventTime: Date,
    val payload: ConfidenceValueMap,
    val context: ConfidenceValueMap
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
        val httpRequest = Request.Builder()
            .url(BASE_URL)
            .headers(headers)
            .post(Json.encodeToString(events).toRequestBody())
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