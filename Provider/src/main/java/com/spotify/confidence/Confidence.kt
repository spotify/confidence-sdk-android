package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.apply.FlagApplierWithRetries
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ConfidenceRegion
import com.spotify.confidence.client.FlagApplierClientImpl
import com.spotify.confidence.client.SdkMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class Confidence private constructor(
    private val clientSecret: String,
    private val dispatcher: CoroutineDispatcher,
    private val eventSenderEngine: EventSenderEngine,
    private val root: ConfidenceContextProvider? = null,
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
    private val flagApplier: FlagApplierWithRetries
) : Contextual, EventSender {
    private val removedKeys = mutableListOf<String>()
    private val coroutineScope = CoroutineScope(dispatcher)
    private var contextMap: MutableMap<String, ConfidenceValue> = mutableMapOf()

    private val flagResolver by lazy {
        RemoteFlagResolver(
            clientSecret,
            region,
            OkHttpClient(),
            dispatcher,
            SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION)
        )
    }

    internal suspend fun resolveFlags(flags: List<String>): Result<FlagResolution> {
        return flagResolver.resolve(flags, getContext().openFeatureFlatten())
    }

    fun applyFlag(flagName: String, resolveToken: String) {
        flagApplier.apply(flagName, resolveToken)
    }

    override fun putContext(key: String, value: ConfidenceValue) {
        contextMap[key] = value
    }

    override fun putContext(context: Map<String, ConfidenceValue>) {
        contextMap += context
    }

    override fun setContext(context: Map<String, ConfidenceValue>) {
        contextMap = context.toMutableMap()
    }

    override fun removeContext(key: String) {
        removedKeys.add(key)
        contextMap.remove(key)
    }

    override fun getContext(): Map<String, ConfidenceValue> =
        this.root?.let {
            it.getContext().filterKeys { key -> !removedKeys.contains(key) } + contextMap
        } ?: contextMap

    override fun withContext(context: Map<String, ConfidenceValue>) = Confidence(
        clientSecret,
        dispatcher,
        eventSenderEngine,
        this,
        region,
        flagApplier
    ).also {
        it.putContext(context)
    }
    override fun send(
        definition: String,
        payload: ConfidenceFieldsType
    ) {
        eventSenderEngine.emit(definition, payload, getContext())
    }

    override fun onLowMemory(body: (List<File>) -> Unit): EventSender {
        coroutineScope.launch {
            eventSenderEngine
                .onLowMemoryChannel()
                .consumeEach {
                    body(it)
                }
        }
        return this
    }

    override fun stop() {
        eventSenderEngine.stop()
    }

    companion object {
        fun create(
            context: Context,
            clientSecret: String,
            region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): Confidence {
            val engine = EventSenderEngine.instance(
                context,
                clientSecret,
                flushPolicies = listOf(confidenceSizeFlushPolicy),
                dispatcher = dispatcher
            )
            val flagApplierClient = FlagApplierClientImpl(
                clientSecret,
                SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION),
                region,
                dispatcher
            )
            val flagApplier = FlagApplierWithRetries(
                client = flagApplierClient,
                dispatcher = dispatcher,
                diskStorage = FileDiskStorage.create(context)
            )
            return Confidence(
                clientSecret,
                dispatcher,
                engine,
                region = region,
                flagApplier = flagApplier
            ).addCommonContext(context)
        }
    }
}

internal fun Map<String, ConfidenceValue>.openFeatureFlatten(): Map<String, ConfidenceValue> {
    val context = toMutableMap()
    val openFeatureContext = context["open_feature"]?.let { it as ConfidenceValue.Struct }
    openFeatureContext?.let {
        context += it.map
    }
    context.remove("open_feature")
    return context
}