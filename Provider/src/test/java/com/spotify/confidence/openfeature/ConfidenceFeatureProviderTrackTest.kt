package com.spotify.confidence.openfeature

import com.spotify.confidence.Confidence
import com.spotify.confidence.ConfidenceValue
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceFeatureProviderTrackTest {
    @Test
    fun trackForwardsMergedContextAndMappedData() {
        val confidence = mockk<Confidence>(relaxed = true)
        every { confidence.getContext() } returns mapOf("plan" to ConfidenceValue.String("free"))

        val dataSlot = slot<Map<String, ConfidenceValue>>()
        val contextSlot = slot<Map<String, ConfidenceValue>>()
        every {
            confidence.track(
                eventName = "Checkout",
                data = capture(dataSlot),
                eventContext = capture(contextSlot)
            )
        } returns Unit

        val provider = ConfidenceFeatureProvider.create(confidence)
        val details = TrackingEventDetails(
            499.99,
            ImmutableStructure(
                "numberOfItems" to Value.Integer(4),
                "timeInCheckout" to Value.String("PT3M20S")
            )
        )
        val context = ImmutableContext(
            targetingKey = "user-1",
            attributes = mapOf(
                "plan" to Value.String("premium"),
                "country" to Value.String("SE")
            )
        )

        provider.track("Checkout", context, details)

        verify {
            confidence.track(
                eventName = "Checkout",
                data = any(),
                eventContext = any()
            )
        }
        assertEquals(ConfidenceValue.Double(499.99), dataSlot.captured["value"])
        assertEquals(ConfidenceValue.Integer(4), dataSlot.captured["numberOfItems"])
        assertEquals(ConfidenceValue.String("premium"), contextSlot.captured["plan"])
        assertEquals(ConfidenceValue.String("SE"), contextSlot.captured["country"])
        assertEquals(ConfidenceValue.String("user-1"), contextSlot.captured["targeting_key"])
    }

    @Test
    fun trackWithoutDetailsSendsEmptyData() {
        val confidence = mockk<Confidence>(relaxed = true)
        every { confidence.getContext() } returns emptyMap()

        val dataSlot = slot<Map<String, ConfidenceValue>>()
        every {
            confidence.track(
                eventName = "PageView",
                data = capture(dataSlot),
                eventContext = any()
            )
        } returns Unit

        val provider = ConfidenceFeatureProvider.create(confidence)
        provider.track("PageView", null, null)

        assertTrue(dataSlot.captured.isEmpty())
    }

    @Test
    fun trackContextAttributeOverridesMergedEvaluationContext() {
        val confidence = mockk<Confidence>(relaxed = true)
        every { confidence.getContext() } returns mapOf("plan" to ConfidenceValue.String("free"))

        val dataSlot = slot<Map<String, ConfidenceValue>>()
        every {
            confidence.track(
                eventName = "Checkout",
                data = capture(dataSlot),
                eventContext = any()
            )
        } returns Unit

        val provider = ConfidenceFeatureProvider.create(confidence)
        val details = TrackingEventDetails(
            null,
            ImmutableStructure(
                "context" to Value.Structure(mapOf("source" to Value.String("details")))
            )
        )

        provider.track(
            "Checkout",
            ImmutableContext(attributes = mapOf("plan" to Value.String("premium"))),
            details
        )

        assertEquals(
            ConfidenceValue.Struct(mapOf("source" to ConfidenceValue.String("details"))),
            dataSlot.captured["context"]
        )
    }
}
