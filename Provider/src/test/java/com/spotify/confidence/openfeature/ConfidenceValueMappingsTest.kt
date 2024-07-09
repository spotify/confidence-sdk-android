package com.spotify.confidence.openfeature

import com.spotify.confidence.ConfidenceValue
import dev.openfeature.sdk.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfidenceValueMappingsTest {

    @Test
    fun confidenceStringToValueString() {
        val confidenceValue = ConfidenceValue.String("test")
        val value = confidenceValue.toValue()
        assertEquals("test", value.asString())
        assertNull(value.asInteger())
        assertNull(value.asDate())
        assertNull(value.asBoolean())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceDoubleToValueDouble() {
        val confidenceValue = ConfidenceValue.Double(1.23)
        val value = confidenceValue.toValue()
        assertEquals(1.23, value.asDouble()!!, 0.001)
        assertNull(value.asString())
        assertNull(value.asInteger())
        assertNull(value.asDate())
        assertNull(value.asBoolean())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceBooleanToValueBoolean() {
        val confidenceValue = ConfidenceValue.Boolean(true)
        val value = confidenceValue.toValue()
        assertEquals(true, value.asBoolean())
        assertNull(value.asString())
        assertNull(value.asDouble())
        assertNull(value.asDate())
        assertNull(value.asInteger())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceIntegerToValueInteger() {
        val confidenceValue = ConfidenceValue.Integer(42)
        val value = confidenceValue.toValue()
        assertEquals(42, value.asInteger())
        assertNull(value.asString())
        assertNull(value.asDouble())
        assertNull(value.asDate())
        assertNull(value.asBoolean())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceStructToValueStructure() {
        val confidenceValue = ConfidenceValue.Struct(mapOf("key" to ConfidenceValue.String("value")))
        val value = confidenceValue.toValue()
        assertEquals(mapOf("key" to dev.openfeature.sdk.Value.String("value")), value.asStructure())
        assertNull(value.asString())
        assertNull(value.asDouble())
        assertNull(value.asDate())
        assertNull(value.asBoolean())
        assertNull(value.asList())
    }

    @Test
    fun confidenceListToValueList() {
        val confidenceValue = ConfidenceValue.List(listOf(ConfidenceValue.String("item")))
        val value = confidenceValue.toValue()
        assertEquals(listOf(dev.openfeature.sdk.Value.String("item")), value.asList())
        assertNull(value.asString())
        assertNull(value.asDouble())
        assertNull(value.asDate())
        assertNull(value.asBoolean())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceDateToValueDate() {
        val date = java.util.Date()
        val confidenceValue = ConfidenceValue.Date(date)
        val value = confidenceValue.toValue()
        assertEquals(date, value.asDate())
        assertNull(value.asString())
        assertNull(value.asDouble())
        assertNull(value.asBoolean())
        assertNull(value.asInteger())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceTimestampToValueTimestamp() {
        val timestamp = java.util.Date()
        val confidenceValue = ConfidenceValue.Timestamp(timestamp)
        val value = confidenceValue.toValue()
        assertEquals(timestamp.time, value.asDate()!!.time)
        assertNull(value.asString())
        assertNull(value.asDouble())
        assertNull(value.asBoolean())
        assertNull(value.asInteger())
        assertNull(value.asStructure())
    }

    @Test
    fun confidenceNullToValueNull() {
        val confidenceValue = ConfidenceValue.Null
        val value = confidenceValue.toValue()
        assertEquals(Value.Null, value)
    }
}