package com.spotify.confidence

import com.spotify.confidence.client.await
import com.spotify.confidence.client.serializers.StructureSerializer
import com.spotify.confidence.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val payload: Map<String, @Contextual EventValue>
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
        contextual(EventValueSerializer)
    }
}

object EventValueSerializer : KSerializer<EventValue> {
    override val descriptor = PrimitiveSerialDescriptor("EventValue", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): EventValue {
        require(decoder is JsonDecoder)
        when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                val jsonPrimitive = element.jsonPrimitive
                return when {
                    jsonPrimitive.isString -> EventValue.String(jsonPrimitive.content)
                    jsonPrimitive.booleanOrNull != null -> EventValue.Boolean(jsonPrimitive.boolean)
                    jsonPrimitive.intOrNull != null -> EventValue.Int(jsonPrimitive.int)
                    jsonPrimitive.doubleOrNull != null -> EventValue.Double(jsonPrimitive.double)
                    else -> TODO()
                }
            }
            else -> {
                val jsonObject = element
                    .jsonObject
                val map = jsonObject
                    .keys.associateWith {
                        Json.decodeFromJsonElement(
                            this,
                            jsonObject.getValue(it)
                        )
                    }
                return EventValue.Struct(map)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: EventValue) {
        when (value) {
            is EventValue.Int -> encoder.encodeInt(value.value)
            is EventValue.Boolean -> encoder.encodeBoolean(value.value)
            is EventValue.Double -> encoder.encodeDouble(value.value)
            is EventValue.String -> encoder.encodeString(value.value)
            is EventValue.Struct -> encoder.encodeStructure(StructureSerializer.descriptor) {
                for ((key, mapValue) in value.value) {
                    encodeStringElement(StructureSerializer.descriptor, 0, key)
                    encodeSerializableElement(
                        StructureSerializer.descriptor,
                        1,
                        this@EventValueSerializer,
                        mapValue
                    )
                }
            }
        }
    }
}