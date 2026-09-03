package com.spotify.confidence.openfeature

import com.spotify.confidence.Confidence
import com.spotify.confidence.ConfidenceValue
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.spotify.confidence.Result as ConfidenceResult

class ConfidenceFeatureProviderTrackTest {
    @Test
    fun shutdownStopsConfidence() {
        val confidence = mockk<Confidence>(relaxed = true)

        ConfidenceFeatureProvider.create(confidence).shutdown()

        verify(exactly = 1) { confidence.stop() }
    }

    @Test
    fun initializeEmitsProviderReady() = runTest {
        val confidence = mockk<Confidence>(relaxed = true)
        val provider = ConfidenceFeatureProvider.create(confidence)

        provider.initialize(
            ImmutableContext(
                targetingKey = "user-1",
                attributes = mapOf("country" to Value.String("SE"))
            )
        )

        assertTrue(provider.observe().first() is OpenFeatureProviderEvents.ProviderReady)
        verify {
            confidence.putContextLocal(
                match {
                    it["targeting_key"] == ConfidenceValue.String("user-1") &&
                        it["country"] == ConfidenceValue.String("SE")
                }
            )
        }
        coVerify { confidence.fetchAndActivate() }
    }

    @Test
    fun initializeEmitsProviderErrorBeforeThrowing() = runTest {
        val confidence = mockk<Confidence>(relaxed = true)
        val error = IllegalStateException("boom")
        coEvery { confidence.fetchAndActivate() } throws error
        val provider = ConfidenceFeatureProvider.create(confidence)

        try {
            provider.initialize(null)
            fail("Expected initialization to throw")
        } catch (e: IllegalStateException) {
            assertEquals(error, e)
        }

        val event = provider.observe().first()
        assertTrue(event is OpenFeatureProviderEvents.ProviderError)
        event as OpenFeatureProviderEvents.ProviderError
        assertEquals("boom", event.eventDetails!!.message)
        assertEquals(ErrorCode.GENERAL, event.eventDetails!!.errorCode)
    }

    @Test
    fun onContextSetEmitsProviderReadyAfterReconciliation() = runTest {
        val confidence = mockk<Confidence>(relaxed = true)
        coEvery { confidence.putContextAndWait(any(), any()) } returns ConfidenceResult.Success(Unit)
        val provider = ConfidenceFeatureProvider.create(confidence)

        provider.onContextSet(
            oldContext = ImmutableContext(attributes = mapOf("plan" to Value.String("free"))),
            newContext = ImmutableContext(
                targetingKey = "user-1",
                attributes = mapOf("country" to Value.String("SE"))
            )
        )

        assertTrue(provider.observe().first() is OpenFeatureProviderEvents.ProviderReady)
        coVerify {
            confidence.putContextAndWait(
                match {
                    it["targeting_key"] == ConfidenceValue.String("user-1") &&
                        it["country"] == ConfidenceValue.String("SE")
                },
                listOf("plan")
            )
        }
    }

    @Test
    fun onContextSetEmitsProviderStaleWhenReconciliationFails() = runTest {
        val confidence = mockk<Confidence>(relaxed = true)
        coEvery {
            confidence.putContextAndWait(any(), any())
        } returns ConfidenceResult.Failure(IllegalStateException("fetch failed"))
        val provider = ConfidenceFeatureProvider.create(confidence)

        provider.onContextSet(
            oldContext = ImmutableContext(attributes = mapOf("plan" to Value.String("free"))),
            newContext = ImmutableContext(
                targetingKey = "user-1",
                attributes = mapOf("country" to Value.String("SE"))
            )
        )

        val event = provider.observe().first()
        assertTrue(event is OpenFeatureProviderEvents.ProviderStale)
        event as OpenFeatureProviderEvents.ProviderStale
        assertEquals("fetch failed", event.eventDetails!!.message)
    }

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
