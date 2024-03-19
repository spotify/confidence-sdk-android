package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.client.ConfidenceRegion
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
    private val region: ConfidenceRegion = ConfidenceRegion.GLOBAL,
    private val dispatcher: CoroutineDispatcher,
    private val eventSenderEngine: EventSenderEngine,
    private val root: ConfidenceContextProvider
) : Contextual, EventSender, FlagEvaluator {
    private val removedKeys = mutableListOf<String>()
    private val coroutineScope = CoroutineScope(dispatcher)
    private var contextMap: MutableMap<String, ConfidenceValue> = mutableMapOf()
    internal val flagResolver by lazy {
        RemoteFlagResolver(
            clientSecret,
            region,
            OkHttpClient(),
            dispatcher,
            SdkMetadata(SDK_ID, BuildConfig.SDK_VERSION)
        )
    }

    internal suspend fun resolveFlags(flags: List<String>): FlagResolution {
        return flagResolver.resolve(flags, getContext())
    }

    override fun putContext(key: String, value: ConfidenceValue) {
        contextMap[key] = value
    }

    override fun putContext(context: ConfidenceContext) {
        putContext(context.name, context.value)
    }

    override fun setContext(context: Map<String, ConfidenceValue>) {
        contextMap = context.toMutableMap()
    }

    override fun removeContext(key: String) {
        removedKeys.add(key)
        contextMap.remove(key)
    }

    override fun getContext(): Map<String, ConfidenceValue> =
        this.root.getContext().filterKeys { removedKeys.contains(it) } + contextMap

    override suspend fun <T> getValue(flag: String, defaultValue: T): T {
        val response = resolveFlags(listOf(flag))
        return response.getValue(flag, defaultValue)
    }

    override fun withContext(context: ConfidenceContext) = Confidence(
        clientSecret,
        region,
        dispatcher,
        eventSenderEngine,
        this
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
        ): EventSender {
            val engine = EventSenderEngine.instance(
                context,
                clientSecret,
                flushPolicies = listOf(confidenceFlushPolicy),
                dispatcher = dispatcher
            )
            val confidenceContext = object : ConfidenceContextProvider {
                override fun getContext(): Map<String, ConfidenceValue> {
                    return emptyMap()
                }
            }
            return Confidence(clientSecret, region, dispatcher, engine, confidenceContext)
        }
    }
}

private val confidenceFlushPolicy = object : FlushPolicy {
    private var size = 0
    override fun reset() {
        size = 0
    }

    override fun hit(event: Event) {
        size++
    }

    override fun shouldFlush(): Boolean {
        return size > 4
    }
}