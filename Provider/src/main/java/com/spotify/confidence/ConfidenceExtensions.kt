package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.ResolveResponse
import dev.openfeature.sdk.EvaluationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface ConfidenceContextProvider {
    fun confidenceContext(): ConfidenceValue.Struct
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface ConfidenceAPI : ConfidenceContextProvider {
    fun fork(context: ConfidenceContext): ConfidenceAPI
    fun putContext(context: ConfidenceContext)
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
    override fun confidenceContext(): ConfidenceValue.Struct = ConfidenceValue.Struct(mapOf())
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

class Confidence(
    val clientSecret: String,
    private val root: ConfidenceContextProvider = CommonContext()
) : ConfidenceAPI {
    private var contextMap: ConfidenceValue.Struct = ConfidenceValue.Struct(mapOf())
    override fun putContext(context: ConfidenceContext) {
        val map = contextMap.value.toMutableMap()
        map[context.name] = context.value
        contextMap = ConfidenceValue.Struct(map)
    }

    override fun confidenceContext(): ConfidenceValue.Struct {
        return ConfidenceValue.Struct(root.confidenceContext().value + contextMap.value)
    }

    override fun fork(context: ConfidenceContext) = Confidence(clientSecret, this).also {
        it.putContext(context)
    }
}

internal fun ConfidenceAPI.resolve(flags: List<String>): ResolveResponse {
    TODO()
}

fun Confidence.openFeatureProvider(
    context: Context,
    initialisationStrategy: InitialisationStrategy
): ConfidenceFeatureProvider = ConfidenceFeatureProvider.create(
    context,
    confidenceAPI = this,
    clientSecret = clientSecret,
    initialisationStrategy = initialisationStrategy
)

fun Confidence.eventSender(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): EventSender = EventSenderImpl.create(
    clientSecret = clientSecret,
    dispatcher = dispatcher,
    flushPolicies = listOf(confidenceFlushPolicy),
    context = context,
    confidenceContext = this
).onLowMemory { files ->
    val sortedFiles = files.sortedBy { it.lastModified() }
    sortedFiles.take(10).forEach { it.delete() }
}

val confidenceFlushPolicy = object : FlushPolicy {
    private var size = 0
    override fun reset() {
        size = 0
    }

    override fun hit(event: Event) {
        size++
    }

    override fun shouldFlush(): Boolean {
        return size > 4
    }
}

sealed class ConfidenceValue {
    data class String(val value: kotlin.String) : ConfidenceValue()
    data class Double(val value: kotlin.Double) : ConfidenceValue()
    data class Boolean(val value: kotlin.Boolean) : ConfidenceValue()
    data class Int(val value: kotlin.Int) : ConfidenceValue()
    data class Struct(val value: Map<kotlin.String, ConfidenceValue>) : ConfidenceValue()
}