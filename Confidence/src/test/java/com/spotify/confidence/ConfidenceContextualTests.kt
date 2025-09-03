package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files

class ConfidenceContextualTests {

    private val mockContext: Context = mock()

    @get:Rule
    var tmpFile = TemporaryFolder()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(Files.createTempDirectory("tmpTests").toFile())
        whenever(mockContext.getDir(any(), any())).thenReturn(Files.createTempDirectory("events").toFile())
        whenever(mockContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(InMemorySharedPreferences())
    }

    @Test
    fun created_confidence_respects_custom_visitor_id_key() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidence = ConfidenceFactory.create(
            mockContext,
            "clientSecret",
            dispatcher = testDispatcher,
            visitorIdContextKey = "custom_visitor_id"
        )
        val context = confidence.getContext()
        Assert.assertNotNull(context)
        Assert.assertTrue(context.containsKey("custom_visitor_id"))
        Assert.assertFalse(context.containsKey(VISITOR_ID_CONTEXT_KEY))
    }

    @Test
    fun created_confidence_respects_pre_set_visitor_id() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val confidence = ConfidenceFactory.create(
            mockContext,
            "clientSecret",
            dispatcher = testDispatcher,
            initialContext = mapOf(VISITOR_ID_CONTEXT_KEY to ConfidenceValue.String("my_visitor_id"))
        )
        val context = confidence.getContext()
        Assert.assertNotNull(context)
        Assert.assertTrue(context.containsKey(VISITOR_ID_CONTEXT_KEY))
        Assert.assertEquals("my_visitor_id", context[VISITOR_ID_CONTEXT_KEY].toString())
    }

    @Test
    fun test_forking_context_works() {
        val debugLogger = DebugLoggerFake()
        val confidence = Confidence(
            "",
            Dispatchers.IO,
            mock(),
            mock(),
            mock(),
            mock(),
            mapOf(),
            mock(),
            mock(),
            ConfidenceRegion.EUROPE,
            debugLogger
        )

        val mutableMap = mutableMapOf<String, ConfidenceValue>()
        mutableMap["screen"] = ConfidenceValue.String("value")
        mutableMap["hello"] = ConfidenceValue.Boolean(false)
        mutableMap["NN"] = ConfidenceValue.Double(20.0)
        mutableMap["my_struct"] = ConfidenceValue.Struct(mapOf("x" to ConfidenceValue.Double(2.0)))
        confidence.putContext(mutableMap)
        val eventSender = confidence.withContext(mapOf("my_value" to ConfidenceValue.String("my value")))
        Assert.assertEquals(mutableMap, confidence.getContext())
        Assert.assertTrue(mutableMap.all { eventSender.getContext().containsKey(it.key) })
        Assert.assertTrue(eventSender.getContext().containsKey("my_value"))
        Assert.assertTrue(eventSender.getContext()["my_value"] == ConfidenceValue.String("my value"))
    }

    @Test
    fun removing_context_will_skip_the_context_coming_from_parent() {
        val debugLogger = DebugLoggerFake()
        val confidence = Confidence(
            "",
            Dispatchers.IO,
            mock(),
            mock(),
            mock(),
            mock(),
            mapOf(),
            mock(),
            mock(),
            ConfidenceRegion.EUROPE,
            debugLogger
        )

        val mutableMap = mutableMapOf<String, ConfidenceValue>()
        mutableMap["screen"] = ConfidenceValue.String("value")
        mutableMap["hello"] = ConfidenceValue.Boolean(false)
        mutableMap["NN"] = ConfidenceValue.Double(20.0)
        mutableMap["my_struct"] = ConfidenceValue.Struct(mapOf("x" to ConfidenceValue.Double(2.0)))
        confidence.putContext(mutableMap)
        val eventSender = confidence.withContext(mapOf("my_value" to ConfidenceValue.String("my value")))
        Assert.assertEquals(mutableMap, confidence.getContext())
        Assert.assertTrue(mutableMap.all { eventSender.getContext().containsKey(it.key) })
        Assert.assertTrue(eventSender.getContext().containsKey("my_value"))
        Assert.assertTrue(eventSender.getContext()["my_value"] == ConfidenceValue.String("my value"))

        // remove the screen
        Assert.assertTrue(eventSender.getContext().containsKey("screen"))
        eventSender.removeContext("screen")
        Assert.assertTrue(!eventSender.getContext().containsKey("screen"))
    }
}