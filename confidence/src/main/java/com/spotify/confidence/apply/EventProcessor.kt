package com.spotify.confidence.apply

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

class EventProcessor<INPUT, BatchProcessInputs, DATA>(
    private val onInitialised: suspend () -> DATA,
    private val onApply: (INPUT, DATA) -> Unit,
    private val onProcessBatch:
        (DATA, SendChannel<BatchProcessInputs>, CoroutineScope, CoroutineExceptionHandler) -> Unit,
    private val processBatchAction: (BatchProcessInputs, DATA) -> Unit,
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

    fun start() {
        coroutineScope.launch {
            // the data being in a coroutine like this
            // ensures we don't have any shared mutability
            val data: DATA = onInitialised()

            // Try send events retrieved via "onInitialised()"
            onProcessBatch(
                data,
                dataSentChannel,
                coroutineScope,
                exceptionHandler
            )

            // the select clause makes sure that we don't
            // share the data/file write operations between coroutines
            // at any certain time there is only one of these events being handled
            while (true) {
                select {
                    writeRequestChannel.onReceive { writeRequest ->
                        onApply(writeRequest, data)
                        onProcessBatch(
                            data,
                            dataSentChannel,
                            coroutineScope,
                            exceptionHandler
                        )
                    }

                    dataSentChannel.onReceive { event ->
                        processBatchAction(event, data)
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
}