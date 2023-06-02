package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.apply.FlagApplier
import dev.openfeature.contrib.providers.apply.FlagApplierWithRetries
import dev.openfeature.contrib.providers.cache.ProviderCache
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient.ConfidenceRegion
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient.ConfidenceRegion.EUROPE
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.Metadata
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.OpenFeatureError.FlagNotFoundError
import dev.openfeature.sdk.exceptions.OpenFeatureError.InvalidContextError
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>>,
    override val metadata: Metadata,
    private val cache: ProviderCache,
    private val client: ConfidenceClient,
    private val flagApplier: FlagApplier
) : FeatureProvider {
    data class Builder(
        val context: Context,
        val clientSecret: String
    ) {
        private var region: ConfidenceRegion? = null
        private var hooks: List<Hook<*>>? = null
        private var metadata: Metadata? = null
        private var client: ConfidenceClient? = null
        private var cache: ProviderCache? = null
        private var dispatcher: CoroutineDispatcher = Dispatchers.IO
        fun hooks(hooks: List<Hook<*>>) = apply { this.hooks = hooks }
        fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

        /**
         * Default is "EU". This value is ignored if an external client is set via this builder
         */
        fun region(region: ConfidenceRegion) = apply { this.region = region }

        /**
         * CoroutineDispatcher to use for file I/O and network traffic, if not set it will use
         * Dispatcher.IO
         */
        fun dispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.dispatcher = dispatcher
        }

        /**
         * Used for testing.
         */
        fun client(client: ConfidenceClient) = apply { this.client = client }

        /**
         * Used for testing.
         */
        fun cache(cache: ProviderCache) = apply { this.cache = cache }

        fun build(): ConfidenceFeatureProvider {
            val configuredRegion = region ?: EUROPE
            val configuredClient = client ?: ConfidenceRemoteClient(
                clientSecret = clientSecret,
                region = configuredRegion,
                dispatcher = dispatcher
            )
            return ConfidenceFeatureProvider(
                hooks ?: listOf(),
                metadata ?: ConfidenceMetadata(),
                cache ?: StorageFileCache(context),
                configuredClient,
                FlagApplierWithRetries(
                    configuredClient,
                    dispatcher,
                    context
                )
            )
        }
    }

    override suspend fun initialize(initialContext: EvaluationContext?) {
        if (initialContext == null) return
        try {
            val (resolvedFlags, resolveToken) = client.resolve(listOf(), initialContext)
            cache.refresh(resolvedFlags, resolveToken, initialContext)
        } catch (_: Throwable) {
            // Can't refresh cache at this time. Do we retry at a later time?
        }
    }

    override suspend fun onContextSet(
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
                val resolvedFlag = resolve.entry
                when (resolvedFlag.resolveReason) {
                    ResolveReason.RESOLVE_REASON_MATCH -> {
                        val resolvedValue: Value = findValueFromValuePath(resolvedFlag.value, parsedKey.valuePath)
                            ?: throw ParseError(
                                "Unable to parse flag value: ${parsedKey.valuePath.joinToString(separator = "/")}"
                            )
                        val value = getTyped<T>(resolvedValue) ?: defaultValue
                        flagApplier.apply(parsedKey.flagName, resolvedFlag.resolveToken)
                        ProviderEvaluation(
                            value = value,
                            variant = resolvedFlag.variant,
                            reason = Reason.TARGETING_MATCH.toString()
                        )
                    }
                    else -> {
                        flagApplier.apply(parsedKey.flagName, resolvedFlag.resolveToken)
                        ProviderEvaluation(
                            value = defaultValue,
                            reason = Reason.DEFAULT.toString()
                        )
                    }
                }
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
            is Value.Instant -> v.instant.toString() as T
            is Value.Integer -> v.integer as T
            is Value.List -> v as T
            is Value.String -> v.string as T
            is Value.Structure -> v as T
            Value.Null -> null
        }
    }

    private fun findValueFromValuePath(value: Value.Structure, valuePath: List<String>): Value? {
        if (valuePath.isEmpty()) return value
        return when (val currValue = value.structure[valuePath[0]]) {
            is Value.Structure -> findValueFromValuePath(currValue, valuePath.subList(1, valuePath.count()))
            else -> {
                if (valuePath.count() == 1) {
                    return currValue
                } else {
                    return null
                }
            }
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

    class ConfidenceMetadata(override var name: String? = "confidence") : Metadata
}