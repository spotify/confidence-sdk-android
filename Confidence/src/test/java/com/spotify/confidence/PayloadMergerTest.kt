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

    @Test
    fun `merged payload snapshots message and context`() {
        val payloadMerger = PayloadMergerImpl()
        val context: MutableMap<String, ConfidenceValue> = mutableMapOf("a" to ConfidenceValue.Integer(1))
        val message: MutableMap<String, ConfidenceValue> = mutableMapOf("b" to ConfidenceValue.Integer(2))
        val result = payloadMerger(context, message)
        context["a"] = ConfidenceValue.Integer(3)
        context["new"] = ConfidenceValue.String("late context")
        message["b"] = ConfidenceValue.Integer(4)
        message["new"] = ConfidenceValue.String("late message")

        assert(
            result == mapOf(
                "b" to ConfidenceValue.Integer(2),
                "context" to ConfidenceValue.Struct(
                    mapOf("a" to ConfidenceValue.Integer(1))
                )
            )
        )
    }
}
