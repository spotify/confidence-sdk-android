package com.spotify.confidence

import kotlinx.coroutines.Dispatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock

class ConfidenceContextualTests {
    @Test
    fun test_forking_context_works() {
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
            ConfidenceRegion.EUROPE
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
            ConfidenceRegion.EUROPE
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