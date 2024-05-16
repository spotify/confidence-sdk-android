package com.spotify.confidence

fun <T> FlagResolution?.getEvaluation(
    flag: String,
    defaultValue: T,
    context: Map<String, ConfidenceValue>,
    applyFlag: (String, String) -> Unit = { _, _ -> }
): Evaluation<T> {
    val parsedKey = FlagKey(flag)
    if (this == null) {
        return Evaluation(
            value = defaultValue,
            reason = ResolveReason.ERROR,
            errorCode = ErrorCode.PROVIDER_NOT_READY
        )
    }
    requireNotNull(this)
    val resolvedFlag = this.flags.firstOrNull { it.flag == parsedKey.flagName }
        ?: return Evaluation(
            value = defaultValue,
            reason = ResolveReason.ERROR,
            errorCode = ErrorCode.FLAG_NOT_FOUND
        )

    if (resolvedFlag.reason != ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR) {
        applyFlag(parsedKey.flagName, resolveToken)
    } else {
        return Evaluation(
            value = defaultValue,
            reason = ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR,
            errorMessage = "Invalid targeting key",
            errorCode = ErrorCode.INVALID_CONTEXT
        )
    }

    // handle flag found
    val flagValue = resolvedFlag.value
    val resolvedValue: ConfidenceValue =
        findValueFromValuePath(ConfidenceValue.Struct(flagValue), parsedKey.valuePath)
            ?: if (resolvedFlag.reason == ResolveReason.RESOLVE_REASON_MATCH) {
                return Evaluation(
                    value = defaultValue,
                    reason = resolvedFlag.reason,
                    errorCode = ErrorCode.PARSE_ERROR
                )
            } else {
                return Evaluation(value = defaultValue, reason = resolvedFlag.reason)
            }

    return when (resolvedFlag.reason) {
        ResolveReason.RESOLVE_REASON_MATCH -> {
            val value = getTyped<T>(resolvedValue) ?: defaultValue
            val resolveReason = if (this.context != context) {
                ResolveReason.RESOLVE_REASON_STALE
            } else {
                resolvedFlag.reason
            }
            return Evaluation(
                value = value,
                reason = resolveReason,
                variant = resolvedFlag.variant
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
        is ConfidenceValue.Boolean -> v.boolean as T
        is ConfidenceValue.Double -> v.double as T
        is ConfidenceValue.Integer -> v.integer as T
        is ConfidenceValue.String -> v.string as T
        is ConfidenceValue.Struct -> v as T
        is ConfidenceValue.Date -> v as T
        is ConfidenceValue.Timestamp -> v as T
        is ConfidenceValue.List -> v as T
        is ConfidenceValue.Null -> null
    }
}

private fun findValueFromValuePath(value: ConfidenceValue.Struct, valuePath: List<String>): ConfidenceValue? {
    if (valuePath.isEmpty()) return value
    val currValue = value.map[valuePath[0]]
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

enum class ErrorCode {
    // The value was resolved before the provider was ready.
    RESOLVE_STALE,

    // The flag could not be found.
    FLAG_NOT_FOUND,
    INVALID_CONTEXT,
    PROVIDER_NOT_READY,
    PARSE_ERROR
}

data class ParseError(
    override val message: String,
    val flagPaths: List<String> = listOf()
) : Error(message)
data class FlagNotFoundError(
    override val message: String,
    val flag: String
) : Error(message)