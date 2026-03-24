@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spotify.confidence

import android.util.Base64
import com.spotify.confidence.ConfidenceError.ParseError
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.Clock
import com.spotify.confidence.client.FlagApplierClientImpl
import com.spotify.confidence.client.Flags
import com.spotify.confidence.client.ResolveFlags
import com.spotify.confidence.client.ResolvedFlag
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Date

internal class ConfidenceRemoteClientTests {
    private val mockWebServer = MockWebServer()
    private val testTelemetry = Telemetry(SDK_ID + "_TEST", Telemetry.Library.CONFIDENCE, "")

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkStatic(Base64::class)
    }

    @Test
    fun testDeserializeResolveResponse() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val instant = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/test-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/test-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {\n" +
            "    \"mystring\": \"red\",\n" +
            "    \"myboolean\": false,\n" +
            "    \"myinteger\": 7,\n" +
            "    \"mydouble\": 3.14,\n" +
            "    \"mynull\": null,\n" +
            "    \"mydate\": \"$instant\",\n" +
            "    \"mystruct\": {\n" +
            "        \"innerString\": \"innerValue\"\n" +
            "    }\n" +
            "   },\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"mystruct\": {\n" +
            "      \"structSchema\": {\n" +
            "       \"schema\": {\n" +
            "        \"innerString\": {\n" +
            "         \"stringSchema\": {}\n" +
            "        }\n" +
            "       }\n" +
            "      }\n" +
            "     },\n" +
            "     \"myboolean\": {\n" +
            "      \"boolSchema\": {}\n" +
            "     },\n" +
            "     \"mystring\": {\n" +
            "      \"stringSchema\": {}\n" +
            "     },\n" +
            "     \"mydouble\": {\n" +
            "      \"doubleSchema\": {}\n" +
            "     },\n" +
            "     \"mynull\": {\n" +
            "      \"doubleSchema\": {}\n" +
            "     },\n" +
            "     \"myinteger\": {\n" +
            "      \"intSchema\": {}\n" +
            "     },\n" +
            "     \"mydate\": {\n" +
            "      \"stringSchema\": {}\n" +
            "     }\n" +
            "    }\n" +
            "   },\n" +
            "   \"reason\": \"RESOLVE_REASON_MATCH\",\n" +
            "   \"shouldApply\": true\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse = RemoteFlagResolver(
            clientSecret = "",
            region = ConfidenceRegion.EUROPE,
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
        )
            .resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
        val expectedFlags = Flags(
            listOf(
                ResolvedFlag(
                    "test-kotlin-flag-1",
                    "flags/test-kotlin-flag-1/variants/variant-1",
                    mutableMapOf(
                        "mystring" to ConfidenceValue.String("red"),
                        "myboolean" to ConfidenceValue.Boolean(false),
                        "myinteger" to ConfidenceValue.Integer(7),
                        "mydouble" to ConfidenceValue.Double(3.14),
                        "mynull" to ConfidenceValue.Null,
                        "mydate" to ConfidenceValue.String(instant.toString()),
                        "mystruct" to ConfidenceValue.Struct(
                            mapOf(
                                "innerString" to ConfidenceValue.String("innerValue")
                            )
                        )
                    ),
                    ResolveReason.RESOLVE_REASON_MATCH,
                    shouldApply = true
                )
            )
        )
        val expectedParsed = ResolveFlags(
            expectedFlags,
            "token1"
        )
        assertEquals(expectedParsed.resolvedFlags.list, (parsedResponse as Result.Success<FlagResolution>).data.flags)
    }

    @Test
    fun testDeserializeResolveResponseNoApply() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/test-kotlin-flag-1\",\n" +
            "   \"variant\": \"\",\n" +
            "   \"value\": null,\n" +
            "   \"flagSchema\": null,\n" +
            "   \"reason\": \"RESOLVE_REASON_NO_SEGMENT_MATCH\",\n" +
            "   \"shouldApply\": false\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse =
            RemoteFlagResolver(
                clientSecret = "",
                region = ConfidenceRegion.EUROPE,
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher,
                httpClient = OkHttpClient(),
                telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
            ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
        val expectedParsed = ResolveFlags(
            Flags(
                listOf(
                    ResolvedFlag(
                        flag = "test-kotlin-flag-1",
                        variant = "",
                        reason = com.spotify.confidence.ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH,
                        shouldApply = false
                    )
                )
            ),
            "token1"
        )
        assertEquals(expectedParsed.resolvedFlags.list, (parsedResponse as Result.Success<FlagResolution>).data.flags)
        assertEquals(expectedParsed.resolveToken, parsedResponse.data.resolveToken)
    }

    @Test
    fun testDoubleTypeSchemaConversion() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/test-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/test-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {\n" +
            "    \"mydouble\": 3\n" +
            "   },\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"mydouble\": {\n" +
            "      \"doubleSchema\": {}\n" +
            "     }\n" +
            "    }\n" +
            "   },\n" +
            "   \"shouldApply\": true,\n" +
            "   \"reason\": \"RESOLVE_REASON_MATCH\"\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse =
            RemoteFlagResolver(
                clientSecret = "",
                region = ConfidenceRegion.EUROPE,
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher,
                httpClient = OkHttpClient(),
                telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
            )
                .resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
        val expectedParsed = ResolveFlags(
            Flags(
                listOf(
                    ResolvedFlag(
                        "test-kotlin-flag-1",
                        "flags/test-kotlin-flag-1/variants/variant-1",
                        mutableMapOf(
                            "mydouble" to ConfidenceValue.Double(3.0)
                        ),
                        com.spotify.confidence.ResolveReason.RESOLVE_REASON_MATCH,
                        shouldApply = true
                    )
                )
            ),
            "token1"
        )
        assertEquals(expectedParsed.resolvedFlags.list, (parsedResponse as Result.Success<FlagResolution>).data.flags)
    }

    @Test
    fun testWrongValueForSchema() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/test-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/test-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {\n" +
            "    \"myinteger\": 3.1\n" +
            "   },\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"myinteger\": {\n" +
            "      \"intSchema\": {}\n" +
            "     }\n" +
            "    }\n" +
            "   },\n" +
            "   \"reason\": \"RESOLVE_REASON_MATCH\",\n" +
            "   \"shouldApply\": true\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val ex = assertThrows(ParseError::class.java) {
            runTest {
                val testDispatcher = UnconfinedTestDispatcher(testScheduler)
                RemoteFlagResolver(
                    clientSecret = "",
                    region = ConfidenceRegion.EUROPE,
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher,
                    httpClient = OkHttpClient(),
                    telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
                )
                    .resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
            }
        }
        assertEquals("Incompatible value \"myinteger\" for schema", ex.message)
    }

    @Test
    fun testNoSchemaForValue() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/test-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/test-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {\n" +
            "    \"myinteger\": 3\n" +
            "   },\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"anothervalue\": {\n" +
            "      \"intSchema\": {}\n" +
            "     }\n" +
            "    }\n" +
            "   },\n" +
            "   \"reason\": \"RESOLVE_REASON_MATCH\",\n" +
            "   \"shouldApply\": true\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val ex = assertThrows(ParseError::class.java) {
            runTest {
                val testDispatcher = UnconfinedTestDispatcher(testScheduler)
                RemoteFlagResolver(
                    clientSecret = "",
                    region = ConfidenceRegion.EUROPE,
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher,
                    httpClient = OkHttpClient(),
                    telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
                )
                    .resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
            }
        }
        assertEquals("Couldn't find value \"myinteger\" in schema", ex.message)
    }

    fun testDeserializedMalformedResolveResponse() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/test-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/test-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {},\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"myinteger\": {\n" +
            "      \"WRONG-SCHEMA-IDENTIFIED\": {}\n" +
            "     }\n" +
            "    }\n" +
            "   },\n" +
            "   \"reason\": \"RESOLVE_REASON_MATCH\"\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val ex = assertThrows(ParseError::class.java) {
            runTest {
                val testDispatcher = UnconfinedTestDispatcher(testScheduler)
                RemoteFlagResolver(
                    clientSecret = "",
                    region = ConfidenceRegion.EUROPE,
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher,
                    httpClient = OkHttpClient(),
                    telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
                )
                    .resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
            }
        }
        assertEquals("Unrecognized flag schema identifier: [WRONG-SCHEMA-IDENTIFIED]", ex.message)
    }

    @Test
    fun testDeserializedUnexpectedFlagName() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"test-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/test-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {},\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"myinteger\": {\n" +
            "      \"intSchema\": {}\n" +
            "     }\n" +
            "    }\n" +
            "   },\n" +
            "   \"reason\": \"RESOLVE_REASON_MATCH\",\n" +
            "   \"shouldApply\": true\n" +
            "  }\n" +
            " ],\n" +
            " \"resolveToken\": \"token1\"\n" +
            "}\n"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val ex = assertThrows(ParseError::class.java) {
            runTest {
                val testDispatcher = UnconfinedTestDispatcher(testScheduler)
                RemoteFlagResolver(
                    clientSecret = "",
                    region = ConfidenceRegion.EUROPE,
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher,
                    httpClient = OkHttpClient(),
                    telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
                )
                    .resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
            }
        }
        assertEquals("Unexpected flag name in resolve flag data: test-kotlin-flag-1", ex.message)
    }

    @Test
    fun testIntSchemaWithWholeNumberDouble() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = """
        {
          "resolvedFlags": [
            {
              "flag": "flags/test-flag",
              "variant": "flags/test-flag/variants/variant-1",
              "value": {
                "myinteger": 400.0
              },
              "flagSchema": {
                "schema": {
                  "myinteger": {
                    "intSchema": {}
                  }
                }
              },
              "reason": "RESOLVE_REASON_MATCH",
              "shouldApply": true
            }
          ],
          "resolveToken": "token1"
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse = RemoteFlagResolver(
            clientSecret = "",
            region = ConfidenceRegion.EUROPE,
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
        ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
        val flags = (parsedResponse as Result.Success<FlagResolution>).data.flags
        assertEquals(ConfidenceValue.Integer(400), flags[0].value["myinteger"])
    }

    @Test
    fun testIntSchemaWithNegativeWholeNumberDouble() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = """
        {
          "resolvedFlags": [
            {
              "flag": "flags/test-flag",
              "variant": "flags/test-flag/variants/variant-1",
              "value": {
                "myinteger": -5.0
              },
              "flagSchema": {
                "schema": {
                  "myinteger": {
                    "intSchema": {}
                  }
                }
              },
              "reason": "RESOLVE_REASON_MATCH",
              "shouldApply": true
            }
          ],
          "resolveToken": "token1"
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse = RemoteFlagResolver(
            clientSecret = "",
            region = ConfidenceRegion.EUROPE,
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
        ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
        val flags = (parsedResponse as Result.Success<FlagResolution>).data.flags
        assertEquals(ConfidenceValue.Integer(-5), flags[0].value["myinteger"])
    }

    @Test
    fun testIntSchemaWithZeroDouble() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = """
        {
          "resolvedFlags": [
            {
              "flag": "flags/test-flag",
              "variant": "flags/test-flag/variants/variant-1",
              "value": {
                "myinteger": 0.0
              },
              "flagSchema": {
                "schema": {
                  "myinteger": {
                    "intSchema": {}
                  }
                }
              },
              "reason": "RESOLVE_REASON_MATCH",
              "shouldApply": true
            }
          ],
          "resolveToken": "token1"
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse = RemoteFlagResolver(
            clientSecret = "",
            region = ConfidenceRegion.EUROPE,
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
        ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))
        val flags = (parsedResponse as Result.Success<FlagResolution>).data.flags
        assertEquals(ConfidenceValue.Integer(0), flags[0].value["myinteger"])
    }

    @Test
    fun testAllSchemaTypesWithMixedValues() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = """
        {
          "resolvedFlags": [
            {
              "flag": "flags/test-flag",
              "variant": "flags/test-flag/variants/variant-1",
              "value": {
                "my_string": "hello",
                "my_bool_true": true,
                "my_bool_false": false,
                "my_int": 42,
                "my_int_as_double": 100.0,
                "my_double": 3.14,
                "my_double_whole": 7.0,
                "my_null_string": null,
                "my_null_int": null,
                "my_struct": {
                  "nested_str": "inner",
                  "nested_int": 10.0
                }
              },
              "flagSchema": {
                "schema": {
                  "my_string": { "stringSchema": {} },
                  "my_bool_true": { "boolSchema": {} },
                  "my_bool_false": { "boolSchema": {} },
                  "my_int": { "intSchema": {} },
                  "my_int_as_double": { "intSchema": {} },
                  "my_double": { "doubleSchema": {} },
                  "my_double_whole": { "doubleSchema": {} },
                  "my_null_string": { "stringSchema": {} },
                  "my_null_int": { "intSchema": {} },
                  "my_struct": {
                    "structSchema": {
                      "schema": {
                        "nested_str": { "stringSchema": {} },
                        "nested_int": { "intSchema": {} }
                      }
                    }
                  }
                }
              },
              "reason": "RESOLVE_REASON_MATCH",
              "shouldApply": true
            }
          ],
          "resolveToken": "token1"
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonPayload)
        )
        val parsedResponse = RemoteFlagResolver(
            clientSecret = "",
            region = ConfidenceRegion.EUROPE,
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = Telemetry("", Telemetry.Library.CONFIDENCE, "")
        ).resolve(listOf(), mapOf("targeting_key" to ConfidenceValue.String("user1")))

        val values = (parsedResponse as Result.Success<FlagResolution>).data.flags[0].value
        assertEquals(ConfidenceValue.String("hello"), values["my_string"])
        assertEquals(ConfidenceValue.Boolean(true), values["my_bool_true"])
        assertEquals(ConfidenceValue.Boolean(false), values["my_bool_false"])
        assertEquals(ConfidenceValue.Integer(42), values["my_int"])
        assertEquals(ConfidenceValue.Integer(100), values["my_int_as_double"])
        assertEquals(ConfidenceValue.Double(3.14), values["my_double"])
        assertEquals(ConfidenceValue.Double(7.0), values["my_double_whole"])
        assertEquals(ConfidenceValue.Null, values["my_null_string"])
        assertEquals(ConfidenceValue.Null, values["my_null_int"])

        val struct = values["my_struct"] as ConfidenceValue.Struct
        assertEquals(ConfidenceValue.String("inner"), struct.map["nested_str"])
        assertEquals(ConfidenceValue.Integer(10), struct.map["nested_int"])
    }

    @Test
    fun testSerializeResolveRequest() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val date = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val expectedSerializedRequest = "{\n" +
                    "  \"flags\": [\n" +
                    "    \"flags/flag1\",\n" +
                    "    \"flags/flag2\"\n" +
                    "  ],\n" +
                    "  \"evaluationContext\": {\n" +
                    "    \"targeting_key\": \"user1\",\n" +
                    "    \"myboolean\": true,\n" +
                    "    \"mystring\": \"test\",\n" +
                    "    \"myinteger\": 7,\n" +
                    "    \"mydouble\": 3.14,\n" +
                    "    \"mydate\": \"2023-03-01T14:01:46.123Z\",\n" +
                    "    \"mynull\": null,\n" +
                    "    \"mylist\": [\n" +
                    "      \"innerList\",\n" +
                    "      \"innerList_Second_Item\"\n" +
                    "    ],\n" +
                    "    \"mystructure\": {\n" +
                    "      \"myinnerString\": \"value\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"clientSecret\": \"secret1\",\n" +
                    "  \"apply\": false,\n" +
                    "  \"sdk\": {\n" +
                    "    \"id\": \"SDK_ID_KOTLIN_PROVIDER_TEST\",\n" +
                    "    \"version\": \"\"\n" +
                    "  }\n" +
                    "}"
                assertEquals(
                    expectedSerializedRequest.replace("\\s".toRegex(), ""),
                    request.body.copy().readUtf8().replace("\\s".toRegex(), "")
                )
                return MockResponse()
                    .setBody(
                        "{\n" +
                            " \"resolvedFlags\": [],\n" +
                            " \"resolveToken\": \"token1\"\n" +
                            "}"
                    )
                    .setResponseCode(200)
            }
        }

        RemoteFlagResolver(
            clientSecret = "secret1",
            region = ConfidenceRegion.EUROPE,
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher,
            httpClient = OkHttpClient(),
            telemetry = Telemetry(
                "SDK_ID_KOTLIN_PROVIDER_TEST",
                Telemetry.Library.CONFIDENCE,
                ""
            )
        )
            .resolve(
                listOf("flag1", "flag2"),
                mapOf(
                    "targeting_key" to ConfidenceValue.String("user1"),
                    "myboolean" to ConfidenceValue.Boolean(true),
                    "mystring" to ConfidenceValue.String("test"),
                    "myinteger" to ConfidenceValue.Integer(7),
                    "mydouble" to ConfidenceValue.Double(3.14),
                    "mydate" to ConfidenceValue.Timestamp(date),
                    "mynull" to ConfidenceValue.Null,
                    "mylist" to ConfidenceValue.List(
                        listOf(
                            ConfidenceValue.String("innerList"),
                            ConfidenceValue.String("innerList_Second_Item")
                        )
                    ),
                    "mystructure" to ConfidenceValue.Struct(mapOf("myinnerString" to ConfidenceValue.String("value")))
                )
            )
    }

    @Test
    fun testSerializeApplyRequest() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val applyDate = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val sendDate = Date.from(Instant.parse("2023-03-01T14:03:46.124Z"))
        val mockClock: Clock = mock()

        whenever(mockClock.currentTime()).thenReturn(sendDate)
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val expectedSerializedRequest = "{\n" +
                    "  \"flags\": [\n" +
                    "    {\n" +
                    "      \"flag\": \"flags/flag1\",\n" +
                    "      \"applyTime\": \"2023-03-01T14:01:46.123Z\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"sendTime\": \"2023-03-01T14:03:46.124Z\",\n" +
                    "  \"clientSecret\": \"secret1\",\n" +
                    "  \"resolveToken\": \"token1\",\n" +
                    "  \"sdk\": {\n" +
                    "    \"id\": \"SDK_ID_KOTLIN_CONFIDENCE_TEST\",\n" +
                    "    \"version\": \"\"\n" +
                    "  }\n" +
                    "}"
                assertEquals(
                    expectedSerializedRequest.replace("\\s".toRegex(), ""),
                    request.body.readUtf8().replace("\\s".toRegex(), "")
                )

                return MockResponse().setResponseCode(200)
            }
        }
        FlagApplierClientImpl(
            "secret1",
            Telemetry(SDK_ID + "_TEST", Telemetry.Library.CONFIDENCE, ""),
            mockWebServer.url("/v1/flags:apply"),
            mockClock,
            dispatcher = testDispatcher
        )
            .apply(listOf(AppliedFlag("flag1", applyDate)), "token1")
    }

    @Test
    fun testApplyRequestIncludesTelemetryHeader() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val applyDate = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val sendDate = Date.from(Instant.parse("2023-03-01T14:03:46.124Z"))
        val mockClock: Clock = mock()
        whenever(mockClock.currentTime()).thenReturn(sendDate)

        val telemetry = Telemetry(SDK_ID + "_TEST", Telemetry.Library.CONFIDENCE, "1.0.0")
        // Pre-populate telemetry so the header will be present
        telemetry.trackEvaluation(
            Telemetry.EvaluationReason.TARGETING_MATCH,
            Telemetry.EvaluationErrorCode.UNSPECIFIED
        )

        var recordedRequest: RecordedRequest? = null
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedRequest = request
                return MockResponse().setResponseCode(200)
            }
        }

        FlagApplierClientImpl(
            "secret1",
            telemetry,
            mockWebServer.url("/v1/flags:apply"),
            mockClock,
            dispatcher = testDispatcher
        ).apply(listOf(AppliedFlag("flag1", applyDate)), "token1")

        val header = recordedRequest?.getHeader(Telemetry.HEADER_NAME)
        assertTrue(
            "Expected ${Telemetry.HEADER_NAME} header on apply request",
            header != null && header.isNotEmpty()
        )
    }

    @Test
    fun testApplyRequestOmitsHeaderWhenNoTelemetry() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val applyDate = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val sendDate = Date.from(Instant.parse("2023-03-01T14:03:46.124Z"))
        val mockClock: Clock = mock()
        whenever(mockClock.currentTime()).thenReturn(sendDate)

        val telemetry = Telemetry(SDK_ID + "_TEST", Telemetry.Library.CONFIDENCE, "1.0.0")

        var recordedRequest: RecordedRequest? = null
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedRequest = request
                return MockResponse().setResponseCode(200)
            }
        }

        FlagApplierClientImpl(
            "secret1",
            telemetry,
            mockWebServer.url("/v1/flags:apply"),
            mockClock,
            dispatcher = testDispatcher
        ).apply(listOf(AppliedFlag("flag1", applyDate)), "token1")

        assertTrue(
            "No telemetry header expected when no events tracked",
            recordedRequest?.getHeader(Telemetry.HEADER_NAME) == null
        )
    }

    @Test
    fun testApplyReturnsSuccessfulAfter200() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val applyDate = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val sendDate = Date.from(Instant.parse("2023-03-01T14:03:46.124Z"))
        val mockClock: Clock = mock()

        whenever(mockClock.currentTime()).thenReturn(sendDate)
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(200)
            }
        }
        val result = FlagApplierClientImpl(
            "secret1",
            testTelemetry,
            mockWebServer.url("/v1/flags:apply"),
            mockClock,
            dispatcher = testDispatcher
        )
            .apply(listOf(AppliedFlag("flag1", applyDate)), "token1")

        assertEquals(Result.Success(Unit), result)
    }

    @Test
    fun testApplyReturnsFailureAfter500() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val applyDate = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val sendDate = Date.from(Instant.parse("2023-03-01T14:03:46.124Z"))
        val mockClock: Clock = mock()

        whenever(mockClock.currentTime()).thenReturn(sendDate)
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(500)
            }
        }
        val result = FlagApplierClientImpl(
            "secret1",
            testTelemetry,
            mockWebServer.url("/v1/flags:apply"),
            mockClock,
            dispatcher = testDispatcher
        )
            .apply(listOf(AppliedFlag("flag1", applyDate)), "token1")

        assertTrue(result is Result.Failure)
    }
}
