package com.spotify.confidence.e2e

import android.content.Context
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

internal class TestBackend : AutoCloseable {
    private val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    @Volatile
    var resolveValue: (RecordedRequest) -> String = { "hello" }

    val baseUrl: String
        get() = server.url("/").toString()

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return when (request.path) {
                    "/v1/flags:resolve" -> resolveResponse(resolveValue(request))
                    "/v1/flags:apply" -> MockResponse().setResponseCode(200).setBody("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    fun awaitRequest(path: String, timeoutMillis: Long = 5_000): RecordedRequest {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            requests.lastOrNull { it.path == path }?.let { return it }
            Thread.sleep(10)
        }
        error("No request received for $path. Received: ${requests.map { it.path }}")
    }

    fun awaitRequests(path: String, count: Int, timeoutMillis: Long = 5_000): List<RecordedRequest> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            requests.filter { it.path == path }.takeIf { it.size >= count }?.let { return it }
            Thread.sleep(10)
        }
        error("Expected $count requests for $path. Received: ${requests.map { it.path }}")
    }

    override fun close() {
        server.shutdown()
    }

    private fun resolveResponse(stringValue: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "resolvedFlags": [
                {
                  "flag": "flags/e2e-flag",
                  "variant": "flags/e2e-flag/variants/enabled",
                  "value": {
                    "boolean": true,
                    "string": "$stringValue",
                    "integer": 42,
                    "double": 3.14,
                    "object": {
                      "nested": "value",
                      "count": 2
                    }
                  },
                  "flagSchema": {
                    "schema": {
                      "boolean": { "boolSchema": {} },
                      "string": { "stringSchema": {} },
                      "integer": { "intSchema": {} },
                      "double": { "doubleSchema": {} },
                      "object": {
                        "structSchema": {
                          "schema": {
                            "nested": { "stringSchema": {} },
                            "count": { "intSchema": {} }
                          }
                        }
                      }
                    }
                  },
                  "reason": "RESOLVE_REASON_MATCH",
                  "shouldApply": true
                }
              ],
              "resolveToken": "e2e-resolve-token"
            }
            """.trimIndent()
        )
}

internal fun awaitEventPersisted(context: Context, timeoutMillis: Long = 5_000): Boolean {
    val eventsDirectory = context.getDir("events", Context.MODE_PRIVATE)
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (eventsDirectory.walkTopDown().any { it.isFile && it.length() > 0 }) return true
        Thread.sleep(10)
    }
    return false
}
