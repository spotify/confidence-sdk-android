package com.spotify.confidence.openfeature

import com.spotify.confidence.ConfidenceValue
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.TrackingEventDetails

internal fun mergeEventContext(
    sessionContext: Map<String, ConfidenceValue>,
    openFeatureContext: Map<String, ConfidenceValue>
): Map<String, ConfidenceValue> {
    if (openFeatureContext.isEmpty()) {
        return sessionContext
    }
    return sessionContext + openFeatureContext
}

internal fun EvaluationContext?.toTrackContextMap(): Map<String, ConfidenceValue> {
    if (this == null) {
        return emptyMap()
    }
    val map = mutableMapOf<String, ConfidenceValue>()
    val targetingKey = getTargetingKey()
    if (targetingKey.isNotEmpty() && !asMap().containsKey("targeting_key")) {
        map["targeting_key"] = ConfidenceValue.String(targetingKey)
    }
    map.putAll(asMap().mapValues { it.value.toConfidenceValue() })
    return map
}

internal fun TrackingEventDetails?.toTrackingData(): Map<String, ConfidenceValue> {
    if (this == null) {
        return emptyMap()
    }
    return mapOf(
        "value" to (value?.toConfidenceValue() ?: ConfidenceValue.Null)
    ) + structure.asMap().mapValues { it.value.toConfidenceValue() }
}

private fun Number.toConfidenceValue(): ConfidenceValue = when (this) {
    is Int -> ConfidenceValue.Integer(this)
    is Double -> ConfidenceValue.Double(this)
    else -> ConfidenceValue.Null
}
