package com.spotify.confidence.fakes

import com.spotify.confidence.FlagResolution
import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.cache.DiskStorage

class FakeDiskStorage() : DiskStorage {
    override fun store(flagResolution: FlagResolution) {
        TODO("Not yet implemented")
    }

    override fun read(): FlagResolution = FlagResolution.EMPTY

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>) {
        TODO("Not yet implemented")
    }

    override fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>> {
        return emptyMap<String, MutableMap<String, ApplyInstance>>().toMutableMap()
    }
}