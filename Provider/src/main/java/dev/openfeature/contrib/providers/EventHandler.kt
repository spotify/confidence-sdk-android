package dev.openfeature.contrib.providers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

interface EventApplier<INPUT> {
    fun apply(input: INPUT)
}

class EventHandler<INPUT, BatchProcessInputs, DATA> private constructor(
    private val initialiseCallback: suspend () -> DATA,
    private val onApplyCallback: (INPUT, DATA) -> Unit,
    private val processBatchCallback:
        (DATA, SendChannel<BatchProcessInputs>, CoroutineScope, CoroutineExceptionHandler) -> Unit,
    private val onBatchProcessedCallback: (BatchProcessInputs, DATA) -> Unit,
    dispatcher: CoroutineDispatcher
) : EventApplier<INPUT> {
    private val coroutineScope: CoroutineScope by lazy {
        // the SupervisorJob makes sure that if one coroutine
        // throw an error, the whole scope is not cancelled
        // the thrown error can be handled individually
        CoroutineScope(SupervisorJob() + dispatcher)
    }

    private val writeRequestChannel: Channel<INPUT> = Channel()
    private val dataSentChannel: Channel<BatchProcessInputs> = Channel()
    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, _ -> }
    }

    init {
        coroutineScope.launch {
            // the data being in a coroutine like this
            // ensures we don't have any shared mutability
            val data: DATA = initialiseCallback()

            // the select clause makes sure that we don't
            // share the data/file write operations between coroutines
            // at any certain time there is only one of these events being handled
            while (true) {
                select {
                    writeRequestChannel.onReceive { writeRequest ->
                        onApplyCallback(writeRequest, data)
                        processBatchCallback(
                            data,
                            dataSentChannel,
                            coroutineScope,
                            exceptionHandler
                        )
                    }

                    dataSentChannel.onReceive { event ->
                        onBatchProcessedCallback(event, data)
                    }
                }
            }
        }
    }

    override fun apply(input: INPUT) {
        coroutineScope.launch {
            writeRequestChannel.send(input)
        }
    }

    companion object {
        fun <INPUT, BatchProcessInputs, DATA> builder(dispatcher: CoroutineDispatcher) =
            Builder<INPUT, BatchProcessInputs, DATA>(dispatcher)
    }

    class Builder<INPUT, BatchProcessInputs, DATA> constructor(
        private val dispatcher: CoroutineDispatcher
    ) {
        private lateinit var initialiseCallback: suspend () -> DATA
        private lateinit var onApplyCallback: (INPUT, DATA) -> Unit
        private lateinit var onBatchProcessedCallback: (BatchProcessInputs, DATA) -> Unit
        private lateinit var processBatchCallback:
            (DATA, SendChannel<BatchProcessInputs>, CoroutineScope, CoroutineExceptionHandler) -> Unit

        fun initialise(
            initialiseCallback: suspend () -> DATA
        ): Builder<INPUT, BatchProcessInputs, DATA> {
            this.initialiseCallback = initialiseCallback
            return this
        }

        fun onApplyEvent(
            callback: (INPUT, DATA) -> Unit
        ): Builder<INPUT, BatchProcessInputs, DATA> {
            onApplyCallback = callback
            return this
        }

        fun onBatchProcessed(
            callback: (BatchProcessInputs, DATA) -> Unit
        ): Builder<INPUT, BatchProcessInputs, DATA> {
            this.onBatchProcessedCallback = callback
            return this
        }

        fun onBatchProcess(
            callback: (DATA, SendChannel<BatchProcessInputs>, CoroutineScope, CoroutineExceptionHandler) -> Unit
        ): Builder<INPUT, BatchProcessInputs, DATA> {
            this.processBatchCallback = callback
            return this
        }

        fun build() = EventHandler(
            initialiseCallback,
            onApplyCallback,
            processBatchCallback,
            onBatchProcessedCallback,
            dispatcher = dispatcher
        )
    }
}