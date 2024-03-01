package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.apply.FlagApplier
import com.spotify.confidence.apply.FlagApplierWithRetries
import com.spotify.confidence.cache.DiskStorage
import com.spotify.confidence.cache.InMemoryCache
import com.spotify.confidence.cache.ProviderCache
import com.spotify.confidence.cache.ProviderCache.CacheResolveResult
import com.spotify.confidence.cache.StorageFileCache
import com.spotify.confidence.client.ConfidenceClient
import com.spotify.confidence.client.ConfidenceRegion
import com.spotify.confidence.client.ConfidenceRemoteClient
import com.spotify.confidence.client.ResolveResponse
import com.spotify.confidence.client.SdkMetadata
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.ProviderMetadata
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.OpenFeatureEvents
import dev.openfeature.sdk.exceptions.ErrorCode
import dev.openfeature.sdk.exceptions.OpenFeatureError.FlagNotFoundError
import dev.openfeature.sdk.exceptions.OpenFeatureError.InvalidContextError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
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
    private val cache: ProviderCache,
    private val storage: DiskStorage,
    private val initialisationStrategy: InitialisationStrategy,
    private val client: ConfidenceClient,
    private val flagApplier: FlagApplier,
    private val eventHandler: EventHandler,
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

    fun clientSecret(): String {
        return client.clientSecret()
    }

    private fun internalInitialize(
        initialContext: EvaluationContext,
        strategy: InitialisationStrategy
    ) {
        // refresh cache with the last stored data
        storage.read()?.let(cache::refresh)

        if (strategy == InitialisationStrategy.ActivateAndFetchAsync) {
            eventHandler.publish(OpenFeatureEvents.ProviderReady)
        }

        coroutineScope.launch(networkExceptionHandler) {
            val resolveResponse = client.resolve(listOf(), initialContext)
            if (resolveResponse is ResolveResponse.Resolved) {
                val (flags, resolveToken) = resolveResponse.flags

                // update the cache and emit the ready signal when the strategy is expected
                // to wait for the network response

                // we store the flag anyways
                val storedData = storage.store(
                    flags.list,
                    resolveToken,
                    initialContext
                )

                when (strategy) {
                    InitialisationStrategy.FetchAndActivate -> {
                        // refresh the cache from the stored data
                        cache.refresh(cacheData = storedData)
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
        val parsedKey = FlagKey(key)
        val evaluationContext = context ?: throw InvalidContextError()
        return when (val resolve: CacheResolveResult = cache.resolve(parsedKey.flagName, evaluationContext)) {
            is CacheResolveResult.Found -> {
                resolve.entry.toProviderEvaluation(
                    parsedKey,
                    defaultValue
                )
            }
            CacheResolveResult.Stale -> ProviderEvaluation(
                value = defaultValue,
                reason = Reason.ERROR.toString(),
                errorCode = ErrorCode.PROVIDER_NOT_READY
            )
            CacheResolveResult.NotFound -> throw FlagNotFoundError(parsedKey.flagName)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getTyped(v: Value): T? {
        return when (v) {
            is Value.Boolean -> v.boolean as T
            is Value.Double -> v.double as T
            is Value.Date -> v.date as T
            is Value.Integer -> v.integer as T
            is Value.List -> v as T
            is Value.String -> v.string as T
            is Value.Structure -> v as T
            Value.Null -> null
        }
    }

    private fun findValueFromValuePath(value: Value.Structure, valuePath: List<String>): Value? {
        if (valuePath.isEmpty()) return value
        val currValue = value.structure[valuePath[0]]
        return when {
            currValue is Value.Structure -> {
                findValueFromValuePath(currValue, valuePath.subList(1, valuePath.count()))
            }
            valuePath.count() == 1 -> currValue
            else -> null
        }
    }

    private data class FlagKey(
        val flagName: String,
        val valuePath: List<String>
    ) {
        companion object {
            operator fun invoke(evalKey: String): FlagKey {
                val splits = evalKey.split(".")
                return FlagKey(
                    splits.getOrNull(0)!!,
                    splits.subList(1, splits.count())
                )
            }
        }
    }
    companion object {
        private class ConfidenceMetadata(override var name: String? = "confidence") : ProviderMetadata

        fun isStorageEmpty(
            context: Context
        ): Boolean {
            val storage = StorageFileCache.create(context)
            val data = storage.read()
            return data == null
        }

        @Suppress("LongParameterList")
        fun create(
            context: Context,
            clientSecret: String,
            region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
            initialisationStrategy: InitialisationStrategy = InitialisationStrategy.FetchAndActivate,
            hooks: List<Hook<*>> = listOf(),
            client: ConfidenceClient? = null,
            metadata: ProviderMetadata = ConfidenceMetadata(),
            cache: ProviderCache? = null,
            storage: DiskStorage? = null,
            flagApplier: FlagApplier? = null,
            eventHandler: EventHandler = EventHandler(Dispatchers.IO),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): ConfidenceFeatureProvider {
            val configuredClient = client ?: ConfidenceRemoteClient(
                clientSecret = clientSecret,
                sdkMetadata = SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION),
                region = region,
                dispatcher = dispatcher
            )
            val diskStorage = storage ?: StorageFileCache.create(context)
            val flagApplierWithRetries = flagApplier ?: FlagApplierWithRetries(
                client = configuredClient,
                dispatcher = dispatcher,
                diskStorage = diskStorage
            )

            return ConfidenceFeatureProvider(
                hooks = hooks,
                metadata = metadata,
                cache = cache ?: InMemoryCache(),
                storage = diskStorage,
                initialisationStrategy = initialisationStrategy,
                client = configuredClient,
                flagApplier = flagApplierWithRetries,
                eventHandler,
                dispatcher
            )
        }
    }

    private fun <T> ProviderCache.CacheResolveEntry.toProviderEvaluation(
        parsedKey: FlagKey,
        defaultValue: T
    ): ProviderEvaluation<T> = when (resolveReason) {
        com.spotify.confidence.client.ResolveReason.RESOLVE_REASON_MATCH -> {
            val resolvedValue: Value = findValueFromValuePath(value, parsedKey.valuePath)
                ?: throw ParseError(
                    "Unable to parse flag value: ${parsedKey.valuePath.joinToString(separator = "/")}"
                )
            val value = getTyped<T>(resolvedValue) ?: defaultValue
            flagApplier.apply(parsedKey.flagName, resolveToken)
            ProviderEvaluation(
                value = value,
                variant = variant,
                reason = Reason.TARGETING_MATCH.toString()
            )
        }
        com.spotify.confidence.client.ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR -> {
            ProviderEvaluation(
                value = defaultValue,
                reason = Reason.ERROR.toString(),
                errorCode = ErrorCode.INVALID_CONTEXT,
                errorMessage = "Invalid targeting key"
            )
        }
        else -> {
            flagApplier.apply(parsedKey.flagName, resolveToken)
            ProviderEvaluation(
                value = defaultValue,
                reason = Reason.DEFAULT.toString()
            )
        }
    }
}

sealed interface InitialisationStrategy {
    object FetchAndActivate : InitialisationStrategy
    object ActivateAndFetchAsync : InitialisationStrategy
}