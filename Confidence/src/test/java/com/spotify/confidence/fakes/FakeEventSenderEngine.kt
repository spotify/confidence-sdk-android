package com.spotify.confidence.fakes

import com.spotify.confidence.ConfidenceFieldsType
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.EventSenderEngine
import kotlinx.coroutines.channels.Channel
import java.io.File

class FakeEventSenderEngine() : EventSenderEngine {

    val list: MutableList<Triple<String, ConfidenceFieldsType, Map<String, ConfidenceValue>>> = mutableListOf()

    override fun onLowMemoryChannel(): Channel<List<File>> {
        TODO("Not yet implemented")
    }

    override fun emit(eventName: String, data: ConfidenceFieldsType, context: Map<String, ConfidenceValue>) {
        list.add(Triple(eventName, data, context))
    }

    override fun flush() {
    }

    override fun stop() {
    }
}