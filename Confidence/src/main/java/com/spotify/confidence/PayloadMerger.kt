package com.spotify.confidence

private typealias ConfidenceStruct = Map<String, ConfidenceValue>
internal interface PayloadMerger : (ConfidenceStruct, ConfidenceStruct) -> ConfidenceStruct
internal class PayloadMergerImpl : PayloadMerger {
    override fun invoke(context: ConfidenceStruct, message: ConfidenceStruct): ConfidenceStruct =
        context + message
}