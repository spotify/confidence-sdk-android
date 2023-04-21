package dev.openfeature.contrib.providers.apply

interface FlagApplier {
    fun apply(flagName: String, resolveToken: String)
}