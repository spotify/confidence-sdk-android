
package com.spotify.confidence.openfeature

import com.spotify.confidence.Confidence
import com.spotify.confidence.ConfidenceError.ErrorCode
import com.spotify.confidence.ConfidenceError.FlagNotFoundError
import com.spotify.confidence.ConfidenceError.ParseError
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.Evaluation
import com.spotify.confidence.ResolveReason
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.ProviderMetadata
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.TrackingEventDetails
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.OpenFeatureError

internal const val PROVIDER_ID = "SDK_ID_KOTLIN_CONFIDENCE"

@Suppress(
    "TooManyFunctions",
    "LongParameterList"
)
class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>>,
    override val metadata: ProviderMetadata,
    private val initialisationStrategy: InitialisationStrategy,
    private val confidence: Confidence
) : FeatureProvider {

    override suspend fun initialize(initialContext: EvaluationContext?) {
        initialContext?.toConfidenceContext()?.let {
            confidence.putContextLocal(it.map)
        }

        when (initialisationStrategy) {
            InitialisationStrategy.ActivateAndFetchAsync -> {
                confidence.activate()
                confidence.asyncFetch()
            }
            InitialisationStrategy.FetchAndActivate -> {
                confidence.fetchAndActivate()
            }
        }
    }

    override fun shutdown() {
    }

    override suspend fun onContextSet(
        oldContext: EvaluationContext?,
        newContext: EvaluationContext
    ) {
        val context = newContext.toConfidenceContext()
        val removedKeys = oldContext?.asMap()?.keys?.minus(newContext.asMap().keys) ?: emptySet()
        confidence.putContext(context.map, removedKeys.toList())
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        val evaluation = generateEvaluation(key, defaultValue.toConfidenceValue())
        return ProviderEvaluation(
            value = evaluation.value.toValue(),
            reason = evaluation.reason,
            variant = evaluation.variant,
            errorCode = evaluation.errorCode,
            errorMessage = evaluation.errorMessage
        )
    }

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: EvaluationContext?
    ): ProviderEvaluation<String> {
        return generateEvaluation(key, defaultValue)
    }

    override fun track(trackingEventName: String, context: EvaluationContext?, details: TrackingEventDetails?) {
        confidence.track(trackingEventName, details?.toConfidenceValue() ?: emptyMap())
    }

    private fun <T> generateEvaluation(
        key: String,
        defaultValue: T
    ): ProviderEvaluation<T> {
        try {
            return confidence.getFlag(key, defaultValue).toProviderEvaluation()
        } catch (e: ParseError) {
            throw OpenFeatureError.ParseError(e.message)
        } catch (e: FlagNotFoundError) {
            throw OpenFeatureError.FlagNotFoundError(e.flag)
        }
    }
    companion object {
        private class ConfidenceMetadata(override var name: String? = PROVIDER_ID) : ProviderMetadata

        @Suppress("LongParameterList")
        fun create(
            confidence: Confidence,
            initialisationStrategy: InitialisationStrategy = InitialisationStrategy.FetchAndActivate,
            hooks: List<Hook<*>> = listOf(),
            metadata: ProviderMetadata = ConfidenceMetadata()
        ): ConfidenceFeatureProvider {
            return ConfidenceFeatureProvider(
                hooks = hooks,
                metadata = metadata,
                initialisationStrategy = initialisationStrategy,
                confidence = confidence
            )
        }
    }
}

private fun TrackingEventDetails.toConfidenceValue(): Map<String, ConfidenceValue> = mapOf(
    "value" to (this.value?.toConfidenceValue() ?: ConfidenceValue.Null)
) + this.structure.asMap().mapValues { it.value.toConfidenceValue() }

private fun Number.toConfidenceValue(): ConfidenceValue = when (this) {
    is Int -> ConfidenceValue.Integer(this)
    is Double -> ConfidenceValue.Double(this)
    else -> ConfidenceValue.Null
}

internal fun Value.toConfidenceValue(): ConfidenceValue = when (this) {
    is Value.Structure -> ConfidenceValue.Struct(structure.mapValues { it.value.toConfidenceValue() })
    is Value.Boolean -> ConfidenceValue.Boolean(this.boolean)
    is Value.Date -> ConfidenceValue.Timestamp(this.date)
    is Value.Double -> ConfidenceValue.Double(this.double)
    is Value.Integer -> ConfidenceValue.Integer(this.integer)
    is Value.List -> {
        // if types are different, return an empty list
        if (this.list.map { it.javaClass.simpleName }.groupBy { it }.size > 1) {
            ConfidenceValue.List(listOf())
        } else {
            ConfidenceValue.List(this.list.map { it.toConfidenceValue() })
        }
    }
    Value.Null -> ConfidenceValue.Null
    is Value.String -> ConfidenceValue.String(this.string)
}

internal fun ConfidenceValue.toValue(): Value = when (this) {
    is ConfidenceValue.Boolean -> Value.Boolean(this.boolean)
    is ConfidenceValue.Date -> Value.Date(this.date)
    is ConfidenceValue.Double -> Value.Double(this.double)
    is ConfidenceValue.Integer -> Value.Integer(this.integer)
    is ConfidenceValue.List -> Value.List(this.list.map { it.toValue() })
    ConfidenceValue.Null -> Value.Null
    is ConfidenceValue.String -> Value.String(this.string)
    is ConfidenceValue.Struct -> Value.Structure(this.map.mapValues { it.value.toValue() })
    is ConfidenceValue.Timestamp -> Value.Date(this.dateTime)
}

private fun <T> Evaluation<T>.toProviderEvaluation() = ProviderEvaluation(
    reason = this.reason.toOFReason().name,
    errorCode = this.errorCode.toOFErrorCode(),
    errorMessage = this.errorMessage,
    value = this.value,
    variant = this.variant
)

private fun ResolveReason.toOFReason(): Reason = when (this) {
    ResolveReason.ERROR -> Reason.ERROR
    ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR -> Reason.ERROR
    ResolveReason.RESOLVE_REASON_UNSPECIFIED -> Reason.UNKNOWN
    ResolveReason.RESOLVE_REASON_MATCH -> Reason.TARGETING_MATCH
    ResolveReason.RESOLVE_REASON_STALE -> Reason.STALE
    else -> Reason.DEFAULT
}

private fun ErrorCode?.toOFErrorCode() = when (this) {
    null -> null
    ErrorCode.FLAG_NOT_FOUND -> dev.openfeature.sdk.exceptions.ErrorCode.FLAG_NOT_FOUND
    ErrorCode.INVALID_CONTEXT -> dev.openfeature.sdk.exceptions.ErrorCode.INVALID_CONTEXT
    else -> dev.openfeature.sdk.exceptions.ErrorCode.PROVIDER_NOT_READY
}

sealed interface InitialisationStrategy {
    object FetchAndActivate : InitialisationStrategy
    object ActivateAndFetchAsync : InitialisationStrategy
}

fun EvaluationContext.toConfidenceContext(): ConfidenceValue.Struct {
    val map = mutableMapOf<String, ConfidenceValue>()
    map["targeting_key"] = ConfidenceValue.String(getTargetingKey())
    map.putAll(asMap().mapValues { it.value.toConfidenceValue() })
    return ConfidenceValue.Struct(map)
}