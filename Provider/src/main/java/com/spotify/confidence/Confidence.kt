package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.apply.FlagApplierWithRetries
import com.spotify.confidence.cache.DiskStorage
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ConfidenceRegion
import com.spotify.confidence.client.FlagApplierClient
import com.spotify.confidence.client.FlagApplierClientImpl
import com.spotify.confidence.client.SdkMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

interface Confidence : Contextual, EventSender
interface ConfidenceShutdownAPI {
    fun shutdown()
}

class RootConfidence internal constructor(
    private val clientSecret: String,
    private val dispatcher: CoroutineDispatcher,
    private val eventSenderEngine: EventSenderEngine,
    private val diskStorage: DiskStorage,
    private val flagResolver: FlagResolver,
    private val flagApplierClient: FlagApplierClient,
    private val parent: ConfidenceContextProvider? = null,
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL
) : Confidence by ConfidenceImpl(
    clientSecret,
    dispatcher,
    eventSenderEngine,
    diskStorage,
    flagResolver,
    flagApplierClient,
    parent,
    region
),
    ConfidenceShutdownAPI {
    private val flagApplier = FlagApplierWithRetries(
        client = flagApplierClient,
        dispatcher = dispatcher,
        diskStorage = diskStorage
    )

    override fun shutdown() {
        eventSenderEngine.stop()
    }

    internal suspend fun resolve(flags: List<String>): Result<FlagResolution> {
        return flagResolver.resolve(flags, getContext().openFeatureFlatten())
    }

    internal fun apply(flagName: String, resolveToken: String) {
        flagApplier.apply(flagName, resolveToken)
    }
}

internal class ChildConfidence internal constructor(
    private val clientSecret: String,
    private val dispatcher: CoroutineDispatcher,
    private val eventSenderEngine: EventSenderEngine,
    private val diskStorage: DiskStorage,
    private val flagResolver: FlagResolver,
    private val flagApplierClient: FlagApplierClient,
    private val parent: ConfidenceContextProvider? = null,
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL
) : Confidence by ConfidenceImpl(
    clientSecret,
    dispatcher,
    eventSenderEngine,
    diskStorage,
    flagResolver,
    flagApplierClient,
    parent,
    region
)

class ConfidenceImpl internal constructor(
    private val clientSecret: String,
    private val dispatcher: CoroutineDispatcher,
    private val eventSenderEngine: EventSenderEngine,
    private val diskStorage: DiskStorage,
    private val flagResolver: FlagResolver,
    private val flagApplierClient: FlagApplierClient,
    private val parent: ConfidenceContextProvider? = null,
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL
) : Confidence {
    private val removedKeys = mutableListOf<String>()
    private var contextMap: MutableMap<String, ConfidenceValue> = mutableMapOf()

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
        this.parent?.let {
            it.getContext().filterKeys { key -> !removedKeys.contains(key) } + contextMap
        } ?: contextMap

    override fun withContext(context: Map<String, ConfidenceValue>): Confidence = ChildConfidence(
        clientSecret,
        dispatcher,
        eventSenderEngine,
        diskStorage,
        flagResolver,
        flagApplierClient,
        this,
        region
    ).also {
        it.putContext(context)
    }

    override fun send(
        definition: String,
        payload: ConfidenceFieldsType
    ) {
        eventSenderEngine.emit(definition, payload, getContext())
    }
}

internal fun Map<String, ConfidenceValue>.openFeatureFlatten(): Map<String, ConfidenceValue> {
    val context = this.toMutableMap()
    val openFeatureContext = context[OPEN_FEATURE_CONTEXT_KEY]?.let { it as ConfidenceValue.Struct }
    openFeatureContext?.let {
        context += it.map
    }
    context.remove(OPEN_FEATURE_CONTEXT_KEY)
    return context
}

internal const val OPEN_FEATURE_CONTEXT_KEY = "open_feature"

object ConfidenceFactory {
    fun create(
        context: Context,
        clientSecret: String,
        region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): RootConfidence {
        val engine = EventSenderEngineImpl.instance(
            context,
            clientSecret,
            flushPolicies = listOf(minBatchSizeFlushPolicy),
            sdkMetadata = SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION),
            dispatcher = dispatcher
        )
        val flagApplierClient = FlagApplierClientImpl(
            clientSecret,
            SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION),
            region,
            dispatcher
        )

        val flagResolver = RemoteFlagResolver(
            clientSecret = clientSecret,
            region = region,
            httpClient = OkHttpClient(),
            dispatcher = dispatcher,
            sdkMetadata = SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION)
        )
        return RootConfidence(
            clientSecret,
            dispatcher,
            engine,
            region = region,
            flagResolver = flagResolver,
            diskStorage = FileDiskStorage.create(context),
            flagApplierClient = flagApplierClient
        )
    }
}