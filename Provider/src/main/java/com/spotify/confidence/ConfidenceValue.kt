package com.spotify.confidence

import com.spotify.confidence.client.serializers.ConfidenceValueSerializer
import com.spotify.confidence.client.serializers.DateSerializer
import com.spotify.confidence.client.serializers.DateTimeSerializer
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
    data class List internal constructor(val list: kotlin.collections.List<ConfidenceValue>) : ConfidenceValue

    @Serializable
    data class Date(@Serializable(DateSerializer::class) val date: java.util.Date) : ConfidenceValue

    @Serializable
    data class Timestamp(@Serializable(DateTimeSerializer::class) val dateTime: java.util.Date) : ConfidenceValue

    @Serializable
    object Null : ConfidenceValue

    companion object {
        fun stringList(list: kotlin.collections.List<kotlin.String>) =
            List(list.map(::String))
        fun doubleList(list: kotlin.collections.List<kotlin.Double>) =
            List(list.map(::Double))
        fun booleanList(list: kotlin.collections.List<kotlin.Boolean>) =
            List(list.map(::Boolean))
        fun integerList(list: kotlin.collections.List<kotlin.Int>) =
            List(list.map(::Integer))

        fun dateList(list: kotlin.collections.List<java.util.Date>) =
            List(list.map(::Date))
        fun timestampList(list: kotlin.collections.List<java.util.Date>) =
            List(list.map(::Timestamp))
    }
}