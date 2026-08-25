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
    if (targetingKey.isNotEmpty()) {
        map["targeting_key"] = ConfidenceValue.String(targetingKey)
    }
    // Explicit attributes win over the injected targeting key
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
    is Long -> if (this in Int.MIN_VALUE..Int.MAX_VALUE) {
        ConfidenceValue.Integer(toInt())
    } else {
        ConfidenceValue.Double(toDouble())
    }
    is Double -> ConfidenceValue.Double(this)
    is Float -> ConfidenceValue.Double(toDouble())
    else -> ConfidenceValue.Null
}
