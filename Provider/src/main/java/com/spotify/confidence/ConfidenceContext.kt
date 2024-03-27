package com.spotify.confidence

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceContextProvider {
    fun getContext(): Map<String, ConfidenceValue>
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface ContextApi : ConfidenceContextProvider {
    fun withContext(context: Map<String, ConfidenceValue>): ContextApi

    fun putContext(context: Map<String, ConfidenceValue>)
    fun setContext(context: Map<String, ConfidenceValue>)
    fun putContext(key: String, value: ConfidenceValue)
    fun removeContext(key: String)
}

interface ConfidenceContext {
    val name: String
    val value: ConfidenceValue
}

class CommonContext : ConfidenceContextProvider {
    override fun getContext(): Map<String, ConfidenceValue> = mapOf()
}

fun EvaluationContext.toConfidenceContext(): ConfidenceValue.Struct {
    TODO()
}