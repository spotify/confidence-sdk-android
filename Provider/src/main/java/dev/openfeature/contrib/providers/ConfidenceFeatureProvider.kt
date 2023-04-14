package dev.openfeature.contrib.providers

import android.content.Context
<<<<<<< HEAD
import dev.openfeature.contrib.providers.apply.FlagApplier
import dev.openfeature.contrib.providers.apply.FlagApplierWithRetries
import dev.openfeature.contrib.providers.cache.ProviderCache
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.ConfidenceRegion
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient
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
import kotlinx.coroutines.withContext

class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>>,
    override val metadata: Metadata,
    private val cache: ProviderCache,
    private val client: ConfidenceClient,
    private val flagApplier: FlagApplier,
    private val dispatcher: CoroutineDispatcher
) : FeatureProvider {
    override suspend fun initialize(initialContext: EvaluationContext?) {
        if (initialContext == null) return
        withContext(dispatcher) {
            try {
                val (resolvedFlags, resolveToken) = client.resolve(listOf(), initialContext)
                cache.refresh(resolvedFlags.list, resolveToken, initialContext)
            } catch (_: Throwable) {
                // Can't refresh cache at this time. Do we retry at a later time?
            }
=======
import dev.openfeature.contrib.providers.cache.ProviderCache
import dev.openfeature.contrib.providers.cache.ProviderCache.CacheResolveResult
import dev.openfeature.contrib.providers.cache.StorageFileCache
import dev.openfeature.contrib.providers.client.*
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient.*
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient.ConfidenceRegion.*
import dev.openfeature.sdk.*
import dev.openfeature.sdk.exceptions.OpenFeatureError.*
import java.time.Instant

class ConfidenceFeatureProvider private constructor(
    override val hooks: List<Hook<*>> = listOf(),
    override val metadata: Metadata = ConfidenceMetadata(),
    private val cache: ProviderCache,
    private val client: ConfidenceClient,
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
        fun build() = ConfidenceFeatureProvider(
            hooks,
            metadata,
            cache ?: StorageFileCache(context),
            client ?: ConfidenceRemoteClient(clientSecret, region))
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
>>>>>>> 43375cb (Transfer codebase)
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
<<<<<<< HEAD
        defaultValue: Boolean,
        context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        return generateEvaluation(key, defaultValue, context)
=======
        defaultValue: Boolean
    ): ProviderEvaluation<Boolean> {
        return generateEvaluation(key, defaultValue)
>>>>>>> 43375cb (Transfer codebase)
    }

    override fun getDoubleEvaluation(
        key: String,
<<<<<<< HEAD
        defaultValue: Double,
        context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        return generateEvaluation(key, defaultValue, context)
=======
        defaultValue: Double
    ): ProviderEvaluation<Double> {
        return generateEvaluation(key, defaultValue)
>>>>>>> 43375cb (Transfer codebase)
    }

    override fun getIntegerEvaluation(
        key: String,
<<<<<<< HEAD
        defaultValue: Int,
        context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        return generateEvaluation(key, defaultValue, context)
=======
        defaultValue: Int
    ): ProviderEvaluation<Int> {
        return generateEvaluation(key, defaultValue)
>>>>>>> 43375cb (Transfer codebase)
    }

    override fun getObjectEvaluation(
        key: String,
<<<<<<< HEAD
        defaultValue: Value,
        context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        return generateEvaluation(key, defaultValue, context)
=======
        defaultValue: Value
    ): ProviderEvaluation<Value> {
        return generateEvaluation(key, defaultValue)
>>>>>>> 43375cb (Transfer codebase)
    }

    override fun getStringEvaluation(
        key: String,
<<<<<<< HEAD
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
=======
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
                        processApplyAsync(parsedKey.flagName, resolvedFlag.resolveToken)
                        ProviderEvaluation(
                            value = getTyped<T>(resolvedValue) ?: defaultValue,
                            variant = resolvedFlag.variant,
                            reason = Reason.TARGETING_MATCH.toString())
                    }
                    else -> {
                        ProviderEvaluation(
                            value = defaultValue,
                            reason = Reason.DEFAULT.toString())
>>>>>>> 43375cb (Transfer codebase)
                    }
                }
            }
            CacheResolveResult.Stale -> ProviderEvaluation(
                value = defaultValue,
<<<<<<< HEAD
                reason = Reason.STALE.toString()
            )
=======
                reason = Reason.STALE.toString())
>>>>>>> 43375cb (Transfer codebase)
            CacheResolveResult.NotFound -> throw FlagNotFoundError(parsedKey.flagName)
        }
    }

<<<<<<< HEAD
    @Suppress("UNCHECKED_CAST")
    private fun <T> getTyped(v: Value): T? {
        return when (v) {
=======
    private fun processApplyAsync(flagName: String, resolveToken: String) {
        Thread {
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
>>>>>>> 43375cb (Transfer codebase)
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
<<<<<<< HEAD
        return when (val currValue = value.structure[valuePath[0]]) {
=======
        return when(val currValue = value.structure[valuePath[0]]) {
>>>>>>> 43375cb (Transfer codebase)
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
<<<<<<< HEAD
                    splits.subList(1, splits.count())
                )
            }
        }
    }
    companion object {
        private class ConfidenceMetadata(override var name: String? = "confidence") : Metadata

        @Suppress("LongParameterList")
        suspend fun create(
            context: Context,
            clientSecret: String,
            region: ConfidenceRegion = ConfidenceRegion.EUROPE,
            hooks: List<Hook<*>> = listOf(),
            client: ConfidenceClient? = null,
            metadata: Metadata = ConfidenceMetadata(),
            cache: ProviderCache? = null,
            flagApplier: FlagApplier? = null,
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
                dispatcher
            )
        }
    }
=======
                    splits.subList(1, splits.count()))
            }
        }
    }

    class ConfidenceMetadata(override var name: String? = "confidence") : Metadata
>>>>>>> 43375cb (Transfer codebase)
}