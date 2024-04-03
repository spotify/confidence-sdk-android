package com.spotify.confidence

import com.spotify.confidence.client.serializers.ConfidenceValueSerializer
import dev.openfeature.sdk.DateSerializer
import kotlinx.serialization.Serializable

@Serializable(ConfidenceValueSerializer::class)
sealed class ConfidenceValue {
    data class String(val string: kotlin.String) : ConfidenceValue()
    data class Double(val double: kotlin.Double) : ConfidenceValue()
    data class Boolean(val boolean: kotlin.Boolean) : ConfidenceValue()
    data class Integer(val integer: Int) : ConfidenceValue()
    data class Struct(val map: Map<kotlin.String, ConfidenceValue>) : ConfidenceValue()
    data class List(val list: kotlin.collections.List<ConfidenceValue>) : ConfidenceValue()
    data class Date(@Serializable(DateSerializer::class) val date: java.util.Date) : ConfidenceValue()

    object Null : ConfidenceValue()
}