package com.spotify.confidence

class ConfidenceError {
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

    class InvalidContextInMessage : Error("Field 'context' is not allowed in event's data")
}