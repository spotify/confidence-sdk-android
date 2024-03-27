package com.spotify.confidence

import java.io.File

interface EventSender : ContextApi {
    fun send(
        definition: String,
        payload: ConfidenceFieldsType = mapOf()
    )
    fun onLowMemory(body: (List<File>) -> Unit): EventSender
    fun stop()
}

interface FlushPolicy {
    fun reset()
    fun hit(event: Event)
    fun shouldFlush(): Boolean
}