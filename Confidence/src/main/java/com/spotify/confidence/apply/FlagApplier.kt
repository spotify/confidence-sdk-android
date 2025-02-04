package com.spotify.confidence.apply

interface FlagApplier {
    fun apply(flagName: String, resolveToken: String, shouldApply: Boolean)
}