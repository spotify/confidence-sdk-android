package com.spotify.confidence

sealed class ConfidenceValue {
    data class String(val value: kotlin.String) : ConfidenceValue()
    data class Double(val value: kotlin.Double) : ConfidenceValue()
    data class Boolean(val value: kotlin.Boolean) : ConfidenceValue()
    data class Int(val value: kotlin.Int) : ConfidenceValue()
    data class Struct(val value: Map<kotlin.String, ConfidenceValue>) : ConfidenceValue()
}