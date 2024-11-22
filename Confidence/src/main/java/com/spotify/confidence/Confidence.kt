package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.ConfidenceError.ParseError
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
    private val debugLogger: DebugLogger?
) : Contextual, EventSender {
    private val removedKeys = mutableListOf<String>()
    private val contextMap = MutableStateFlow(initialContext)
    private var currentFetchJob: Job? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val eventProducers: MutableList<EventProducer> = mutableListOf()

    private val flagApplier = FlagApplierWithRetries(
        client = flagApplierClient,
        dispatcher = dispatcher,
        diskStorage = diskStorage
    )

    private suspend fun resolve(flags: List<String>): Result<FlagResolution> {
        debugLogger?.let {
            debugLogger.logFlag("Resolve")
        }
        return flagResolver.resolve(flags, getContext())
    }

    suspend fun awaitReconciliation() {
        if (currentFetchJob != null) {
            currentFetchJob?.join()
            activate()
        }
    }

    /**
     * Apply a flag
     * @param flagName name of the flag.
     * @param resolveToken resolve token.
     */
    fun apply(flagName: String, resolveToken: String) {
        flagApplier.apply(flagName, resolveToken)
        debugLogger?.logFlag("Apply", flagName)
    }

    /**
     * @return flag value for a specific flag.
     * @param key expects dot-notation to retrieve a specific entry in the flag's value, e.g. "flagname.myentry"
     * @param default  returned in case of errors or in case of the variant's rule indicating to use the default value.
     */
    fun <T> getValue(key: String, default: T) = getFlag(key, default).value

    /**
     * @return evaluation data for a specific flag. Evaluation data includes the variant's name and reason/error information.
     * @param key expects dot-notation to retrieve a specific entry in the flag's value, e.g. "flagname.myentry"
     * @param default returned in case of errors or in case of the variant's rule indicating to use the default value.
     */
    fun <T> getFlag(
        key: String,
        default: T
    ): Evaluation<T> {
        val evaluationContext = getContext()
        val eval = cache.get().getEvaluation(
            key,
            default,
            evaluationContext
        ) { flagName, resolveToken ->
            // this lambda will be invoked inside the evaluation process
            // and only if the resolve reason is not targeting key error.
            apply(flagName, resolveToken)
        }
        // we are using a custom serializer so that the Json is serialized correctly in the logs
        val newMap: Map<String, @Serializable(NetworkConfidenceValueSerializer::class) ConfidenceValue> =
            evaluationContext
        val contextJson = Json.encodeToJsonElement(newMap)
        val flag = key.splitToSequence(".").first()
        debugLogger?.logResolve(flag, contextJson)
        return eval
    }

    override fun putContext(key: String, value: ConfidenceValue) {
        val map = contextMap.value.toMutableMap()
        map[key] = value
        contextMap.value = map
        debugLogger?.logContext("PutContext", contextMap.value)
    }

    override suspend fun putContextAndAwait(key: String, value: ConfidenceValue) {
        putContext(key, value)
        triggerContextChange()
    }

    private suspend fun triggerContextChange() {
        // Only have context changes trigger fetch on top level Confidence
        if (parent == null) {
            fetchAndActivate()
        }
    }

    override fun putContext(context: Map<String, ConfidenceValue>) {
        val map = contextMap.value.toMutableMap()
        map += context
        contextMap.value = map
        debugLogger?.logContext("PutContext", contextMap.value)
    }

    override suspend fun putContextAndAwait(context: Map<String, ConfidenceValue>) {
        putContext(context)
        triggerContextChange()
    }

    /**
     * Check if cache is empty
     */
    fun isStorageEmpty(): Boolean = diskStorage.read() == FlagResolution.EMPTY

    /**
     * Mutate context by adding an entry and removing another
     * @param context context to add.
     * @param removedKeys key to remove from context.
     */
    suspend fun putContextAndAwait(context: Map<String, ConfidenceValue>, removedKeys: List<String>) {
        val map = contextMap.value.toMutableMap()
        map += context
        for (key in removedKeys) {
            map.remove(key)
        }
        this.removedKeys.addAll(removedKeys)
        contextMap.value = map
        debugLogger?.logContext("PutContext", contextMap.value)
        triggerContextChange()
    }

    override fun removeContext(key: String) {
        val map = contextMap.value.toMutableMap()
        map.remove(key)
        removedKeys.add(key)
        contextMap.value = map
        debugLogger?.logContext("RemoveContext", contextMap.value)
    }

    override suspend fun removeContextAndAwait(key: String) {
        removeContext(key)
        triggerContextChange()
    }

    override fun getContext(): Map<String, ConfidenceValue> =
        this.parent?.let {
            it.getContext().filterKeys { key -> !removedKeys.contains(key) } + contextMap.value
        } ?: contextMap.value

    override fun withContext(context: Map<String, ConfidenceValue>): EventSender = Confidence(
        clientSecret,
        dispatcher,
        eventSenderEngine,
        diskStorage,
        flagResolver,
        cache,
        mapOf(),
        flagApplierClient,
        this,
        region,
        debugLogger
    ).also {
        it.putContext(context)
    }

    override fun track(
        eventName: String,
        data: ConfidenceFieldsType
    ) {
        eventSenderEngine.emit(eventName, data, getContext())
    }

    override fun flush() {
        eventSenderEngine.flush()
    }

    private val networkExceptionHandler by lazy {
        CoroutineExceptionHandler { _, throwable ->
            debugLogger?.logMessage("Network error", isWarning = true, throwable = throwable)
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

    /**
     * Activating the cache means that the flag data on disk is loaded into memory, so consumers can access flag values.
     */
    fun activate() {
        val resolveResponse = diskStorage.read()
        cache.refresh(resolveResponse)
    }

    /**
     * Fetch latest flag evaluations and store them on disk. Note that "activate" must be called for this data to be
     * made available in the app session.
     */
    fun asyncFetch() {
        currentFetchJob?.cancel()
        currentFetchJob = fetch()
    }

    /**
     * Fetch latest flag evaluations and store them on disk. Regardless of the fetch outcome (success or failure), this
     * function activates the cache after the fetch.
     * Activating the cache means that the flag data on disk is loaded into memory, so consumers can access flag values.
     * Fetching is best-effort, so no error is propagated. Errors can still be thrown if something goes wrong access data on disk.
     */
    suspend fun fetchAndActivate() = kotlinx.coroutines.withContext(dispatcher) {
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
                        event.data,
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

/**
 * Confidence instance, which can be used for flag evaluation and event tracking.
 */
object ConfidenceFactory {
    /**
     * Create a Factory Confidence instance.
     * @param context application context.
     * @param clientSecret confidence clientSecret, which is found in Confidence console.
     * @param initialContext can be set initially, e.g. targeting_key:value.
     * @param region region of operation.
     * @param dispatcher coroutine dispatcher.
     * @param loggingLevel allows to print warnings or debugging information to the local console.
     * @param timeoutMillis sets a timeout for completing an HTTP call. Defaults to 10 seconds
     */
    fun create(
        context: Context,
        clientSecret: String,
        initialContext: Map<String, ConfidenceValue> = mapOf(),
        region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        loggingLevel: LoggingLevel = LoggingLevel.WARN,
        timeoutMillis: Long = 10000
    ): Confidence {
        val debugLogger: DebugLogger? = if (loggingLevel == LoggingLevel.NONE) {
            null
        } else {
            DebugLoggerImpl(loggingLevel, clientSecret)
        }
        val engine = EventSenderEngineImpl.instance(
            context,
            clientSecret,
            flushPolicies = listOf(minBatchSizeFlushPolicy),
            sdkMetadata = SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION),
            dispatcher = dispatcher,
            debugLogger = debugLogger
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
            httpClient = OkHttpClient.Builder()
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build(),
            dispatcher = dispatcher,
            sdkMetadata = SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION)
        )
        val visitorId = ConfidenceValue.String(VisitorUtil.getId(context))
        val initContext = initialContext.toMutableMap()
        initContext[VISITOR_ID_CONTEXT_KEY] = visitorId
        debugLogger?.logContext("InitialContext", initContext)

        return Confidence(
            clientSecret,
            dispatcher,
            engine,
            initialContext = initContext,
            region = region,
            flagResolver = flagResolver,
            diskStorage = FileDiskStorage.create(context),
            flagApplierClient = flagApplierClient,
            debugLogger = debugLogger
        )
    }
}

@Deprecated("Use putContextAndAwait(context) instead", replaceWith = ReplaceWith("putContextAndAwait(context)"))
suspend fun Confidence.awaitPutContext(context: Map<String, ConfidenceValue>) {
    putContext(context)
    awaitReconciliation()
}