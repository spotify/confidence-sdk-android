package com.spotify.confidence

import com.spotify.confidence.client.serializers.ConfidenceValueSerializer
import com.spotify.confidence.client.serializers.DateTimeSerializer
import dev.openfeature.sdk.DateSerializer
import kotlinx.serialization.Serializable

@Serializable(ConfidenceValueSerializer::class)
sealed interface ConfidenceValue {
    @Serializable
    data class String(val string: kotlin.String) : ConfidenceValue

    @Serializable
    data class Double(val double: kotlin.Double) : ConfidenceValue

    @Serializable
    data class Boolean(val boolean: kotlin.Boolean) : ConfidenceValue

    @Serializable
    data class Integer(val integer: Int) : ConfidenceValue

    @Serializable
    data class Struct(val map: Map<kotlin.String, ConfidenceValue>) : ConfidenceValue

    @Serializable
    data class List(val list: kotlin.collections.List<ConfidenceValue>) : ConfidenceValue

    @Serializable
    data class Date(@Serializable(DateSerializer::class) val date: java.util.Date) : ConfidenceValue

    @Serializable
    data class Timestamp(@Serializable(DateTimeSerializer::class) val dateTime: java.util.Date) : ConfidenceValue

    @Serializable
    object Null : ConfidenceValue
}