package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.apply.FlagApplier
import dev.openfeature.contrib.providers.apply.FlagApplierWithRetries
import dev.openfeature.contrib.providers.cache.ProviderCache
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.ConfidenceRegion
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolveResponse
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.ProviderMetadata
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.events.EventHandler
import dev.openfeature.sdk.events.EventsPublisher
import dev.openfeature.sdk.events.OpenFeatureEvents
import dev.openfeature.sdk.exceptions.OpenFeatureError.FlagNotFoundError
import dev.openfeature.sdk.exceptions.OpenFeatureError.InvalidContextError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Suppress(
    "TooManyFunctions",
    "LongParameterList"
)
class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>>,
    override val metadata: ProviderMetadata,
    private val cache: ProviderCache,
    private val client: ConfidenceClient,
    private val flagApplier: FlagApplier,
    private val eventsPublisher: EventsPublisher,
    dispatcher: CoroutineDispatcher
) : FeatureProvider {
    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val networkExceptionHandler by lazy {
        CoroutineExceptionHandler { _, _ ->
            // network failed, provider is ready but with default/cache values
            eventsPublisher.publish(OpenFeatureEvents.ProviderReady)
        }
    }
    override fun initialize(initialContext: EvaluationContext?) {
        if (initialContext == null) return
        coroutineScope.launch(networkExceptionHandler) {
            val resolveResponse = client.resolve(listOf(), initialContext)
            if (resolveResponse is ResolveResponse.Resolved) {
                val (flags, resolveToken) = resolveResponse.flags
                cache.refresh(flags.list, resolveToken, initialContext)
                eventsPublisher.publish(OpenFeatureEvents.ProviderReady)
            }
        }
    }

    override fun shutdown() {
        coroutineScope.cancel()
    }

    override fun onContextSet(
        oldContext: EvaluationContext?,
        newContext: EvaluationContext
    ) {
        if (newContext != oldContext) {
            initialize(newContext)
        }
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
                reason = Reason.STALE.toString()
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

        @Suppress("LongParameterList")
        fun create(
            context: Context,
            clientSecret: String,
            region: ConfidenceRegion = ConfidenceRegion.EUROPE,
            hooks: List<Hook<*>> = listOf(),
            client: ConfidenceClient? = null,
            metadata: ProviderMetadata = ConfidenceMetadata(),
            cache: ProviderCache? = null,
            flagApplier: FlagApplier? = null,
            eventsPublisher: EventsPublisher = EventHandler.eventsPublisher(Dispatchers.IO),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): ConfidenceFeatureProvider {
            val configuredClient = client ?: ConfidenceRemoteClient(
                clientSecret = clientSecret,
                region = region,
                dispatcher = dispatcher
            )
            val flagApplierWithRetries = flagApplier ?: FlagApplierWithRetries(
                client = configuredClient,
                dispatcher = dispatcher,
                context = context
            )

            return ConfidenceFeatureProvider(
                hooks = hooks,
                metadata = metadata,
                cache = cache ?: StorageFileCache.create(context),
                client = configuredClient,
                flagApplier = flagApplierWithRetries,
                eventsPublisher,
                dispatcher
            )
        }
    }

    private fun <T> ProviderCache.CacheResolveEntry.toProviderEvaluation(
        parsedKey: FlagKey,
        defaultValue: T
    ): ProviderEvaluation<T> = when (resolveReason) {
        ResolveReason.RESOLVE_REASON_MATCH -> {
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
        else -> {
            flagApplier.apply(parsedKey.flagName, resolveToken)
            ProviderEvaluation(
                value = defaultValue,
                reason = Reason.DEFAULT.toString()
            )
        }
    }
}