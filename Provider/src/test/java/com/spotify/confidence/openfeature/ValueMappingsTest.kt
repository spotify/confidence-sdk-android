package com.spotify.confidence.openfeature

import com.spotify.confidence.ConfidenceValue
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ValueMappingsTest {

    @Test
    fun openFeatureStringValueToConfidenceValueString() {
        val value = Value.String("test")
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals("test", confidenceValue.asString()?.string)
    }

    @Test
    fun openFeatureDoubleValueToConfidenceValueDouble() {
        val value = Value.Double(1.23)
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals(1.23, confidenceValue.asDouble()?.double!!, 0.001)
    }

    @Test
    fun openFeatureBooleanValueToConfidenceValueBoolean() {
        val value = Value.Boolean(true)
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals(true, confidenceValue.asBoolean()?.boolean)
    }

    @Test
    fun openFeatureIntegerValueToConfidenceValueInteger() {
        val value = Value.Integer(42)
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals(42, confidenceValue.asInteger()?.integer)
    }

    @Test
    fun openFeatureLongValueToConfidenceValue() {
        assertEquals(ConfidenceValue.Integer(42), Value.Long(42).toConfidenceValue())
        assertEquals(ConfidenceValue.Double(3_000_000_000.0), Value.Long(3_000_000_000).toConfidenceValue())
        assertThrows(OpenFeatureError.ParseError::class.java) {
            Value.Long(9_007_199_254_740_993L).toConfidenceValue()
        }
    }

    @Test
    fun openFeatureStructureValueToConfidenceValueStruct() {
        val value = Value.Structure(mapOf("key" to Value.String("value")))
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals("value", confidenceValue.asStructure()?.map?.get("key")?.asString()?.string)
    }

    @Test
    fun openFeatureListValueToConfidenceValueList() {
        val value = Value.List(listOf(Value.String("value")))
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals("value", confidenceValue.asList()?.list?.get(0)?.asString()?.string)
    }

    @Test
    fun mixedListShouldReturnEmptyConfidenceList() {
        val value = Value.List(listOf(Value.String("value"), Value.Integer(42)))
        val confidenceValue: ConfidenceValue = value.toConfidenceValue()
        assertEquals(0, confidenceValue.asList()?.list?.size)
    }

    private fun ConfidenceValue.asString(): ConfidenceValue.String? {
        return this as? ConfidenceValue.String
    }

    private fun ConfidenceValue.asDouble(): ConfidenceValue.Double? {
        return this as? ConfidenceValue.Double
    }

    private fun ConfidenceValue.asBoolean(): ConfidenceValue.Boolean? {
        return this as? ConfidenceValue.Boolean
    }

    private fun ConfidenceValue.asInteger(): ConfidenceValue.Integer? {
        return this as? ConfidenceValue.Integer
    }

    private fun ConfidenceValue.asStructure(): ConfidenceValue.Struct? {
        return this as? ConfidenceValue.Struct
    }

    private fun ConfidenceValue.asList(): ConfidenceValue.List? {
        return this as? ConfidenceValue.List
    }
}
