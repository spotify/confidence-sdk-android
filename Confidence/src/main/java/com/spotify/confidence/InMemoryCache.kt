package com.spotify.confidence

interface ProviderCache {
    fun refresh(flagResolution: FlagResolution)
    fun get(): FlagResolution?
}

class InMemoryCache : ProviderCache {
    @Volatile
    private var flagResolution: FlagResolution? = null
    override fun refresh(flagResolution: FlagResolution) {
        this.flagResolution = flagResolution
    }

    override fun get(): FlagResolution? = this.flagResolution
}