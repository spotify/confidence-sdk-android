package dev.openfeature.contrib.providers.client

import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.ImmutableStructure
import dev.openfeature.sdk.Structure
import dev.openfeature.sdk.Value
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun EvaluationContext.toEvaluationContextStruct(): Structure {
    val ctxAttributes: MutableMap<String, Value> =
        mutableMapOf(Pair("targeting_key", Value.String(getTargetingKey())))
    ctxAttributes.putAll(asMap())
    return ImmutableStructure(ctxAttributes)
}

internal suspend inline fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        val callback = ContinuationCallback(this, continuation)
        enqueue(callback)
        continuation.invokeOnCancellation(callback)
    }
}

internal class ContinuationCallback(
    private val call: Call,
    private val continuation: CancellableContinuation<Response>
) : Callback, CompletionHandler {

    override fun onResponse(call: Call, response: Response) {
        continuation.resume(response)
    }

    override fun onFailure(call: Call, e: IOException) {
        if (!call.isCanceled()) {
            continuation.resumeWithException(e)
        }
    }

    override fun invoke(cause: Throwable?) {
        try {
            call.cancel()
        } catch (_: Throwable) {}
    }
}