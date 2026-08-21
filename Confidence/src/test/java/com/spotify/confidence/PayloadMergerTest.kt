package com.spotify.confidence

import org.junit.Test

class PayloadMergerTest {
    @Test
    fun `merging two payloads`() {
        val payloadMerger = PayloadMergerImpl()
        val context = mapOf("a" to ConfidenceValue.Integer(1), "b" to ConfidenceValue.Integer(2))
        val message = mapOf("b" to ConfidenceValue.Integer(3), "c" to ConfidenceValue.Integer(4))
        val result = payloadMerger(context, message)
        assert(
            result == mapOf(
                "b" to ConfidenceValue.Integer(3),
                "c" to ConfidenceValue.Integer(4),
                "context" to ConfidenceValue.Struct(
                    mapOf(
                        "a" to ConfidenceValue.Integer(1),
                        "b" to ConfidenceValue.Integer(2)
                    )
                )
            )
        )
    }

    @Test
    fun `context in data overrides evaluation context`() {
        val payloadMerger = PayloadMergerImpl()
        val context = mapOf("a" to ConfidenceValue.Integer(1), "b" to ConfidenceValue.Integer(2))
        val message = mutableMapOf(
            "b" to ConfidenceValue.Integer(3),
            "context" to ConfidenceValue.String("override")
        )
        val result = payloadMerger(context, message)
        message["b"] = ConfidenceValue.Integer(4)
        message["new"] = ConfidenceValue.String("late mutation")

        assert(
            result == mapOf(
                "b" to ConfidenceValue.Integer(3),
                "context" to ConfidenceValue.String("override")
            )
        )
    }
}
