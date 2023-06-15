package dev.openfeature.contrib.providers.client

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Structure
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.OpenFeatureError.GeneralError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.lang.reflect.Type
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ConfidenceRemoteClient : ConfidenceClient {
    private val clientSecret: String
    private val okHttpClient: OkHttpClient
    private val baseUrl: String
    private val headers: Headers
    private val clock: Clock
    private val dispatcher: CoroutineDispatcher

    constructor(clientSecret: String, region: ConfidenceRegion, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        this.clientSecret = clientSecret
        this.okHttpClient = OkHttpClient()
        this.headers = Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
        baseUrl = when (region) {
            ConfidenceRegion.EUROPE -> "https://resolver.eu.confidence.dev"
            ConfidenceRegion.USA -> "https://resolver.us.confidence.dev"
        }
        this.clock = Clock.systemUTC()
        this.dispatcher = dispatcher
    }

    internal constructor(
        clientSecret: String = "",
        baseUrl: HttpUrl,
        clock: Clock = Clock.systemUTC(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        this.clientSecret = clientSecret
        this.okHttpClient = OkHttpClient()
        this.headers = Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
        this.baseUrl = baseUrl.toString()
        this.clock = clock
        this.dispatcher = dispatcher
    }

    companion object {
        private val gson = GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .registerTypeAdapter(Structure::class.java, StructureTypeAdapter())
            .registerTypeAdapter(SchemaType.SchemaStruct::class.java, SchemaTypeAdapter())
            .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
            .create()
    }

    enum class ConfidenceRegion {
        EUROPE,
        USA
    }

    override suspend fun resolve(flags: List<String>, ctx: EvaluationContext): ResolveFlagsResponse =
        withContext(dispatcher) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/flags:resolve")
                    .headers(headers)
                    .post(
                        gson.toJson(
                            ResolveFlagsRequest(
                                flags.map { "flags/$it" },
                                getEvaluationContextStruct(ctx),
                                clientSecret,
                                false
                            )
                        ).toRequestBody()
                    )
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GeneralError("Unexpected code $response")
                    return@withContext processResolveResponse(response)
                }
            } catch (err: Error) {
                throw GeneralError("Error when executing resolve request: ${err.localizedMessage}")
            }
        }

    override suspend fun apply(flags: List<AppliedFlag>, resolveToken: String) = withContext(dispatcher) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/v1/flags:apply")
                .headers(headers)
                .post(
                    gson.toJson(
                        ApplyFlagsRequest(
                            flags.map { AppliedFlag("flags/${it.flag}", it.applyTime) },
                            clock.instant(),
                            clientSecret,
                            resolveToken
                        )
                    ).toRequestBody()
                )
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GeneralError("Unexpected code $response")
            }
        } catch (err: Error) {
            throw GeneralError("Error when executing resolve request: ${err.localizedMessage}")
        }
    }

    private fun processResolveResponse(response: Response): ResolveFlagsResponse {
        val type = object : TypeToken<ResolveFlagsResponse>() {}.type
        val resolveFlagsResponse =
            gson.fromJson<ResolveFlagsResponse?>(response.body!!.string(), type)
        resolveFlagsResponse.resolvedFlags =
            resolveFlagsResponse.resolvedFlags.map { resolvedFlag ->
                // Remove resource type prefix "flags/"
                val flagNameSplits = resolvedFlag.flag.split("/")
                if (flagNameSplits.count() < 2 || flagNameSplits[0] != "flags") {
                    throw ParseError("Unexpected flag name in resolve flag data: ${resolvedFlag.flag}")
                }
                // Verify generic JSON->Value parsing against Confidence-defined flagSchema
                // Apply Value-type translations according to flagSchema, if needed
                val newValue: Map<String, Value>? = resolvedFlag.value?.asMap()?.mapValues {
                    verifyAndConvert(
                        it.key,
                        it.value,
                        resolvedFlag.flagSchema ?: SchemaType.SchemaStruct(mapOf())
                    )
                }
                when (newValue) {
                    null -> resolvedFlag.copy(flag = flagNameSplits[1])
                    else -> resolvedFlag.copy(
                        flag = flagNameSplits[1],
                        value = MutableStructure(newValue.toMutableMap())
                    )
                }
            }
        return resolveFlagsResponse
    }

    private fun verifyAndConvert(key: String, value: Value, schemaStruct: SchemaType.SchemaStruct): Value {
        return when (schemaStruct.schema[key]) {
            is SchemaType.StringSchema -> when (value) {
                is Value.Instant -> Value.String(value.instant.toString())
                is Value.String -> value
                is Value.Null -> value
                else -> { throw ParseError("Incompatible value \"$key\" for schema") }
            }
            SchemaType.DoubleSchema -> when (value) {
                is Value.Integer -> {
                    val intValue = value.asInteger()
                        ?: throw GeneralError("Internal error when processing Integer value $value")
                    Value.Double(intValue.toDouble())
                }
                is Value.Double -> value
                is Value.Null -> value
                else -> { throw ParseError("Incompatible value \"$key\" for schema") }
            }
            is SchemaType.SchemaStruct -> when (value) {
                is Value.Structure -> {
                    return Value.Structure(
                        value.asStructure()?.mapValues {
                            verifyAndConvert(it.key, it.value, schemaStruct.schema[key] as SchemaType.SchemaStruct)
                        }
                            ?: mapOf()
                    )
                }
                is Value.Null -> value
                else -> { throw ParseError("Incompatible value \"$key\" for schema") }
            }
            SchemaType.BoolSchema -> when (value) {
                is Value.Boolean -> value
                is Value.Null -> value
                else -> { throw ParseError("Incompatible value \"$key\" for schema") }
            }
            SchemaType.IntSchema -> when (value) {
                is Value.Integer -> value
                is Value.Null -> value
                else -> { throw ParseError("Incompatible value \"$key\" for schema") }
            }
            null -> { throw ParseError("Couldn't find value \"$key\" in schema") }
        }
    }

    private fun getEvaluationContextStruct(ctx: EvaluationContext): Structure {
        val ctxAttributes: MutableMap<String, Value> =
            mutableMapOf(Pair("targeting_key", Value.String(ctx.getTargetingKey())))
        ctxAttributes.putAll(ctx.asMap())
        return MutableStructure(ctxAttributes)
    }
}

/*
 * Adapters
 */

class SchemaTypeAdapter :
    JsonDeserializer<SchemaType.SchemaStruct>,
    JsonSerializer<SchemaType.SchemaStruct> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SchemaType.SchemaStruct {
        return convertSchemaStruct(json)
    }

    private fun convertSchemaStruct(value: JsonElement?): SchemaType.SchemaStruct {
        val map: MutableMap<String, SchemaType> = mutableMapOf()
        val schemaData: JsonElement? = value?.asJsonObject?.asMap()?.get("schema")
        val iterator = schemaData?.asJsonObject?.entrySet()?.iterator()
        while (iterator?.hasNext() == true) {
            val next: MutableMap.MutableEntry<String, JsonElement> = iterator.next()
            map[next.key] = convertSchemaType(next.value)
        }
        return SchemaType.SchemaStruct(map)
    }

    private fun convertSchemaType(value: JsonElement): SchemaType {
        if (value.asJsonObject.keySet().contains("stringSchema")) {
            return SchemaType.StringSchema
        } else if (value.asJsonObject.keySet().contains("structSchema")) {
            return convertSchemaStruct(value.asJsonObject.get("structSchema"))
        } else if (value.asJsonObject.keySet().contains("boolSchema")) {
            return SchemaType.BoolSchema
        } else if (value.asJsonObject.keySet().contains("intSchema")) {
            return SchemaType.IntSchema
        } else if (value.asJsonObject.keySet().contains("doubleSchema")) {
            return SchemaType.DoubleSchema
        }
        throw ParseError("Unrecognized flag schema identifier: ${value.asJsonObject.keySet()}")
    }

    override fun serialize(
        src: SchemaType.SchemaStruct?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        throw GeneralError("Serialization of flag schema not supported")
    }
}

class StructureTypeAdapter : JsonDeserializer<Structure>, JsonSerializer<Structure> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Structure {
        val map: MutableMap<String, Value> = mutableMapOf()
        val iterator = json?.asJsonObject?.entrySet()?.iterator()
        while (iterator?.hasNext() == true) {
            val next: MutableMap.MutableEntry<String, JsonElement> = iterator.next()
            map[next.key] = convert(next.value)
        }
        return MutableStructure(map)
    }

    /**
     * Generic JSON->Value conversion that doesn't use Confidence schema and rather infer Value type
     * from the format and type of the JsonElement itself
     */
    private fun convert(value: JsonElement): Value {
        if (value.isJsonPrimitive) {
            if (value.asJsonPrimitive.isString) {
                return try {
                    Value.Instant(Instant.parse(value.asString))
                } catch (e: Exception) {
                    Value.String(value.asString)
                }
            } else if (value.asJsonPrimitive.isNumber) {
                return if (value.asDouble.rem(1).equals(0.0)) {
                    Value.Integer(value.asInt)
                } else {
                    Value.Double(value.asDouble)
                }
            } else if (value.asJsonPrimitive.isBoolean) {
                return Value.Boolean(value.asBoolean)
            }
        } else if (value.isJsonNull) {
            return Value.Null
        } else if (value.isJsonArray) {
            return Value.List(value.asJsonArray.map { element -> convert(element) })
        } else if (value.isJsonObject) {
            return Value.Structure(value.asJsonObject.asMap().mapValues { v -> convert(v.value) })
        }
        throw ParseError("Couldn't parse JSON value: $value")
    }

    override fun serialize(
        src: Structure?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return convertStructure(src)
    }

    private fun convertStructure(src: Structure?): JsonElement {
        val jsonObject = JsonObject()
        src?.asMap()?.forEach { entry: Map.Entry<String, Value> ->
            when (val value = entry.value) {
                is Value.String -> jsonObject.addProperty(entry.key, value.asString())
                is Value.Boolean -> jsonObject.addProperty(entry.key, value.asBoolean())
                is Value.Instant -> jsonObject.add(entry.key, Gson().toJsonTree(value.asInstant().toString()))
                is Value.Double -> jsonObject.addProperty(entry.key, value.asDouble())
                is Value.Integer -> jsonObject.addProperty(entry.key, value.asInteger())
                is Value.List -> jsonObject.add(entry.key, convertArray(entry.value.asList()))
                Value.Null -> jsonObject.add(entry.key, null)
                is Value.Structure -> {
                    val attributes = value.asStructure()?.toMutableMap()
                    if (attributes != null) {
                        jsonObject.add(entry.key, convertStructure(MutableStructure(attributes)))
                    } else {
                        throw GeneralError("Error while serializing nested Structure")
                    }
                }
            }
        }
        return jsonObject
    }

    private fun convertArray(value: List<Value>?): JsonArray {
        val jsonArray = JsonArray()
        value?.forEach { v ->
            when (v) {
                is Value.String -> jsonArray.add(v.string)
                is Value.Boolean -> jsonArray.add(v.boolean)
                is Value.Instant -> jsonArray.add(v.asInstant().toString())
                is Value.Double -> jsonArray.add(v.asDouble())
                is Value.Integer -> jsonArray.add(v.asInteger())
                is Value.List -> jsonArray.add(convertArray(v.asList()))
                Value.Null -> jsonArray.add(JsonNull.INSTANCE)
                is Value.Structure -> {
                    val attributes = v.asStructure()?.toMutableMap()
                    if (attributes != null) {
                        jsonArray.add(convertStructure(MutableStructure(attributes)))
                    } else {
                        throw GeneralError("Error while serializing nested Structure")
                    }
                }
            }
        }
        return jsonArray
    }
}

class InstantTypeAdapter : JsonDeserializer<Instant>, JsonSerializer<Instant> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Instant {
        val asString = json?.asJsonPrimitive?.asString
        return Instant.parse(asString)
    }

    override fun serialize(
        src: Instant?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.toString())
    }
}

/*
 * Data models
 */

data class ResolveFlagsRequest(
    var flags: List<String>,
    var evaluationContext: Structure,
    var clientSecret: String,
    var apply: Boolean
)

data class ResolveFlagsResponse(var resolvedFlags: List<ResolvedFlag>, var resolveToken: String)

data class ResolvedFlag(
    var flag: String,
    var variant: String,
    var value: Structure?,
    var flagSchema: SchemaType.SchemaStruct?,
    var reason: ResolveReason
)

data class ApplyFlagsRequest(
    var flags: List<AppliedFlag>,
    var sendTime: Instant,
    var clientSecret: String,
    var resolveToken: String
)

data class AppliedFlag(
    var flag: String,
    var applyTime: Instant
)

sealed interface SchemaType {
    data class SchemaStruct(
        var schema: Map<String, SchemaType>
    ) : SchemaType
    object IntSchema : SchemaType
    object DoubleSchema : SchemaType
    object StringSchema : SchemaType
    object BoolSchema : SchemaType
}

enum class ResolveReason {
    // Unspecified enum.
    RESOLVE_REASON_UNSPECIFIED,

    // The flag was successfully resolved because one rule matched.
    RESOLVE_REASON_MATCH,

    // The flag could not be resolved because no rule matched.
    RESOLVE_REASON_NO_SEGMENT_MATCH,

    // The flag could not be resolved because the matching rule had no variant
    // that could be assigned.
    RESOLVE_REASON_NO_TREATMENT_MATCH,

    // The flag could not be resolved because it was archived.
    RESOLVE_REASON_FLAG_ARCHIVED
}