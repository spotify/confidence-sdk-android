package com.spotify.confidence

import dev.openfeature.sdk.DateSerializer
import kotlinx.serialization.Serializable

sealed class ConfidenceValue {
    data class String(val string: kotlin.String) : ConfidenceValue()
    data class Double(val double: kotlin.Double) : ConfidenceValue()
    data class Boolean(val boolean: kotlin.Boolean) : ConfidenceValue()
    data class Integer(val integer: Int) : ConfidenceValue()
    data class Struct(val map: Map<kotlin.String, ConfidenceValue>) : ConfidenceValue()
    data class Date(@Serializable(DateSerializer::class) val date: java.util.Date) : ConfidenceValue()

    object Null : ConfidenceValue()
}