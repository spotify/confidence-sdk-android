package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.DiskStorage
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ResolveReason
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.ProviderMetadata
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import dev.openfeature.sdk.exceptions.OpenFeatureError.InvalidContextError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

const val SDK_ID = "SDK_ID_KOTLIN_PROVIDER"

@Suppress(
    "TooManyFunctions",
    "LongParameterList"
)
class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>>,
    override val metadata: ProviderMetadata,
    private val storage: DiskStorage,
    private val providerCache: ProviderCache,
    private val initialisationStrategy: InitialisationStrategy,
    private val eventHandler: EventHandler,
    private val confidence: Confidence,
    dispatcher: CoroutineDispatcher
) : FeatureProvider {
    private val job = SupervisorJob()

    private val coroutineScope = CoroutineScope(job + dispatcher)
    private val networkExceptionHandler by lazy {
        CoroutineExceptionHandler { _, _ ->
            // network failed, provider is ready but with default/cache values
            eventHandler.publish(OpenFeatureEvents.ProviderReady)
        }
    }

    override fun initialize(initialContext: EvaluationContext?) {
        initialContext?.let {
            internalInitialize(
                initialContext,
                initialisationStrategy
            )
        }
    }

    private fun internalInitialize(
        initialContext: EvaluationContext,
        strategy: InitialisationStrategy
    ) {
        // refresh cache with the last stored data
        storage.read().let(providerCache::refresh)
        if (strategy == InitialisationStrategy.ActivateAndFetchAsync) {
            eventHandler.publish(OpenFeatureEvents.ProviderReady)
        }

        coroutineScope.launch(networkExceptionHandler) {
            confidence.putContext("open_feature", initialContext.toConfidenceContext())
            val resolveResponse = confidence.resolve(listOf())
            if (resolveResponse is Result.Success) {
                // we store the flag anyways
                storage.store(resolveResponse.data)

                when (strategy) {
                    InitialisationStrategy.FetchAndActivate -> {
                        // refresh the cache from the stored data
                        providerCache.refresh(resolveResponse.data)
                        eventHandler.publish(OpenFeatureEvents.ProviderReady)
                    }

                    InitialisationStrategy.ActivateAndFetchAsync -> {
                        // do nothing
                    }
                }
            } else {
                eventHandler.publish(OpenFeatureEvents.ProviderReady)
            }
        }
    }

    override fun shutdown() {
        job.cancelChildren()
    }

    override fun onContextSet(
        oldContext: EvaluationContext?,
        newContext: EvaluationContext
    ) {
        if (newContext != oldContext) {
            // on the new context we want to fetch new values and update
            // the storage & cache right away which is why we pass `InitialisationStrategy.FetchAndActivate`
            internalInitialize(
                newContext,
                InitialisationStrategy.FetchAndActivate
            )
        }
    }

    override fun observe(): Flow<OpenFeatureEvents> = eventHandler.observe()

    override fun getProviderStatus(): OpenFeatureEvents {
        return eventHandler.getProviderStatus()
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        return generateEvaluation(key, defaultValue, context)
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        return generateEvaluation(key, defaultValue, context)
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        return generateEvaluation(key, defaultValue, context)
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        val evaluation = generateEvaluation(key, defaultValue.toConfidenceValue(), context)
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
        return generateEvaluation(key, defaultValue, context)
    }

    private fun <T> generateEvaluation(
        key: String,
        defaultValue: T,
        context: EvaluationContext?
    ): ProviderEvaluation<T> {
        context ?: throw InvalidContextError()
        return providerCache.get().getEvaluation(
            key,
            defaultValue,
            confidence.getContext().openFeatureFlatten()
        ) { flagName, resolveToken ->
            confidence.apply(flagName, resolveToken)
        }.toProviderEvaluation()
    }
    companion object {
        private class ConfidenceMetadata(override var name: String? = "confidence") : ProviderMetadata

        fun isStorageEmpty(
            context: Context
        ): Boolean {
            val storage = FileDiskStorage.create(context)
            val data = storage.read()
            return data == null
        }

        @Suppress("LongParameterList")
        fun create(
            confidence: Confidence,
            context: Context,
            initialisationStrategy: InitialisationStrategy = InitialisationStrategy.FetchAndActivate,
            hooks: List<Hook<*>> = listOf(),
            metadata: ProviderMetadata = ConfidenceMetadata(),
            storage: DiskStorage? = null,
            cache: ProviderCache = InMemoryCache(),
            eventHandler: EventHandler = EventHandler(Dispatchers.IO),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): ConfidenceFeatureProvider {
            val diskStorage = storage ?: FileDiskStorage.create(context)
            return ConfidenceFeatureProvider(
                hooks = hooks,
                metadata = metadata,
                storage = diskStorage,
                initialisationStrategy = initialisationStrategy,
                providerCache = cache,
                eventHandler = eventHandler,
                confidence = confidence,
                dispatcher = dispatcher
            )
        }
    }
}

internal fun Value.toConfidenceValue(): ConfidenceValue = when (this) {
    is Value.Structure -> ConfidenceValue.Struct(structure.mapValues { it.value.toConfidenceValue() })
    is Value.Boolean -> ConfidenceValue.Boolean(this.boolean)
    is Value.Date -> ConfidenceValue.Date(this.date)
    is Value.Double -> ConfidenceValue.Double(this.double)
    is Value.Integer -> ConfidenceValue.Integer(this.integer)
    is Value.List -> ConfidenceValue.List(this.list.map { it.toConfidenceValue() })
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