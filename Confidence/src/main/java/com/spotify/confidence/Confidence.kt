package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.apply.FlagApplierWithRetries
import com.spotify.confidence.cache.DiskStorage
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.FlagApplierClient
import com.spotify.confidence.client.FlagApplierClientImpl
import com.spotify.confidence.client.SdkMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal const val SDK_ID = "SDK_ID_KOTLIN_CONFIDENCE"

class Confidence internal constructor(
    private val clientSecret: String,
    private val dispatcher: CoroutineDispatcher,
    private val eventSenderEngine: EventSenderEngine,
    private val diskStorage: DiskStorage,
    private val flagResolver: FlagResolver,
    private val cache: ProviderCache = InMemoryCache(),
    initialContext: Map<String, ConfidenceValue> = mapOf(),
    private val flagApplierClient: FlagApplierClient,
    private val parent: ConfidenceContextProvider? = null,
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL
) : Contextual, EventSender {
    private val removedKeys = mutableListOf<String>()
    private val contextMap = MutableStateFlow(initialContext)
    private var currentFetchJob: Job? = null

    // only return changes not the initial value
    // only return distinct value
    private val contextChanges: Flow<Map<String, ConfidenceValue>> = contextMap
        .drop(1)
        .distinctUntilChanged()
    private val coroutineScope = CoroutineScope(dispatcher)
    private val eventProducers: MutableList<EventProducer> = mutableListOf()

    init {
        coroutineScope.launch {
            contextChanges
                .collect {
                    fetchAndActivate()
                }
        }
    }

    private val flagApplier = FlagApplierWithRetries(
        client = flagApplierClient,
        dispatcher = dispatcher,
        diskStorage = diskStorage
    )

    private suspend fun resolve(flags: List<String>): Result<FlagResolution> {
        return flagResolver.resolve(flags, getContext())
    }
    suspend fun awaitReconciliation() {
        if (currentFetchJob != null) {
            currentFetchJob?.join()
            activate()
        }
    }

    fun apply(flagName: String, resolveToken: String) {
        flagApplier.apply(flagName, resolveToken)
    }

    fun <T> getValue(key: String, default: T) = getFlag(key, default).value

    fun <T> getFlag(
        key: String,
        default: T
    ): Evaluation<T> = cache.get().getEvaluation(
        key,
        default,
        getContext()
    ) { flagName, resolveToken ->
        // this lambda will be invoked inside the evaluation process
        // and only if the resolve reason is not targeting key error.
        apply(flagName, resolveToken)
    }

    @Synchronized
    override fun putContext(key: String, value: ConfidenceValue) {
        val map = contextMap.value.toMutableMap()
        map[key] = value
        contextMap.value = map
    }

    @Synchronized
    override fun putContext(context: Map<String, ConfidenceValue>) {
        val map = contextMap.value.toMutableMap()
        map += context
        contextMap.value = map
    }

    fun isStorageEmpty(): Boolean = diskStorage.read() == FlagResolution.EMPTY

    @Synchronized
    fun putContext(context: Map<String, ConfidenceValue>, removedKeys: List<String>) {
        val map = contextMap.value.toMutableMap()
        map += context
        for (key in removedKeys) {
            map.remove(key)
        }
        this.removedKeys.addAll(removedKeys)
        contextMap.value = map
    }

    @Synchronized
    override fun removeContext(key: String) {
        val map = contextMap.value.toMutableMap()
        map.remove(key)
        removedKeys.add(key)
        contextMap.value = map
    }

    override fun getContext(): Map<String, ConfidenceValue> =
        this.parent?.let {
            it.getContext().filterKeys { key -> !removedKeys.contains(key) } + contextMap.value
        } ?: contextMap.value

    override fun withContext(context: Map<String, ConfidenceValue>): Confidence = Confidence(
        clientSecret,
        dispatcher,
        eventSenderEngine,
        diskStorage,
        flagResolver,
        cache,
        mapOf(),
        flagApplierClient,
        this,
        region
    ).also {
        it.putContext(context)
    }

    override fun track(
        eventName: String,
        message: ConfidenceFieldsType
    ) {
        eventSenderEngine.emit(eventName, message, getContext())
    }

    override fun flush() {
        eventSenderEngine.flush()
    }

    private val networkExceptionHandler by lazy {
        CoroutineExceptionHandler { _, _ ->
            // network failed, provider is ready but with default/cache values
        }
    }

    private fun fetch(): Job = coroutineScope.launch(networkExceptionHandler) {
        try {
            val resolveResponse = resolve(listOf())
            if (resolveResponse is Result.Success) {
                // we store the flag anyways except when the response was not modified
                if (resolveResponse.data != FlagResolution.EMPTY) {
                    diskStorage.store(resolveResponse.data)
                }
            }
        } catch (e: ParseError) {
            throw ParseError(e.message)
        }
    }

    fun activate() {
        val resolveResponse = diskStorage.read()
        cache.refresh(resolveResponse)
    }

    fun asyncFetch() {
        currentFetchJob?.cancel()
        currentFetchJob = fetch()
    }

    suspend fun fetchAndActivate() {
        currentFetchJob?.cancel()
        currentFetchJob = fetch()
        currentFetchJob?.join()
        activate()
    }
    override fun track(eventProducer: EventProducer) {
        coroutineScope.launch {
            eventProducer
                .events()
                .collect { event ->
                    eventSenderEngine.emit(
                        event.name,
                        event.message,
                        getContext()
                    )
                    if (event.shouldFlush) {
                        eventSenderEngine.flush()
                    }
                }
        }

        coroutineScope.launch {
            eventProducer.contextChanges()
                .collect(this@Confidence::putContext)
        }
        eventProducers.add(eventProducer)
    }

    override fun stop() {
        for (producer in eventProducers) {
            producer.stop()
        }
        if (parent == null) {
            eventSenderEngine.stop()
        }
        coroutineScope.cancel()
    }
}

internal const val VISITOR_ID_CONTEXT_KEY = "visitor_id"

object ConfidenceFactory {
    fun create(
        context: Context,
        clientSecret: String,
        initialContext: Map<String, ConfidenceValue> = mapOf(),
        region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Confidence {
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
        val visitorId = ConfidenceValue.String(VisitorUtil.getId(context))
        val initContext = initialContext.toMutableMap()
        initContext[VISITOR_ID_CONTEXT_KEY] = visitorId

        return Confidence(
            clientSecret,
            dispatcher,
            engine,
            initialContext = initContext,
            region = region,
            flagResolver = flagResolver,
            diskStorage = FileDiskStorage.create(context),
            flagApplierClient = flagApplierClient
        )
    }
}

suspend fun Confidence.awaitPutContext(context: Map<String, ConfidenceValue>) {
    putContext(context)
    awaitReconciliation()
}