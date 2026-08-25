package com.spotify.confidence

import org.junit.Test
import java.util.Date

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

    @Test
    fun `merged payload snapshots nested mutable values`() {
        val nestedContext = mutableMapOf<String, ConfidenceValue>(
            "plan" to ConfidenceValue.String("free")
        )
        val nestedMessage = mutableListOf<ConfidenceValue>(ConfidenceValue.String("original"))
        val eventDate = Date(1_000)
        val result = PayloadMergerImpl()(
            context = mapOf(
                "user" to ConfidenceValue.Struct(nestedContext),
                "date" to ConfidenceValue.Date(eventDate)
            ),
            message = mapOf("items" to ConfidenceValue.List(nestedMessage))
        )

        nestedContext["plan"] = ConfidenceValue.String("premium")
        nestedMessage[0] = ConfidenceValue.String("changed")
        eventDate.time = 2_000

        assert(
            result == mapOf(
                "items" to ConfidenceValue.List(listOf(ConfidenceValue.String("original"))),
                "context" to ConfidenceValue.Struct(
                    mapOf(
                        "user" to ConfidenceValue.Struct(
                            mapOf("plan" to ConfidenceValue.String("free"))
                        ),
                        "date" to ConfidenceValue.Date(Date(1_000))
                    )
                )
            )
        )
    }
}
