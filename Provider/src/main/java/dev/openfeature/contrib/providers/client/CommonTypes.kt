package dev.openfeature.contrib.providers.client

enum class ConfidenceRegion {
    EUROPE,
    USA
}

enum class ResolveReason {
    // Unspecified enum.
    RESOLVE_REASON_UNSPECIFIED,

    // The flag was successfully resolved because one rule matched.
    RESOLVE_REASON_MATCH,

    // The flag could not be resolved because no rule matched.
    RESOLVE_REASON_NO_SEGMENT_MATCH,

    // The flag could not be resolved because the matching rule had no variant
    // that could be assigned.
    RESOLVE_REASON_NO_TREATMENT_MATCH,

    // the flag could not be resolved because the targeting key
    // is invalid
    RESOLVE_REASON_TARGETING_KEY_ERROR,

    // The flag could not be resolved because it was archived.
    RESOLVE_REASON_FLAG_ARCHIVED
}