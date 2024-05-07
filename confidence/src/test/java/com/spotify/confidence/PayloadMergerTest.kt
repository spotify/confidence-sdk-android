package com.spotify.confidence

import org.junit.Test

class PayloadMergerTest {
    @Test
    fun `merging two payloads`() {
        val payloadMerger = PayloadMergerImpl()
        val context = mapOf("a" to ConfidenceValue.Integer(1), "b" to ConfidenceValue.Integer(2))
        val message = mapOf("b" to ConfidenceValue.Integer(3), "c" to ConfidenceValue.Integer(4))
        val result = payloadMerger(context, message)
        assert(result == mapOf("a" to ConfidenceValue.Integer(1), "b" to ConfidenceValue.Integer(3), "c" to ConfidenceValue.Integer(
            4
        )
        ))
    }
}