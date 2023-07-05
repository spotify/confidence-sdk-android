package dev.openfeature.contrib.providers.client.serializers

import dev.openfeature.contrib.providers.client.Flags
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.contrib.providers.client.SchemaType
import dev.openfeature.sdk.DateSerializer
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Structure
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.ValueSerializer
import dev.openfeature.sdk.exceptions.OpenFeatureError
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/***
 * the struct serializer needed for sending the resolve request
 */

internal object StructureSerializer : KSerializer<Structure> {
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Structure {
        error("no deserializer is needed")
    }

    override fun serialize(encoder: Encoder, value: Structure) {
        encoder.encodeStructure(descriptor) {
            value.asMap().forEach { (key, value) ->
                encodeStringElement(descriptor, 0, key)
                encodeSerializableElement(descriptor, 1, StructureValueSerializer, value)
            }
        }
    }
}

private object StructureValueSerializer : KSerializer<Value> {
    override val descriptor: SerialDescriptor
        get() = ValueSerializer.descriptor

    override fun deserialize(decoder: Decoder): Value {
        error("we don't need to deserialize here")
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Value) {
        when (value) {
            is Value.String -> encoder.encodeString(value.string)
            is Value.Boolean -> encoder.encodeBoolean(value.boolean)
            is Value.Double -> encoder.encodeDouble(value.double)
            is Value.Instant -> encoder.encodeSerializableValue(
                DateSerializer,
                value.instant
            )

            is Value.Integer -> encoder.encodeInt(value.integer)
            is Value.List -> encoder.encodeSerializableValue(
                ListSerializer(StructureValueSerializer),
                value.list
            )

            Value.Null -> encoder.encodeNull()
            is Value.Structure -> encoder.encodeSerializableValue(
                StructureSerializer,
                MutableStructure(value.structure.toMutableMap())
            )
        }
    }
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
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
            val values: Structure =
                Json.decodeFromString(FlagValueSerializer(flagSchema), valueJson)

            if (flagSchema.schema.size != values.asMap().size) {
                throw OpenFeatureError.ParseError("Unexpected flag name in resolve flag data: $flag")
            }

            ResolvedFlag(
                flag = flag,
                variant = variant,
                reason = resolvedReason,
                value = values
            )
        } else {
            ResolvedFlag(
                flag = flag,
                variant = variant,
                reason = resolvedReason,
                value = MutableStructure(mutableMapOf())
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
) : KSerializer<Structure> {
    override fun deserialize(decoder: Decoder): Structure {
        val valueMap = mutableMapOf<String, Value>()
        val jsonDecoder = decoder as JsonDecoder
        val jsonElement = jsonDecoder.decodeJsonElement()
        for ((key, value) in jsonElement.jsonObject) {
            schemaStruct.schema[key]?.let {
                valueMap[key] = value.convertToValue(key, it)
            } ?: throw OpenFeatureError.ParseError("Couldn't find value \"$key\" in schema")
        }

        return MutableStructure(valueMap)
    }

    override fun serialize(encoder: Encoder, value: Structure) {
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

private fun JsonElement.convertToValue(key: String, schemaType: SchemaType): Value = when (schemaType) {
    is SchemaType.BoolSchema -> toString().toBooleanStrictOrNull()?.let(Value::Boolean) ?: Value.Null
    is SchemaType.DoubleSchema -> {
        toString().toDoubleOrNull()?.let(Value::Double) ?: Value.Null
    }
    is SchemaType.IntSchema -> {
        // passing double number to an integer schema
        if (toString().contains(".")) {
            throw OpenFeatureError.ParseError("Incompatible value \"$key\" for schema")
        }
        toString().toIntOrNull()?.let(Value::Integer) ?: Value.Null
    }
    is SchemaType.SchemaStruct -> {
        if (jsonObject.isEmpty()) {
            Value.Null
        } else {
            val serializedMap = Json.decodeFromString(
                FlagValueSerializer(schemaType),
                jsonObject.toString()
            ).asMap()

            Value.Structure(serializedMap)
        }
    }
    is SchemaType.StringSchema -> if (!jsonPrimitive.isString) {
        Value.Null
    } else {
        Value.String(toString().replace("\"", ""))
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