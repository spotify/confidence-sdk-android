package com.spotify.confidence

import dev.openfeature.sdk.EvaluationContext

interface ConfidenceContextProvider {
    fun getContext(): Map<String, ConfidenceValue>
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface Contextual : ConfidenceContextProvider {
    fun withContext(context: ConfidenceContext): Contextual

    fun putContext(context: ConfidenceContext)
    fun setContext(context: Map<String, ConfidenceValue>)
    fun putContext(key: String, value: ConfidenceValue)
    fun removeContext(key: String)
}

interface ConfidenceContext {
    val name: String
    val value: ConfidenceValue
}

class PageContext(private val page: String) : ConfidenceContext {
    override val value: ConfidenceValue
        get() = ConfidenceValue.String(page)
    override val name: String
        get() = "page"
}

class CommonContext : ConfidenceContextProvider {
    override fun getContext(): Map<String, ConfidenceValue> = mapOf()
}

fun EvaluationContext.toConfidenceContext() = object : ConfidenceContext {
    override val name: String = "open_feature"
    override val value: ConfidenceValue
        get() = ConfidenceValue.Struct(
            asMap()
                .map { it.key to ConfidenceValue.String(it.value.toString()) }
                .toMap() + ("targeting_key" to ConfidenceValue.String(getTargetingKey()))
        )
}