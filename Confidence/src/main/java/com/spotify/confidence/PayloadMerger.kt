package com.spotify.confidence

private typealias ConfidenceStruct = Map<String, ConfidenceValue>
internal interface PayloadMerger : (ConfidenceStruct, ConfidenceStruct) -> ConfidenceStruct
internal class PayloadMergerImpl(
    private val debugLogger: DebugLogger? = null
) : PayloadMerger {
    override fun invoke(context: ConfidenceStruct, message: ConfidenceStruct): ConfidenceStruct {
        return if (message.containsKey("context")) {
            // An explicit "context" entry in event data overrides the evaluation context for this event.
            debugLogger?.logMessage(
                message = "Event data contains a 'context' field: it replaces the evaluation context for this event",
                isWarning = true
            )
            message.snapshot()
        } else {
            message.snapshot() + mapOf("context" to ConfidenceValue.Struct(context.snapshot()))
        }
    }
}

internal fun ConfidenceStruct.snapshot(): ConfidenceStruct = mapValues { (_, value) -> value.snapshot() }

internal fun ConfidenceValue.snapshot(): ConfidenceValue = when (this) {
    is ConfidenceValue.Struct -> ConfidenceValue.Struct(map.snapshot())
    is ConfidenceValue.List -> ConfidenceValue.List(list.map { it.snapshot() })
    is ConfidenceValue.Date -> ConfidenceValue.Date(java.util.Date(date.time))
    is ConfidenceValue.Timestamp -> ConfidenceValue.Timestamp(java.util.Date(dateTime.time))
    else -> this
}
