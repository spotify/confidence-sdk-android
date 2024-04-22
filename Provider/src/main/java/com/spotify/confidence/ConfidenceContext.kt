package com.spotify.confidence

interface ConfidenceContextProvider {
    fun getContext(): Map<String, ConfidenceValue>
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface Contextual : ConfidenceContextProvider {
    fun withContext(context: Map<String, ConfidenceValue>): Contextual

    fun putContext(context: Map<String, ConfidenceValue>)
    fun putContext(key: String, value: ConfidenceValue)
    fun removeContext(key: String)
}