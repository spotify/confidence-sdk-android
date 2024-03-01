package com.spotify.confidence

import com.spotify.confidence.client.await
import com.spotify.confidence.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
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
    suspend fun upload(events: EventBatch): Boolean
}

/**
 * {
 *   "clientSecret": "my-client-secret",
 *   "events": [
 *     {
 *       "eventDefinition": "eventDefinitions/navigate",
 *       "eventTime": "2018-01-01T00:00:00Z",
 *       "payload": {
 *         "user_id": "1234",
 *         "current": "home-page",
 *         "target": "profile-page"
 *       }
 *     }
 *   ],
 *   "sendTime": "2018-01-01T00:00:00Z"
 * }
 */

@Serializable
internal data class EventBatch(
    val clientSecret: String,
    val events: List<Event>,
    @Contextual
    val sendTime: Date
)

@Serializable
data class Event(
    val eventDefinition: String,
    @Contextual
    val eventTime: Date,
    val payload: Map<String, String>
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

    override suspend fun upload(events: EventBatch): Boolean = withContext(dispatcher) {
        val httpRequest = Request.Builder()
            .url(BASE_URL)
            .headers(headers)
            .post(eventsJson.encodeToString(events).toRequestBody())
            .build()

        val response = httpClient.newCall(httpRequest).await()
        val statusCode = response.code
        /**
         * if server can't handle the batch, we should throw it away
         * except for rate limiting
         * here backend can be more specific
         */
        !((statusCode / 100) == 4 && statusCode != 429)
    }

    companion object {
        const val BASE_URL = "https://events.eu.confidence.dev/v1/events:publish"
    }
}

internal val eventsJson = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
    }
}