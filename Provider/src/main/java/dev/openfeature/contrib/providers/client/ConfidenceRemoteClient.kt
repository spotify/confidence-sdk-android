package dev.openfeature.contrib.providers.client

import dev.openfeature.contrib.providers.client.network.ApplyFlagsInteractor
import dev.openfeature.contrib.providers.client.network.ApplyFlagsInteractorImpl
import dev.openfeature.contrib.providers.client.network.ApplyFlagsRequest
import dev.openfeature.contrib.providers.client.network.ResolveFlagsInteractor
import dev.openfeature.contrib.providers.client.network.ResolveFlagsInteractorImpl
import dev.openfeature.contrib.providers.client.serializers.FlagsSerializer
import dev.openfeature.sdk.EvaluationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class ConfidenceRemoteClient : ConfidenceClient {
    private val clientSecret: String
    private val okHttpClient: OkHttpClient
    private val baseUrl: String
    private val headers: Headers
    private val clock: Clock
    private val dispatcher: CoroutineDispatcher
    private val resolveInteractor: ResolveFlagsInteractor
    private val applyInteractor: ApplyFlagsInteractor

    constructor(
        clientSecret: String,
        region: ConfidenceRegion,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        this.clientSecret = clientSecret
        this.okHttpClient = OkHttpClient()
        this.headers = Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
        baseUrl = when (region) {
            ConfidenceRegion.EUROPE -> "https://resolver.eu.confidence.dev"
            ConfidenceRegion.USA -> "https://resolver.us.confidence.dev"
        }
        this.clock = Clock.CalendarBacked.systemUTC()
        this.dispatcher = dispatcher

        this.resolveInteractor = ResolveFlagsInteractorImpl(
            httpClient = okHttpClient,
            baseUrl = baseUrl,
            dispatcher = dispatcher
        )

        this.applyInteractor = ApplyFlagsInteractorImpl(
            httpClient = okHttpClient,
            baseUrl = baseUrl,
            dispatcher = dispatcher
        )
    }

    internal constructor(
        clientSecret: String = "",
        baseUrl: HttpUrl,
        clock: Clock = Clock.CalendarBacked.systemUTC(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        this.clientSecret = clientSecret
        this.okHttpClient = OkHttpClient()
        this.headers = Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
        this.baseUrl = baseUrl.toString()
        this.clock = clock
        this.dispatcher = dispatcher

        this.resolveInteractor = ResolveFlagsInteractorImpl(
            httpClient = okHttpClient,
            baseUrl = baseUrl.toString(),
            dispatcher = dispatcher
        )

        this.applyInteractor = ApplyFlagsInteractorImpl(
            httpClient = okHttpClient,
            baseUrl = baseUrl.toString(),
            dispatcher = dispatcher
        )
    }

    override suspend fun resolve(
        flags: List<String>,
        ctx: EvaluationContext
    ): ResolveFlags {
        val request = ResolveFlagsRequest(
            flags.map { "flags/$it" },
            ctx.toEvaluationContextStruct(),
            clientSecret,
            false
        )

        val networkResponse = resolveInteractor(request)
        val bodyString = networkResponse.body!!.string()

        // building the json class responsible for serializing the object
        val networkJson = Json {
            serializersModule = SerializersModule {
                contextual(FlagsSerializer)
            }
        }
        return networkJson.decodeFromString(bodyString)
    }

    override suspend fun apply(flags: List<AppliedFlag>, resolveToken: String) {
        val request = ApplyFlagsRequest(
            flags.map { AppliedFlag("flags/${it.flag}", it.applyTime) },
            clock.currentTime(),
            clientSecret,
            resolveToken
        )
        applyInteractor(request)
    }
}