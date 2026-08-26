@file:OptIn(ExperimentalTime::class)

package com.spotify.confidence.openfeature

import com.spotify.confidence.Confidence
import com.spotify.confidence.ConfidenceError.ErrorCode
import com.spotify.confidence.ConfidenceError.FlagNotFoundError
import com.spotify.confidence.ConfidenceError.HttpError
import com.spotify.confidence.ConfidenceError.ParseError
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.Evaluation
import com.spotify.confidence.ResolveReason
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Date
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
    private val providerEvents = MutableSharedFlow<OpenFeatureProviderEvents>(replay = 1)

    override suspend fun initialize(initialContext: EvaluationContext?) {
        try {
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
            providerEvents.emit(OpenFeatureProviderEvents.ProviderReady())
        } catch (e: OpenFeatureError) {
            providerEvents.emit(e.toProviderErrorEvent())
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            providerEvents.emit(e.toProviderErrorEvent())
            throw e
        }
    }

    override fun shutdown() {
        confidence.stop()
    }

    override fun observe(): Flow<OpenFeatureProviderEvents> = providerEvents.asSharedFlow()

    override suspend fun onContextSet(
        oldContext: EvaluationContext?,
        newContext: EvaluationContext
    ) {
        try {
            val context = newContext.toConfidenceContext()
            val removedKeys = oldContext?.asMap()?.keys?.minus(newContext.asMap().keys) ?: emptySet()
            when (val result = confidence.putContextAndWait(context.map, removedKeys.toList())) {
                is com.spotify.confidence.Result.Success -> {
                    // This should be ContextChanged once the Kotlin SDK exposes that event.
                    providerEvents.emit(OpenFeatureProviderEvents.ProviderReady())
                }
                is com.spotify.confidence.Result.Failure -> {
                    providerEvents.emit(result.error.toProviderStaleEvent())
                }
            }
        } catch (e: OpenFeatureError) {
            providerEvents.emit(e.toProviderErrorEvent())
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            providerEvents.emit(e.toProviderErrorEvent())
            throw e
        }
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
        val eventContext = mergeEventContext(
            sessionContext = confidence.getContext(),
            openFeatureContext = context.toTrackContextMap()
        )
        confidence.track(
            eventName = trackingEventName,
            data = details.toTrackingData(),
            eventContext = eventContext
        )
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
        } catch (e: HttpError) {
            throw OpenFeatureError.GeneralError(e.message)
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
            try {
                val method = confidence.javaClass.getDeclaredMethod("setTelemetryLibraryOpenFeature")
                method.isAccessible = true
                method.invoke(confidence)
            } catch (_: Exception) {
                // Best effort - telemetry will default to CONFIDENCE library
            }
            return ConfidenceFeatureProvider(
                hooks = hooks,
                metadata = metadata,
                initialisationStrategy = initialisationStrategy,
                confidence = confidence
            )
        }
    }
}

internal fun Value.toConfidenceValue(): ConfidenceValue = when (this) {
    is Value.Structure -> ConfidenceValue.Struct(structure.mapValues { it.value.toConfidenceValue() })
    is Value.Boolean -> ConfidenceValue.Boolean(this.boolean)
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
    is Value.Instant -> ConfidenceValue.Timestamp(
        Date(this.instant.epochSeconds * 1000 + this.instant.nanosecondsOfSecond / 1_000_000)
    )
}

internal fun ConfidenceValue.toValue(): Value = when (this) {
    is ConfidenceValue.Boolean -> Value.Boolean(this.boolean)
    is ConfidenceValue.Double -> Value.Double(this.double)
    is ConfidenceValue.Integer -> Value.Integer(this.integer)
    is ConfidenceValue.List -> Value.List(this.list.map { it.toValue() })
    ConfidenceValue.Null -> Value.Null
    is ConfidenceValue.String -> Value.String(this.string)
    is ConfidenceValue.Struct -> Value.Structure(this.map.mapValues { it.value.toValue() })
    is ConfidenceValue.Timestamp -> Value.Instant(this.dateTime.toKotlinInstant())
    is ConfidenceValue.Date -> Value.Instant(this.date.toKotlinInstant())
}

private fun Date.toKotlinInstant(): Instant {
    val epochSeconds = time / 1000

    return Instant.fromEpochSeconds(
        epochSeconds = epochSeconds
    )
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
    ErrorCode.FLAG_NOT_FOUND -> dev.openfeature.kotlin.sdk.exceptions.ErrorCode.FLAG_NOT_FOUND
    ErrorCode.INVALID_CONTEXT -> dev.openfeature.kotlin.sdk.exceptions.ErrorCode.INVALID_CONTEXT
    else -> dev.openfeature.kotlin.sdk.exceptions.ErrorCode.PROVIDER_NOT_READY
}

private fun OpenFeatureError.toProviderErrorEvent(): OpenFeatureProviderEvents.ProviderError {
    return OpenFeatureProviderEvents.ProviderError(
        eventDetails = OpenFeatureProviderEvents.EventDetails(
            message = message,
            errorCode = errorCode()
        ),
        error = this
    )
}

private fun Exception.toProviderErrorEvent(): OpenFeatureProviderEvents.ProviderError {
    val error = OpenFeatureError.GeneralError(message ?: "Unknown error")
    return error.toProviderErrorEvent()
}

private fun Throwable.toProviderStaleEvent(): OpenFeatureProviderEvents.ProviderStale {
    return OpenFeatureProviderEvents.ProviderStale(
        eventDetails = OpenFeatureProviderEvents.EventDetails(
            message = message
        )
    )
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
