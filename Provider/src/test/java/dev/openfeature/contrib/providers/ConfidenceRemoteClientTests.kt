package dev.openfeature.contrib.providers

import dev.openfeature.contrib.providers.client.*
import dev.openfeature.sdk.MutableContext
import dev.openfeature.sdk.MutableStructure
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.OpenFeatureError.ParseError
import junit.framework.TestCase.assertEquals
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
import java.time.Clock
import java.time.Instant
import java.util.*

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
    fun testDeserializeResolveResponse() {
        val instant = Instant.parse("2023-03-01T14:01:46Z")
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val parsedResponse = ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
            .resolve(listOf(), MutableContext("user1"))
        val expectedParsed = ResolveFlagsResponse(
            listOf(
                ResolvedFlag(
                    "fdema-kotlin-flag-1",
                    "flags/fdema-kotlin-flag-1/variants/variant-1",
                    MutableStructure(mutableMapOf(
                        "mystring" to Value.String("red"),
                        "myboolean" to Value.Boolean(false),
                        "myinteger" to Value.Integer( 7),
                        "mydouble" to Value.Double(3.14),
                        "mynull" to Value.Null,
                        "mydate" to Value.String(instant.toString()),
                        "mystruct" to Value.Structure(mapOf(
                            "innerString" to Value.String("innerValue")
                        ))
                    )),
                    SchemaType.SchemaStruct(mapOf(
                        "mystring" to SchemaType.StringSchema,
                        "myboolean" to SchemaType.BoolSchema,
                        "myinteger" to SchemaType.IntSchema,
                        "mydouble" to SchemaType.DoubleSchema,
                        "mynull" to SchemaType.DoubleSchema, // Arbitrary schema type here
                        "mydate" to SchemaType.StringSchema,
                        "mystruct" to SchemaType.SchemaStruct(mapOf(
                            "innerString" to SchemaType.StringSchema
                        ))
                    )),
                    ResolveReason.RESOLVE_REASON_MATCH
                )
            ),
            "token1"
        )
        assertEquals(expectedParsed, parsedResponse)
    }

    @Test
    fun testDeserializeResolveResponseNoMatch() {
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val parsedResponse = ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
            .resolve(listOf(), MutableContext("user1"))
        val expectedParsed = ResolveFlagsResponse(
            listOf(
                ResolvedFlag(
                    "fdema-kotlin-flag-1",
                    "",
                    null,
                    null,
                    ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH
                )
            ),
            "token1"
        )
        assertEquals(expectedParsed, parsedResponse)
    }

    @Test
    fun testDoubleTypeSchemaConversion() {
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val parsedResponse = ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
            .resolve(listOf(), MutableContext("user1"))
        val expectedParsed = ResolveFlagsResponse(
            listOf(
                ResolvedFlag(
                    "fdema-kotlin-flag-1",
                    "flags/fdema-kotlin-flag-1/variants/variant-1",
                    MutableStructure(mutableMapOf(
                        "mydouble" to Value.Double(3.0),
                    )),
                    SchemaType.SchemaStruct(mapOf(
                        "mydouble" to SchemaType.DoubleSchema,
                    )),
                    ResolveReason.RESOLVE_REASON_MATCH
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val ex = assertThrows(ParseError::class.java) {
            ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
                .resolve(listOf(), MutableContext("user1"))
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val ex = assertThrows(ParseError::class.java) {
            ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
                .resolve(listOf(), MutableContext("user1"))
        }
        assertEquals("Couldn't find value \"myinteger\" in schema", ex.message)
    }

    @Test
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val ex = assertThrows(ParseError::class.java) {
            ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
                .resolve(listOf(), MutableContext("user1"))
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

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(jsonPayload))
        val ex = assertThrows(ParseError::class.java) {
            ConfidenceRemoteClient(baseUrl = mockWebServer.url("/v1/flags:resolve"))
                .resolve(listOf(), MutableContext("user1"))
        }
        assertEquals("Unexpected flag name in resolve flag data: fdema-kotlin-flag-1", ex.message)
    }

    @Test
    fun testSerializeResolveRequest() {
        val instant = Instant.parse("2023-03-01T14:01:46Z")
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
                        "    \"mydate\": \"2023-03-01T14:01:46Z\",\n" +
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
                assertEquals(expectedSerializedRequest, request.body.copy().readUtf8())
                return MockResponse()
                    .setBody("{\n" +
                            " \"resolvedFlags\": [],\n" +
                            " \"resolveToken\": \"token1\"\n" +
                            "}")
                    .setResponseCode(200)
            }
        }

        ConfidenceRemoteClient("secret1", mockWebServer.url("/v1/flags:resolve"))
            .resolve(
                listOf("flag1", "flag2"),
                MutableContext("user1",
                    mutableMapOf(
                        "myboolean" to Value.Boolean(true),
                        "mystring" to Value.String("test"),
                        "myinteger" to Value.Integer(7),
                        "mydouble" to Value.Double(3.14),
                        "mydate" to Value.Instant(instant),
                        "mynull" to Value.Null,
                        "mylist" to Value.List(listOf(Value.Boolean(true), Value.String("innerList"))),
                        "mystructure" to Value.Structure(mapOf("myinnerString" to Value.String("value"))))))

    }

    @Test
    fun testSerializeApplyRequest() {
        val applyDate = Date.from(Instant.parse("2023-03-01T14:01:46Z"))
        val sendDate = Date.from(Instant.parse("2023-03-01T14:03:46Z"))
        val mockClock: Clock = mock()

        whenever(mockClock.instant()).thenReturn(sendDate.toInstant())
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val expectedSerializedRequest = "{\n" +
                        "  \"flags\": [\n" +
                        "    {\n" +
                        "      \"flag\": \"flags/flag1\",\n" +
                        "      \"applyTime\": \"2023-03-01T14:01:46Z\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"sendTime\": \"2023-03-01T14:03:46Z\",\n" +
                        "  \"clientSecret\": \"secret1\",\n" +
                        "  \"resolveToken\": \"token1\"\n" +
                        "}"
                assertEquals(expectedSerializedRequest, request.body.readUtf8())

                return MockResponse().setResponseCode(200)
            }
        }
        ConfidenceRemoteClient("secret1", mockWebServer.url("/v1/flags:apply"), mockClock)
            .apply(listOf(AppliedFlag("flag1", applyDate.toInstant())), "token1")
    }
}