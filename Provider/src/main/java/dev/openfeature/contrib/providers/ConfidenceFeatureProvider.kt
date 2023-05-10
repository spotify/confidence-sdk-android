package dev.openfeature.contrib.providers

import android.content.Context
import dev.openfeature.contrib.providers.cache.ProviderCache
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.*
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient.*
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient.ConfidenceRegion.*
import dev.openfeature.sdk.*
import dev.openfeature.sdk.exceptions.OpenFeatureError.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>>,
    override val metadata: Metadata,
    private val cache: ProviderCache,
    private val client: ConfidenceClient,
    private val applyDispatcher: CoroutineDispatcher
) : FeatureProvider {
    data class Builder(
        val context: Context,
        val clientSecret: String,
    ) {
        private var hooks: List<Hook<*>> = listOf()
        private var metadata: Metadata = ConfidenceMetadata()
        private var region: ConfidenceRegion = EUROPE
        private var client: ConfidenceClient? = null
        private var cache: ProviderCache? = null
        private var applyDispatcher: CoroutineDispatcher = Dispatchers.Default
        fun hooks(hooks: List<Hook<*>>) = apply { this.hooks = hooks }
        fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

        /**
         * Default is "EU". This value is ignored if an external client is set via this builder
         */
        fun region(region: ConfidenceRegion) = apply { this.region = region }

        /**
         * Used for testing. If set, the "clientSecret" parameter is not used
         */
        fun client(client: ConfidenceClient) = apply { this.client = client }

        /**
         * Used for testing. If set, the "context" parameter is not used
         */
        fun cache(cache: ProviderCache) = apply { this.cache = cache }

        /**
         * Used for testing. Facilitates unit testing of the asynchronous apply operation
         */
        fun coroutineContext(applyDispatcher: CoroutineDispatcher) = apply { this.applyDispatcher = applyDispatcher }
        fun build() = ConfidenceFeatureProvider(
            hooks,
            metadata,
            cache ?: StorageFileCache(context),
            client ?: ConfidenceRemoteClient(clientSecret, region),
            applyDispatcher,
        )
    }

    private var currEvaluationContext: EvaluationContext? = null

    override suspend fun initialize(initialContext: EvaluationContext?) {
        currEvaluationContext = initialContext
        if (initialContext == null) return
        try {
            val (resolvedFlags, resolveToken) = client.resolve(listOf(), initialContext)
            cache.refresh(resolvedFlags, resolveToken, initialContext)
        } catch (_: Throwable) {
            // Can't refresh cache at this time
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
        defaultValue: Boolean
    ): ProviderEvaluation<Boolean> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double
    ): ProviderEvaluation<Double> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int
    ): ProviderEvaluation<Int> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value
    ): ProviderEvaluation<Value> {
        return generateEvaluation(key, defaultValue)
    }

    override fun getStringEvaluation(
        key: String,
        defaultValue: String
    ): ProviderEvaluation<String> {
        return generateEvaluation(key, defaultValue)
    }

    private fun <T> generateEvaluation(key: String, defaultValue: T): ProviderEvaluation<T> {
        val parsedKey = FlagKey(key)
        val evaluationContext = currEvaluationContext ?: throw ProviderNotReadyError()
        return when(val resolve: CacheResolveResult = cache.resolve(parsedKey.flagName, evaluationContext)) {
            is CacheResolveResult.Found -> {
                val resolvedFlag = resolve.entry
                when(resolvedFlag.resolveReason) {
                    ResolveReason.RESOLVE_REASON_MATCH -> {
                        val resolvedValue: Value = findValueFromValuePath(resolvedFlag.value, parsedKey.valuePath)
                            ?: throw ParseError("Unable to parse flag value: ${parsedKey.valuePath.joinToString(separator = "/")}")
                        val value = getTyped<T>(resolvedValue) ?: defaultValue
                        processApplyAsync(parsedKey.flagName, resolvedFlag.resolveToken)
                        ProviderEvaluation(
                            value = value,
                            variant = resolvedFlag.variant,
                            reason = Reason.TARGETING_MATCH.toString())
                    }
                    else -> {
                        processApplyAsync(parsedKey.flagName, resolvedFlag.resolveToken)
                        ProviderEvaluation(
                            value = defaultValue,
                            reason = Reason.DEFAULT.toString())
                    }
                }
            }
            CacheResolveResult.Stale -> ProviderEvaluation(
                value = defaultValue,
                reason = Reason.STALE.toString())
            CacheResolveResult.NotFound -> throw FlagNotFoundError(parsedKey.flagName)
        }
    }

    private fun processApplyAsync(flagName: String, resolveToken: String) {
        CoroutineScope(applyDispatcher).launch {
            try {
                client.apply(listOf(AppliedFlag(flagName, Instant.now())), resolveToken)
            } catch (_: Throwable) {
                // Sending apply is best effort
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getTyped(v: Value): T? {
        return when(v) {
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
        return when(val currValue = value.structure[valuePath[0]]) {
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
                    splits.subList(1, splits.count()))
            }
        }
    }

    class ConfidenceMetadata(override var name: String? = "confidence") : Metadata
}