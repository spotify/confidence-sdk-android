package com.spotify.confidence

internal val minBatchSizeFlushPolicy = object : FlushPolicy {
    private var size = 0
    override fun reset() {
        size = 0
    }

    override fun hit(event: Event) {
        size++
    }

    override fun shouldFlush(): Boolean {
        return size > 4
    }
}