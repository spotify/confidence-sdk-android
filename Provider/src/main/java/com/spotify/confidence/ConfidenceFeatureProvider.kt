package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.DiskStorage
import com.spotify.confidence.cache.FileDiskStorage
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.ProviderMetadata
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
    private val initialisationStrategy: InitialisationStrategy,
    private val eventHandler: EventHandler,
    private val confidenceAPI: Confidence,
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
        storage.read()?.let(confidenceAPI::refreshFlagResolution)

        if (strategy == InitialisationStrategy.ActivateAndFetchAsync) {
            eventHandler.publish(OpenFeatureEvents.ProviderReady)
        }

        coroutineScope.launch(networkExceptionHandler) {
            confidenceAPI.putContext("open_feature", initialContext.toConfidenceContext())
            val resolveResponse = confidenceAPI.resolveFlags(listOf())
            if (resolveResponse is Result.Success) {
                // we store the flag anyways
                storage.store(resolveResponse.data)

                when (strategy) {
                    InitialisationStrategy.FetchAndActivate -> {
                        // refresh the cache from the stored data
                        confidenceAPI.refreshFlagResolution(resolveResponse.data)
                        eventHandler.publish(OpenFeatureEvents.ProviderReady)
                    }

                    InitialisationStrategy.ActivateAndFetchAsync -> {
                        // do nothing
                    }
                }
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
        return generateEvaluation(key, defaultValue, context)
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
        return confidenceAPI.getEvaluation(
            key,
            defaultValue
        ).toProviderEvaluation()
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
            eventHandler: EventHandler = EventHandler(Dispatchers.IO),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): ConfidenceFeatureProvider {
            val diskStorage = storage ?: FileDiskStorage.create(context)
            return ConfidenceFeatureProvider(
                hooks = hooks,
                metadata = metadata,
                storage = diskStorage,
                initialisationStrategy = initialisationStrategy,
                eventHandler,
                confidence,
                dispatcher
            )
        }
    }
}

private fun <T> Evaluation<T>.toProviderEvaluation() = ProviderEvaluation(
    reason = this.reason.name,
    errorCode = null, // TODO convert
    errorMessage = this.errorMessage,
    value = this.value,
    variant = this.variant
)

sealed interface InitialisationStrategy {
    object FetchAndActivate : InitialisationStrategy
    object ActivateAndFetchAsync : InitialisationStrategy
}