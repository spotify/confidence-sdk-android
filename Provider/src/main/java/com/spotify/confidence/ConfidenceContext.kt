package com.spotify.confidence

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceContextProvider {
    fun getContext(): Map<String, ConfidenceValue>
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface Contextual : ConfidenceContextProvider {
    fun withContext(context: Map<String, ConfidenceValue>): Contextual

    fun putContext(context: Map<String, ConfidenceValue>)
    fun setContext(context: Map<String, ConfidenceValue>)
    fun putContext(key: String, value: ConfidenceValue)
    fun removeContext(key: String)
}

class CommonContext : ConfidenceContextProvider {
    override fun getContext(): Map<String, ConfidenceValue> = mapOf()
}

fun EvaluationContext.toConfidenceContext(): ConfidenceValue.Struct {
    val map = mutableMapOf<String, ConfidenceValue>()
    map["targeting_key"] = ConfidenceValue.String(getTargetingKey())
    map.putAll(asMap().mapValues { it.value.toConfidenceValue() })
    return ConfidenceValue.Struct(map)
}