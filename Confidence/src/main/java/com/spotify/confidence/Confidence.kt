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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal const val SDK_ID = "SDK_ID_KOTLIN_CONFIDENCE"
internal const val SDK_VERSION = "0.5.3" // x-release-please-version

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
    private val debugLogger: DebugLogger?,
    private val androidContext: Context? = null,
    private val visitorIdContextKey: String = VISITOR_ID_CONTEXT_KEY
) : Contextual, EventSender {
    private val removedKeys = mutableListOf<String>()
    private val contextMap = MutableStateFlow(initialContext)
    private var currentFetchJob: Job? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val producers: MutableList<Producer> = mutableListOf()

    private val flagApplier = FlagApplierWithRetries(
        client = flagApplierClient,
        dispatcher = dispatcher,
        diskStorage = diskStorage
    )

    private suspend fun resolve(flags: List<String>): Result<FlagResolution> {
        debugLogger?.let {
            debugLogger.logFlag("Resolve", "${getContext()}")
        }
        return flagResolver.resolve(flags, getContext()).also {
            debugLogger?.logFlag("Resolve Completed", "${getContext()}")
        }
    }

    suspend fun awaitReconciliation(timeoutMillis: Long = 5000) {
        if (timeoutMillis <= 0) error("timeoutMillis need to be larger than 0")
        debugLogger?.logMessage("reconciliation started")
        yield() // will make sure that we respect other coroutine scopes triggered before this
        withSafeTimeout(timeoutMillis) {
            currentFetchJob?.join()
            activate()
        }
        debugLogger?.logMessage("reconciliation completed")
    }

    private suspend fun withSafeTimeout(timeout: Long, block: suspend () -> Unit) {
        try {
            withTimeout(timeout) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            debugLogger?.logMessage("timed out after $timeout")
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
        ) { flagName, resolveToken, shouldApply ->
            // this lambda will be invoked inside the evaluation process
            // and only if the resolve reason is not targeting key error.
            if (shouldApply) {
                apply(flagName, resolveToken)
            }
        }
        // we are using a custom serializer so that the Json is serialized correctly in the logs
        val newMap: Map<String, @Serializable(NetworkConfidenceValueSerializer::class) ConfidenceValue> =
            evaluationContext
        val contextJson = Json.encodeToJsonElement(newMap)
        val flag = key.splitToSequence(".").first()
        debugLogger?.logResolve(flag, contextJson)
        return eval
    }

    @Synchronized
    override fun putContext(key: String, value: ConfidenceValue) {
        val map = contextMap.value.toMutableMap()
        map[key] = value
        contextMap.value = map
        triggerNewFlagFetch()
        debugLogger?.logContext("PutContext", contextMap.value)
    }

    @Synchronized
    override fun putContext(context: Map<String, ConfidenceValue>) {
        val map = contextMap.value.toMutableMap()
        map += context
        contextMap.value = map
        triggerNewFlagFetch()
        debugLogger?.logContext("PutContext", contextMap.value)
    }

    /**
     * Adds/override entry to local context data only.
     * Warning: Does not trigger a new flag fetch after the context change.
     * @param context context to add.
     */
    @Synchronized
    fun putContextLocal(context: Map<String, ConfidenceValue>) {
        val map = contextMap.value.toMutableMap()
        map += context
        contextMap.value = map
        // No triggering of new flag fetch
        debugLogger?.logContext("putContextLocal", contextMap.value)
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
    @Synchronized
    fun putContext(context: Map<String, ConfidenceValue>, removedKeys: List<String>) {
        val map = contextMap.value.toMutableMap()
        map += context
        for (key in removedKeys) {
            map.remove(key)
        }
        this.removedKeys.addAll(removedKeys)
        contextMap.value = map
        triggerNewFlagFetch()
        debugLogger?.logContext("PutContext", contextMap.value)
    }

    private fun triggerNewFlagFetch() {
        currentFetchJob?.cancel().also {
            currentFetchJob = null
        }
        coroutineScope.launch {
            fetchAndActivate()
        }
    }

    @Synchronized
    override fun removeContext(key: String) {
        removeContext(listOf(key))
    }

    @Synchronized
    override fun removeContext(keys: Collection<String>) {
        val map = contextMap.value.toMutableMap()
        for (key in keys) {
            map.remove(key)
        }
        removedKeys.addAll(keys)
        contextMap.value = map
        triggerNewFlagFetch()
        debugLogger?.logContext("RemoveContext", contextMap.value)
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
        debugLogger,
        androidContext,
        visitorIdContextKey
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
        currentFetchJob?.cancel().also {
            currentFetchJob = null
        }
        currentFetchJob = fetch()
    }

    /**
     * Fetch latest flag evaluations and store them on disk. Regardless of the fetch outcome (success or failure), this
     * function activates the cache after the fetch.
     * Activating the cache means that the flag data on disk is loaded into memory, so consumers can access flag values.
     * Fetching is best-effort, so no error is propagated. Errors can still be thrown if something goes wrong access data on disk.
     */
    suspend fun fetchAndActivate() = kotlinx.coroutines.withContext(dispatcher) {
        currentFetchJob?.cancel().also {
            currentFetchJob = null
        }
        currentFetchJob = fetch()
        currentFetchJob?.join()
        activate()
    }

    override fun track(producer: Producer) {
        coroutineScope.launch {
            producer.updates().collect { update ->
                when (update) {
                    is Update.Event -> {
                        eventSenderEngine.emit(
                            update.name,
                            update.data,
                            getContext()
                        )
                        if (update.shouldFlush) {
                            eventSenderEngine.flush()
                        }
                    }

                    is Update.ContextUpdate -> putContext(update.context)
                }
            }
            producers.add(producer)
        }
    }

    /**
     * Resets the stored visitor ID and updates the evaluation context with the new visitor ID.
     * This function will generate a new UUID for the visitor ID, store it, and trigger a new flag fetch.
     */
    suspend fun resetVisitorId() {
        androidContext?.let { context ->
            val newVisitorId = VisitorUtil.resetId(context)
            putContext(mapOf(visitorIdContextKey to ConfidenceValue.String(newVisitorId)))
            awaitReconciliation()
            debugLogger?.logMessage("Visitor ID reset to: $newVisitorId")
        } ?: run {
            debugLogger?.logMessage("Cannot reset visitor ID: Android context is not available", isWarning = true)
        }
    }

    override fun stop() {
        for (producer in producers) {
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
     * @param visitorIdContextKey key to use for the visitor id in the context. Defaults to "visitor_id".
     */
    fun create(
        context: Context,
        clientSecret: String,
        initialContext: Map<String, ConfidenceValue> = mapOf(),
        region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        loggingLevel: LoggingLevel = LoggingLevel.WARN,
        timeoutMillis: Long = 10000,
        visitorIdContextKey: String = VISITOR_ID_CONTEXT_KEY
    ): Confidence {
        val debugLogger: DebugLogger? = if (loggingLevel == LoggingLevel.NONE) {
            null
        } else {
            DebugLoggerImpl(loggingLevel, clientSecret)
        }
        val sdkMetadata = SdkMetadata(SDK_ID, SDK_VERSION)
        val engine = EventSenderEngineImpl.instance(
            context,
            clientSecret,
            flushPolicies = listOf(minBatchSizeFlushPolicy),
            sdkMetadata = sdkMetadata,
            dispatcher = dispatcher,
            debugLogger = debugLogger
        )
        val flagApplierClient = FlagApplierClientImpl(
            clientSecret,
            sdkMetadata,
            region,
            dispatcher
        )
        val flagResolver = RemoteFlagResolver(
            clientSecret = clientSecret,
            region = region,
            httpClient = OkHttpClient.Builder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build(),
            dispatcher = dispatcher,
            sdkMetadata = sdkMetadata,
            debugLogger = debugLogger
        )

        val initContext = initialContext.toMutableMap()
        if (!initContext.containsKey(visitorIdContextKey)) {
            debugLogger?.logMessage("Adding visitor id to context with key: $visitorIdContextKey")
            initContext[visitorIdContextKey] = ConfidenceValue.String(VisitorUtil.getId(context))
        }
        debugLogger?.logContext("InitialContext", initContext)

        return Confidence(
            clientSecret,
            dispatcher,
            engine,
            FileDiskStorage.create(context),
            flagResolver,
            InMemoryCache(),
            initContext,
            flagApplierClient,
            null,
            region,
            debugLogger,
            context,
            visitorIdContextKey
        )
    }
}

suspend fun Confidence.awaitPutContext(context: Map<String, ConfidenceValue>) {
    putContext(context)
    awaitReconciliation()
}