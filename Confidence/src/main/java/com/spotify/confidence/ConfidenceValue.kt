package com.spotify.confidence

import com.spotify.confidence.serializers.ConfidenceValueSerializer
import com.spotify.confidence.serializers.DateSerializer
import com.spotify.confidence.serializers.DateTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable(ConfidenceValueSerializer::class)
sealed interface ConfidenceValue {
    @Serializable
    data class String(val string: kotlin.String) : ConfidenceValue {
        override fun toString() = string
    }

    @Serializable
    data class Double(val double: kotlin.Double) : ConfidenceValue {
        override fun toString() = double.toString()
    }

    @Serializable
    data class Boolean(val boolean: kotlin.Boolean) : ConfidenceValue {
        override fun toString() = boolean.toString()
    }

    @Serializable
    data class Integer(val integer: Int) : ConfidenceValue {
        override fun toString() = integer.toString()
    }

    @Serializable
    data class Struct(val map: Map<kotlin.String, ConfidenceValue>) : ConfidenceValue {
        override fun toString() = map.toString()
    }

    @Serializable
    data class List(val list: kotlin.collections.List<ConfidenceValue>) :
        ConfidenceValue {
        override fun toString() = list.toString()
    }

    @Serializable
    data class Date(@Serializable(DateSerializer::class) val date: java.util.Date) : ConfidenceValue {
        override fun toString() = date.toString()
    }

    @Serializable
    data class Timestamp(@Serializable(DateTimeSerializer::class) val dateTime: java.util.Date) :
        ConfidenceValue {
        override fun toString() = dateTime.toString()
    }

    @Serializable
    object Null : ConfidenceValue {
        override fun toString() = "null"
    }

    companion object {
        fun stringList(list: kotlin.collections.List<kotlin.String>) =
            List(list.map(ConfidenceValue::String))
        fun doubleList(list: kotlin.collections.List<kotlin.Double>) =
            List(list.map(ConfidenceValue::Double))
        fun booleanList(list: kotlin.collections.List<kotlin.Boolean>) =
            List(list.map(ConfidenceValue::Boolean))
        fun integerList(list: kotlin.collections.List<kotlin.Int>) =
            List(list.map(ConfidenceValue::Integer))

        fun dateList(list: kotlin.collections.List<java.util.Date>) =
            List(list.map(ConfidenceValue::Date))
        fun timestampList(list: kotlin.collections.List<java.util.Date>) =
            List(list.map(ConfidenceValue::Timestamp))
    }
}

fun ConfidenceValue.Struct.toNetworkJson() =
    Json.encodeToString(NetworkConfidenceValueSerializer, this)