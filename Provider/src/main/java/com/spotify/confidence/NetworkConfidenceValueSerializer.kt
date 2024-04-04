package com.spotify.confidence

import com.spotify.confidence.client.serializers.DateSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure

/***
 * the struct serializer needed for sending the resolve request
 */
private object NetworkStructSerializer : KSerializer<ConfidenceValue.Struct> {
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): ConfidenceValue.Struct {
        error("no deserializer is needed")
    }

    override fun serialize(encoder: Encoder, value: ConfidenceValue.Struct) {
        encoder.encodeStructure(descriptor) {
            for ((key, mapValue) in value.map) {
                encodeStringElement(descriptor, 0, key)
                encodeSerializableElement(descriptor, 1, NetworkConfidenceValueSerializer, mapValue)
            }
        }
    }
}

internal object NetworkConfidenceValueSerializer : KSerializer<ConfidenceValue> {
    override val descriptor: SerialDescriptor
        get() = MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): ConfidenceValue {
        error("Not Implemented")
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
                NetworkStructSerializer,
                ConfidenceValue.Struct(value.map)
            )

            is ConfidenceValue.List -> encoder.encodeSerializableValue(
                ListSerializer(NetworkConfidenceValueSerializer),
                value.list
            )

            is ConfidenceValue.Date -> encoder.encodeSerializableValue(
                DateSerializer,
                value.date
            )
        }
    }
}