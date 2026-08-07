package com.spotify.confidence.openfeature

import com.spotify.confidence.ConfidenceValue
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenFeatureTrackMapperTest {
    @Test
    fun mergeEventContextUsesOpenFeatureValuesOnConflict() {
        val merged = mergeEventContext(
            sessionContext = mapOf(
                "plan" to ConfidenceValue.String("free"),
                "visitor_id" to ConfidenceValue.String("v1")
            ),
            openFeatureContext = mapOf(
                "plan" to ConfidenceValue.String("premium"),
                "country" to ConfidenceValue.String("SE")
            )
        )
        assertEquals(ConfidenceValue.String("premium"), merged["plan"])
        assertEquals(ConfidenceValue.String("v1"), merged["visitor_id"])
        assertEquals(ConfidenceValue.String("SE"), merged["country"])
    }

    @Test
    fun trackingDetailsValueAttributeOverridesNumericValue() {
        val details = TrackingEventDetails(
            99.77,
            ImmutableStructure("value" to Value.String("override"))
        )
        val data = details.toTrackingData()
        assertEquals(ConfidenceValue.String("override"), data["value"])
    }

    @Test
    fun trackContextMapIncludesTargetingKey() {
        val context = ImmutableContext(
            targetingKey = "user-1",
            attributes = mapOf("country" to Value.String("SE"))
        )
        val map = context.toTrackContextMap()
        assertEquals(ConfidenceValue.String("user-1"), map["targeting_key"])
        assertEquals(ConfidenceValue.String("SE"), map["country"])
    }
}
