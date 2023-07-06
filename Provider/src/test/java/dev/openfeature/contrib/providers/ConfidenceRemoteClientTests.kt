@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.openfeature.contrib.providers

import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.Clock
import dev.openfeature.contrib.providers.client.ConfidenceRemoteClient
import dev.openfeature.contrib.providers.client.Flags
import dev.openfeature.contrib.providers.client.ResolveFlags
import dev.openfeature.contrib.providers.client.ResolveReason
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Date

internal class ConfidenceRemoteClientTests {
    private val mockWebServer = MockWebServer()

    @Before
    fun setup() {
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testDeserializeResolveResponse() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val instant = Date.from(Instant.parse("2023-03-01T14:01:46.123Z"))
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/fdema-kotlin-flag-1/variants/variant-1\",\n" +
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
        val parsedResponse = ConfidenceRemoteClient(
            baseUrl = mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher
        )
            .resolve(listOf(), MutableContext("user1"))
        val expectedFlags = Flags(
            listOf(
                ResolvedFlag(
                    "fdema-kotlin-flag-1",
                    "flags/fdema-kotlin-flag-1/variants/variant-1",
                    MutableStructure(
                        mutableMapOf(
                            "mystring" to Value.String("red"),
                            "myboolean" to Value.Boolean(false),
                            "myinteger" to Value.Integer(7),
                            "mydouble" to Value.Double(3.14),
                            "mynull" to Value.Null,
                            "mydate" to Value.String(instant.toString()),
                            "mystruct" to Value.Structure(
                                mapOf(
                                    "innerString" to Value.String("innerValue")
                                )
                            )
                        )
                    ),
                    ResolveReason.RESOLVE_REASON_MATCH
                )
            )
        )
        val expectedParsed = ResolveFlags(
            expectedFlags,
            "token1"
        )
        assertEquals(expectedParsed, parsedResponse)
    }

    @Test
    fun testDeserializeResolveResponseNoMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"\",\n" +
            "   \"value\": null,\n" +
            "   \"flagSchema\": null,\n" +
            "   \"reason\": \"RESOLVE_REASON_NO_SEGMENT_MATCH\"\n" +
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
            ConfidenceRemoteClient(
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher
            )
                .resolve(listOf(), MutableContext("user1"))
        val expectedParsed = ResolveFlags(
            Flags(
                listOf(
                    ResolvedFlag(
                        flag = "fdema-kotlin-flag-1",
                        variant = "",
                        reason = ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH
                    )
                )
            ),
            "token1"
        )
        assertEquals(expectedParsed, parsedResponse)
    }

    @Test
    fun testDoubleTypeSchemaConversion() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/fdema-kotlin-flag-1/variants/variant-1\",\n" +
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
            ConfidenceRemoteClient(
                baseUrl = mockWebServer.url("/v1/flags:resolve"),
                dispatcher = testDispatcher
            )
                .resolve(listOf(), MutableContext("user1"))
        val expectedParsed = ResolveFlags(
            Flags(
                listOf(
                    ResolvedFlag(
                        "fdema-kotlin-flag-1",
                        "flags/fdema-kotlin-flag-1/variants/variant-1",
                        MutableStructure(
                            mutableMapOf(
                                "mydouble" to Value.Double(3.0)
                            )
                        ),
                        ResolveReason.RESOLVE_REASON_MATCH
                    )
                )
            ),
            "token1"
        )
        assertEquals(expectedParsed, parsedResponse)
    }

    @Test
    fun testWrongValueForSchema() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/fdema-kotlin-flag-1/variants/variant-1\",\n" +
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
                ConfidenceRemoteClient(
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher
                )
                    .resolve(listOf(), MutableContext("user1"))
            }
        }
        assertEquals("Incompatible value \"myinteger\" for schema", ex.message)
    }

    @Test
    fun testNoSchemaForValue() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/fdema-kotlin-flag-1/variants/variant-1\",\n" +
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
                ConfidenceRemoteClient(
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher
                )
                    .resolve(listOf(), MutableContext("user1"))
            }
        }
        assertEquals("Couldn't find value \"myinteger\" in schema", ex.message)
    }

    fun testDeserializedMalformedResolveResponse() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"flags/fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/fdema-kotlin-flag-1/variants/variant-1\",\n" +
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
                ConfidenceRemoteClient(
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher
                )
                    .resolve(listOf(), MutableContext("user1"))
            }
        }
        assertEquals("Unrecognized flag schema identifier: [WRONG-SCHEMA-IDENTIFIED]", ex.message)
    }

    @Test
    fun testDeserializedUnexpectedFlagName() {
        val jsonPayload = "{\n" +
            " \"resolvedFlags\": [\n" +
            "  {\n" +
            "   \"flag\": \"fdema-kotlin-flag-1\",\n" +
            "   \"variant\": \"flags/fdema-kotlin-flag-1/variants/variant-1\",\n" +
            "   \"value\": {},\n" +
            "   \"flagSchema\": {\n" +
            "    \"schema\": {\n" +
            "     \"myinteger\": {\n" +
            "      \"intSchema\": {}\n" +
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
                ConfidenceRemoteClient(
                    baseUrl = mockWebServer.url("/v1/flags:resolve"),
                    dispatcher = testDispatcher
                )
                    .resolve(listOf(), MutableContext("user1"))
            }
        }
        assertEquals("Unexpected flag name in resolve flag data: fdema-kotlin-flag-1", ex.message)
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
                    "      true,\n" +
                    "      \"innerList\"\n" +
                    "    ],\n" +
                    "    \"mystructure\": {\n" +
                    "      \"myinnerString\": \"value\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"clientSecret\": \"secret1\",\n" +
                    "  \"apply\": false\n" +
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

        ConfidenceRemoteClient(
            "secret1",
            mockWebServer.url("/v1/flags:resolve"),
            dispatcher = testDispatcher
        )
            .resolve(
                listOf("flag1", "flag2"),
                MutableContext(
                    "user1",
                    mutableMapOf(
                        "myboolean" to Value.Boolean(true),
                        "mystring" to Value.String("test"),
                        "myinteger" to Value.Integer(7),
                        "mydouble" to Value.Double(3.14),
                        "mydate" to Value.Date(date),
                        "mynull" to Value.Null,
                        "mylist" to Value.List(
                            listOf(
                                Value.Boolean(true),
                                Value.String("innerList")
                            )
                        ),
                        "mystructure" to Value.Structure(mapOf("myinnerString" to Value.String("value")))
                    )
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
                    "  \"resolveToken\": \"token1\"\n" +
                    "}"
                assertEquals(
                    expectedSerializedRequest.replace("\\s".toRegex(), ""),
                    request.body.readUtf8().replace("\\s".toRegex(), "")
                )

                return MockResponse().setResponseCode(200)
            }
        }
        ConfidenceRemoteClient(
            "secret1",
            mockWebServer.url("/v1/flags:apply"),
            mockClock,
            dispatcher = testDispatcher
        )
            .apply(listOf(AppliedFlag("flag1", applyDate)), "token1")
    }
}