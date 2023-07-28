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
                // TODO What if we have `writeRequestChannel` event in queue and `dataSentChannel` in queue?
                // Looks like "select" prioritizes the first executable first (so we should move dataSentChannel up top)
                select {
                    writeRequestChannel.onReceive { writeRequest ->
                        println(">> Entered writeRequestChannel")
                        // Add new event to in-mem (send = false) and write in-mem to file
                        onApply(writeRequest, data)
                        // Try to send everything that is send = false in-mem
                        // TODO Does this wait for network to finish before returning? If not, we still can't guarantee
                        // that in transit events are processed before new ones come in
                        onProcessBatch(
                            data,
                            dataSentChannel,
                            coroutineScope,
                            exceptionHandler
                        )
                        println(">> Exits writeRequestChannel")
                    }

                    // TODO Move up to give it priority?
                    dataSentChannel.onReceive { event ->
                        println(">> Entered dataSentChannel")
                        // Set "sent" to true and write in-mem to file
                        processBatchAction(event, data)
                        println(">> Exits dataSentChannel")
                    }
                }
            }
        }
    }

    override fun apply(input: INPUT) {
        println(">> APPLY ENTERS, processor gets new apply event")
        coroutineScope.launch {
            writeRequestChannel.send(input)
            println(">> Wrote in writeRequestChannel!")
        }
        println(">> APPLY RETURNS, application code can continue now...")
    }
}