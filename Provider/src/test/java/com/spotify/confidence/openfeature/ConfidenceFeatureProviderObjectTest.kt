package com.spotify.confidence.openfeature

import com.spotify.confidence.Confidence
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.FlagResolution
import com.spotify.confidence.ResolveReason
import com.spotify.confidence.client.ResolvedFlag
import com.spotify.confidence.getEvaluation
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ConfidenceFeatureProviderObjectTest {
    @Test
    fun missingFlagPreservesLongDefaults() {
        val provider = createProvider(FlagResolution.EMPTY)

        for (count in listOf(42L, Long.MAX_VALUE)) {
            val default = Value.Structure(mapOf("count" to Value.Long(count)))

            val evaluation = provider.getObjectEvaluation("missing", default, null)

            assertSame(default, evaluation.value)
            assertEquals(Reason.ERROR.name, evaluation.reason)
            assertEquals(ErrorCode.FLAG_NOT_FOUND, evaluation.errorCode)
        }
    }

    @Test
    fun matchingFlagResolvesWithLongMaxValueInUnusedDefault() {
        val provider = createProvider(
            FlagResolution(
                context = emptyMap(),
                flags = listOf(
                    ResolvedFlag(
                        flag = "size",
                        variant = "variant-1",
                        value = mapOf("count" to ConfidenceValue.Integer(42)),
                        reason = ResolveReason.RESOLVE_REASON_MATCH,
                        shouldApply = false
                    )
                ),
                resolveToken = "token"
            )
        )
        val default = Value.Structure(mapOf("count" to Value.Long(Long.MAX_VALUE)))

        val evaluation = provider.getObjectEvaluation("size", default, null)

        assertEquals(Value.Structure(mapOf("count" to Value.Integer(42))), evaluation.value)
        assertEquals(Reason.TARGETING_MATCH.name, evaluation.reason)
        assertEquals("variant-1", evaluation.variant)
        assertNull(evaluation.errorCode)
    }

    private fun createProvider(resolution: FlagResolution): ConfidenceFeatureProvider {
        val confidence = mockk<Confidence>(relaxed = true)
        every { confidence.getFlag<Any>(any(), any()) } answers {
            resolution.getEvaluation(firstArg(), secondArg<Any>(), emptyMap())
        }
        return ConfidenceFeatureProvider.create(confidence)
    }
}
