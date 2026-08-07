package com.spotify.confidence

private typealias ConfidenceStruct = Map<String, ConfidenceValue>
internal interface PayloadMerger : (ConfidenceStruct, ConfidenceStruct) -> ConfidenceStruct
internal class PayloadMergerImpl : PayloadMerger {
    override fun invoke(context: ConfidenceStruct, message: ConfidenceStruct): ConfidenceStruct {
        return if (message.containsKey("context")) {
            // An explicit "context" entry in event data overrides the evaluation context for this event.
            message
        } else {
            message + mapOf("context" to ConfidenceValue.Struct(context))
        }
    }
}
