package com.spotify.confidence

import java.util.Date

internal val manualFlushEvent = EngineEvent("confidence_manual_flush", Date(), mapOf())

internal object ManualFlushPolicy : FlushPolicy {
    private var flushRequested = false
    override fun reset() {
        flushRequested = false
    }

    override fun hit(event: EngineEvent) {
        flushRequested = event.eventDefinition == manualFlushEvent.eventDefinition
    }

    override fun shouldFlush(): Boolean = flushRequested
}