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
import okhttp3.Response
import java.net.HttpURLConnection

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
    ): ResolveResponse {
        val request = ResolveFlagsRequest(
            flags.map { "flags/$it" },
            ctx.toEvaluationContextStruct(),
            clientSecret,
            false
        )

        val networkResponse = resolveInteractor(request)
        // The backend right now will never return this status code
        // we are also not sending the ETag to the backend.
        // the code is added as part of the future work to support this feature.
        return if (networkResponse.code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            ResolveResponse.NotModified
        } else {
            networkResponse.toResolveFlags()
        }
    }

    override suspend fun apply(flags: List<AppliedFlag>, resolveToken: String): Result {
        val request = ApplyFlagsRequest(
            flags.map { AppliedFlag("flags/${it.flag}", it.applyTime) },
            clock.currentTime(),
            clientSecret,
            resolveToken
        )
        applyInteractor(request).runCatching {
            return Result.Failure
        }

        return Result.Success
    }
}

private fun Response.toResolveFlags(): ResolveResponse {
    val bodyString = body!!.string()

    // building the json class responsible for serializing the object
    val networkJson = Json {
        serializersModule = SerializersModule {
            contextual(FlagsSerializer)
        }
    }
    return ResolveResponse.Resolved(networkJson.decodeFromString(bodyString))
}

sealed class ResolveResponse {
    object NotModified : ResolveResponse()
    data class Resolved(val flags: ResolveFlags) : ResolveResponse()
}