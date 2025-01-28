package com.spotify.confidence

import com.spotify.confidence.Update.Event
import com.spotify.confidence.fakes.FakeDiskStorage
import com.spotify.confidence.fakes.FakeEventSenderEngine
import com.spotify.confidence.fakes.FakeFlagApplierClient
import com.spotify.confidence.fakes.FakeFlagResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ConfidenceProducerIntegrationTest {

    private lateinit var engine: FakeEventSenderEngine

    @Before
    fun setUp() {
        engine = FakeEventSenderEngine()
    }

    class MyProducer : EventProducer, ContextProducer {
        private val flow = MutableSharedFlow<Update>()
        override fun updates(): Flow<Update> = flow
        override fun stop() {}
        suspend fun emitContextChange(map: Map<String, ConfidenceValue.String>) = flow.emit(Update.ContextUpdate(map))
        suspend fun emitEvent(event: Event) = flow.emit(event)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testSomething() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
//        val testDispatcher = StandardTestDispatcher(testScheduler) //does not work... why?
        val producerUnderTest = MyProducer()
        val confidence = Confidence(
            clientSecret = "secret",
            dispatcher = testDispatcher,
            diskStorage = FakeDiskStorage(),
            eventSenderEngine = engine,
            flagResolver = FakeFlagResolver(),
            debugLogger = null,
            flagApplierClient = FakeFlagApplierClient()
        )

        confidence.track(producerUnderTest)

        producerUnderTest.emitContextChange(
            mapOf("key" to ConfidenceValue.String("value"))
        )
        producerUnderTest.emitEvent(
            Event("event", mapOf("key" to ConfidenceValue.String("value")))
        )

        Assert.assertEquals("value", (confidence.getContext()["key"] as? ConfidenceValue.String)?.string)
        Assert.assertEquals(1, engine.list.size)
        Assert.assertEquals(ConfidenceValue.String("value"), engine.list.first().third["key"])

        confidence.stop()
    }
}