package com.spotify.confidence.cache

import com.spotify.confidence.FlagResolution
import com.spotify.confidence.apply.ApplyInstance

interface DiskStorage {
    fun store(flagResolution: FlagResolution)
    fun read(): FlagResolution

    fun clear()
    fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>)
    fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>>
}