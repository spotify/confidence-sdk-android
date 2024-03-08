package com.spotify.confidence.client

import com.spotify.confidence.client.network.ApplyFlagsInteractor
import com.spotify.confidence.client.network.ApplyFlagsInteractorImpl
import com.spotify.confidence.client.network.ApplyFlagsRequest
import com.spotify.confidence.client.network.ResolveFlagsInteractor
import com.spotify.confidence.client.network.ResolveFlagsInteractorImpl
import com.spotify.confidence.client.serializers.FlagsSerializer
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

internal class ConfidenceRemoteClient : ConfidenceClient {
    private val clientSecret: String
    private val sdkMetadata: SdkMetadata
    private val okHttpClient: OkHttpClient
    private val baseUrl: String
    private val headers: Headers
    private val clock: Clock
    private val dispatcher: CoroutineDispatcher
    private val resolveInteractor: ResolveFlagsInteractor
    private val applyInteractor: ApplyFlagsInteractor

    constructor(
        clientSecret: String,
        sdkMetadata: SdkMetadata,
        region: ConfidenceRegion,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        this.clientSecret = clientSecret
        this.sdkMetadata = sdkMetadata
        this.okHttpClient = OkHttpClient()
        this.headers = Headers.headersOf(
            "Content-Type",
            "application/json",
            "Accept",
            "application/json"
        )
        baseUrl = when (region) {
            ConfidenceRegion.GLOBAL -> "https://resolver.confidence.dev"
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
        sdkMetadata: SdkMetadata = SdkMetadata("", ""),
        baseUrl: HttpUrl,
        clock: Clock = Clock.CalendarBacked.systemUTC(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        this.clientSecret = clientSecret
        this.sdkMetadata = sdkMetadata
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
            false,
            Sdk(sdkMetadata.sdkId, sdkMetadata.sdkVersion)
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
            resolveToken,
            Sdk(sdkMetadata.sdkId, sdkMetadata.sdkVersion)
        )
        val result = applyInteractor(request).runCatching {
            if (isSuccessful) {
                Result.Success
            } else {
                Result.Failure
            }
        }.getOrElse {
            Result.Failure
        }

        return result
    }
}

private fun Response.toResolveFlags(): ResolveResponse {
    val bodyString = body!!.string()

    // building the json class responsible for serializing the object
    val networkJson = Json {
        serializersModule = SerializersModule {
            contextual(FlagsSerializer)
            ignoreUnknownKeys = true
        }
    }
    return ResolveResponse.Resolved(networkJson.decodeFromString(bodyString))
}

sealed class ResolveResponse {
    object NotModified : ResolveResponse()
    data class Resolved(val flags: ResolveFlags) : ResolveResponse()
}

data class SdkMetadata(val sdkId: String, val sdkVersion: String)