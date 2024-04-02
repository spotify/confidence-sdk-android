package com.spotify.confidence.client.serializers

import android.annotation.SuppressLint
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.ParseError
import com.spotify.confidence.client.Flags
import com.spotify.confidence.client.ResolveReason
import com.spotify.confidence.client.ResolvedFlag
import com.spotify.confidence.client.SchemaType
import dev.openfeature.sdk.ValueSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/***
 * the struct serializer needed for sending the resolve request
 */

internal object StructSerializer : KSerializer<ConfidenceValue.Struct> {
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): ConfidenceValue.Struct {
        error("no deserializer is needed")
    }

    override fun serialize(encoder: Encoder, value: ConfidenceValue.Struct) {
        encoder.encodeStructure(descriptor) {
            for ((key, mapValue) in value.map) {
                encodeStringElement(descriptor, 0, key)
                encodeSerializableElement(descriptor, 1, ConfidenceValueSerializer, mapValue)
            }
        }
    }
}

internal object ConfidenceValueSerializer : KSerializer<ConfidenceValue> {
    override val descriptor: SerialDescriptor
        get() = ValueSerializer.descriptor

    override fun deserialize(decoder: Decoder): ConfidenceValue {
        require(decoder is JsonDecoder)
        when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                val jsonPrimitive = element.jsonPrimitive
                return when {
                    jsonPrimitive.isString -> ConfidenceValue.String(jsonPrimitive.content)
                    jsonPrimitive.booleanOrNull != null -> ConfidenceValue.Boolean(jsonPrimitive.boolean)
                    jsonPrimitive.intOrNull != null -> ConfidenceValue.Integer(jsonPrimitive.int)
                    jsonPrimitive.doubleOrNull != null -> ConfidenceValue.Double(jsonPrimitive.double)
                    else -> ConfidenceValue.Null
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
                return ConfidenceValue.Struct(map)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: ConfidenceValue) {
        when (value) {
            is ConfidenceValue.String -> encoder.encodeString(value.string)
            is ConfidenceValue.Boolean -> encoder.encodeBoolean(value.boolean)
            is ConfidenceValue.Double -> encoder.encodeDouble(value.double)

            is ConfidenceValue.Integer -> encoder.encodeInt(value.integer)

            ConfidenceValue.Null -> encoder.encodeNull()
            is ConfidenceValue.Struct -> encoder.encodeSerializableValue(
                StructSerializer,
                ConfidenceValue.Struct(value.map)
            )

            is ConfidenceValue.List -> encoder.encodeSerializableValue(
                ListSerializer(ConfidenceValueSerializer),
                value.list
            )

            is ConfidenceValue.Date -> encoder.encodeSerializableValue(
                DateSerializer,
                value.date
            )
        }
    }
}

/***
 * the serializers needed for serializing the resolve flags response
 */

internal object SchemaTypeSerializer : KSerializer<SchemaType.SchemaStruct> {
    override val descriptor: SerialDescriptor
        get() = MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): SchemaType.SchemaStruct {
        val jsonDecoder = decoder as JsonDecoder
        val jsonElement = jsonDecoder.decodeJsonElement()
        val schemaMap = mutableMapOf<String, SchemaType>()
        for ((key, value) in jsonElement.jsonObject) {
            schemaMap[key] = value.convertToSchemaTypeValue()
        }
        return SchemaType.SchemaStruct(schemaMap)
    }

    override fun serialize(encoder: Encoder, value: SchemaType.SchemaStruct) {
        // we never serialize the object to send it over the wire
        error("no serialization is needed")
    }
}

internal object NetworkResolvedFlagSerializer : KSerializer<ResolvedFlag> {
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): ResolvedFlag {
        val jsonDecoder = decoder as JsonDecoder
        val json = jsonDecoder.decodeJsonElement().jsonObject
        val flag = json["flag"].toString().split("/")
            .last()
            .replace("\"", "")

        val variant = json["variant"]
            .toString()
            .replace("\"", "")

        val resolvedReason = Json.decodeFromString<ResolveReason>(json["reason"].toString())
        val flagSchemaJsonElement = json["flagSchema"]

        val schemasJson = if (flagSchemaJsonElement != null && flagSchemaJsonElement != JsonNull) {
            flagSchemaJsonElement.jsonObject["schema"]
        } else {
            null
        }

        return if (schemasJson != null) {
            val flagSchema =
                Json.decodeFromString(SchemaTypeSerializer, schemasJson.toString())
            val valueJson = json["value"].toString()
            val values: ConfidenceValue.Struct =
                Json.decodeFromString(FlagValueSerializer(flagSchema), valueJson)

            if (flagSchema.schema.size != values.map.size) {
                throw ParseError("Unexpected flag name in resolve flag data: $flag")
            }

            ResolvedFlag(
                flag = flag,
                variant = variant,
                reason = resolvedReason,
                value = values.map
            )
        } else {
            ResolvedFlag(
                flag = flag,
                variant = variant,
                reason = resolvedReason,
                value = mutableMapOf()
            )
        }
    }

    override fun serialize(encoder: Encoder, value: ResolvedFlag) {
        error("no serialization is needed")
    }
}

internal class FlagValueSerializer(
    private val schemaStruct: SchemaType.SchemaStruct,
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), String.serializer()).descriptor
) : KSerializer<ConfidenceValue.Struct> {
    override fun deserialize(decoder: Decoder): ConfidenceValue.Struct {
        val valueMap = mutableMapOf<String, ConfidenceValue>()
        val jsonDecoder = decoder as JsonDecoder
        val jsonElement = jsonDecoder.decodeJsonElement()
        for ((key, value) in jsonElement.jsonObject) {
            schemaStruct.schema[key]?.let {
                valueMap[key] = value.convertToValue(key, it)
            } ?: throw ParseError("Couldn't find value \"$key\" in schema")
        }

        return ConfidenceValue.Struct(valueMap)
    }

    override fun serialize(encoder: Encoder, value: ConfidenceValue.Struct) {
        error("no serialization is needed")
    }
}

internal object FlagsSerializer : KSerializer<Flags> {
    override val descriptor: SerialDescriptor
        get() = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Flags {
        val list = mutableListOf<ResolvedFlag>()
        val jsonDecoder = decoder as JsonDecoder
        val array = jsonDecoder.decodeJsonElement().jsonArray
        for (json in array) {
            list.add(Json.decodeFromString(NetworkResolvedFlagSerializer, json.toString()))
        }
        return Flags(list)
    }

    override fun serialize(encoder: Encoder, value: Flags) {
        error("flags won't be serialized")
    }
}

private fun JsonElement.convertToValue(key: String, schemaType: SchemaType): ConfidenceValue = when (schemaType) {
    is SchemaType.BoolSchema -> toString().toBooleanStrictOrNull()?.let(ConfidenceValue::Boolean)
        ?: ConfidenceValue.Null
    is SchemaType.DoubleSchema -> {
        toString().toDoubleOrNull()?.let(ConfidenceValue::Double) ?: ConfidenceValue.Null
    }
    is SchemaType.IntSchema -> {
        // passing double number to an integer schema
        if (toString().contains(".")) {
            throw ParseError("Incompatible value \"$key\" for schema")
        }
        toString().toIntOrNull()?.let { ConfidenceValue.Integer(it) } ?: ConfidenceValue.Null
    }
    is SchemaType.SchemaStruct -> {
        if (jsonObject.isEmpty()) {
            ConfidenceValue.Struct(mapOf())
        } else {
            val serializedMap = Json.decodeFromString(
                FlagValueSerializer(schemaType),
                jsonObject.toString()
            ).map

            ConfidenceValue.Struct(serializedMap)
        }
    }
    is SchemaType.StringSchema -> if (!jsonPrimitive.isString) {
        ConfidenceValue.Null
    } else {
        ConfidenceValue.String(toString().replace("\"", ""))
    }
}

private fun JsonElement.convertToSchemaTypeValue(): SchemaType = when {
    jsonObject.keys.contains("stringSchema") -> SchemaType.StringSchema
    jsonObject.keys.contains("doubleSchema") -> SchemaType.DoubleSchema
    jsonObject.keys.contains("intSchema") -> SchemaType.IntSchema
    jsonObject.keys.contains("boolSchema") -> SchemaType.BoolSchema
    jsonObject.keys.contains("structSchema") -> {
        val value = jsonObject["structSchema"]!!.jsonObject["schema"]
        SchemaType.SchemaStruct(
            Json.decodeFromString(SchemaTypeSerializer, value.toString()).schema
        )
    }
    else -> error("not a valid schema")
}

@SuppressLint("SimpleDateFormat")
internal object DateSerializer : KSerializer<Date> {
    private val dateFormatter =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val fallbackDateFormatter =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply { timeZone = TimeZone.getTimeZone("UTC") }
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeString(dateFormatter.format(value))
    override fun deserialize(decoder: Decoder): Date = with(decoder.decodeString()) {
        try {
            dateFormatter.parse(this)
                ?: throw IllegalArgumentException("unable to parse $this")
        } catch (e: Exception) {
            fallbackDateFormatter.parse(this)
                ?: throw IllegalArgumentException("unable to parse $this")
        }
    }
}