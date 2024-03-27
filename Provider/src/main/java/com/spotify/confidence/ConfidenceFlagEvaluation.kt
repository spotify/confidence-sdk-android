package com.spotify.confidence

import com.spotify.confidence.client.ResolveReason

internal fun <T> FlagResolution.getEvaluation(
    flag: String,
    defaultValue: T,
    context: Map<String, ConfidenceValue>
): Evaluation<T> {
    val parsedKey = FlagKey(flag)
    val resolvedFlag = this.flags.firstOrNull { it.flag == parsedKey.flagName }
        ?: return Evaluation(
            value = defaultValue,
            reason = ResolveReason.RESOLVE_REASON_UNSPECIFIED,
            errorCode = ErrorCode.FLAG_NOT_FOUND
        )

    // handle stale case
    if (this.context != context) {
        return Evaluation(
            value = defaultValue,
            reason = resolvedFlag.reason,
            errorCode = ErrorCode.RESOLVE_STALE
        )
    }

    // handle flag found
    val flagValue = resolvedFlag.value
    val resolvedValue: ConfidenceValue = findValueFromValuePath(ConfidenceValue.Struct(flagValue), parsedKey.valuePath)
        ?: throw ParseError(
            "Unable to parse flag value: ${parsedKey.valuePath.joinToString(separator = "/")}"
        )

    return when (resolvedFlag.reason) {
        ResolveReason.RESOLVE_REASON_MATCH -> {
            val value = getTyped<T>(resolvedValue) ?: defaultValue
            return Evaluation(
                value = value,
                reason = resolvedFlag.reason,
                variant = resolvedFlag.variant
            )
        }
        ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR -> {
            Evaluation(
                value = defaultValue,
                reason = resolvedFlag.reason,
                errorCode = ErrorCode.INVALID_CONTEXT,
                errorMessage = "Invalid targeting key"
            )
        }

        else -> {
            Evaluation(defaultValue, reason = resolvedFlag.reason)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> getTyped(v: ConfidenceValue): T? {
    return when (v) {
        is ConfidenceValue.Boolean -> v.value as T
        is ConfidenceValue.Double -> v.value as T
        is ConfidenceValue.Int -> v.value as T
        is ConfidenceValue.String -> v.value as T
        is ConfidenceValue.Struct -> v as T
        is ConfidenceValue.Null -> v as T
    }
}

private fun findValueFromValuePath(value: ConfidenceValue.Struct, valuePath: List<String>): ConfidenceValue? {
    if (valuePath.isEmpty()) return value
    val currValue = value.value[valuePath[0]]
    return when {
        currValue is ConfidenceValue.Struct -> {
            findValueFromValuePath(currValue, valuePath.subList(1, valuePath.count()))
        }
        valuePath.count() == 1 -> currValue
        else -> null
    }
}

private data class FlagKey(
    val flagName: String,
    val valuePath: List<String>
) {
    companion object {
        operator fun invoke(evalKey: String): FlagKey {
            val splits = evalKey.split(".")
            return FlagKey(
                splits.getOrNull(0)!!,
                splits.subList(1, splits.count())
            )
        }
    }
}

internal fun <T> FlagResolution.getValue(
    flag: String,
    defaultValue: T,
    context: Map<String, ConfidenceValue>
): T {
    return getEvaluation(flag, defaultValue, context).value
}

enum class ErrorCode {
    // The value was resolved before the provider was ready.
    RESOLVE_STALE,

    // The flag could not be found.
    FLAG_NOT_FOUND,
    INVALID_CONTEXT
}

class ParseError(message: String) : Error(message)