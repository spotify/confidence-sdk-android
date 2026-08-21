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
            message.toMap()
        } else {
            message + mapOf("context" to ConfidenceValue.Struct(context))
        }
    }
}
