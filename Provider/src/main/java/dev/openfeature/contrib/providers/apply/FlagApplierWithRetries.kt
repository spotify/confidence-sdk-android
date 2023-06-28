package dev.openfeature.contrib.providers.apply

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openfeature.contrib.providers.EventProcessor
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.InstantTypeAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

const val APPLY_FILE_NAME = "confidence_apply_cache.json"
data class FlagApplierInput(
    val resolveToken: String,
    val flagName: String
)

data class FlagApplierBatchProcessedInput(
    val resolveToken: String,
    val flags: List<AppliedFlag>
)

data class ApplyInstance(
    val time: Instant,
    val sent: Boolean
)

private typealias FlagsAppliedMap =
    MutableMap<String, MutableMap<String, ApplyInstance>>

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val dispatcher: CoroutineDispatcher,
    context: Context
) : FlagApplier {
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    private val eventProcessor by lazy {
        EventProcessor<
            FlagApplierInput,
            FlagApplierBatchProcessedInput,
            FlagsAppliedMap
            >(
            onInitialised = {
                val data: FlagsAppliedMap = mutableMapOf()
                readFile(data)
                data
            },
            onApply = { flagApplierInput, mutableData ->
                internalApply(
                    flagApplierInput.flagName,
                    flagApplierInput.resolveToken,
                    mutableData
                )
            },
            processBatchAction = { event, mutableData ->
                event.flags.forEach {
                    mutableData[event.resolveToken]?.computeIfPresent(it.flag) { _, v -> v.copy(sent = true) }
                }
                writeToFile(mutableData)
            },
            onProcessBatch = { mutableData, sendChannel, coroutineScope, coroutineExceptionHandler ->
                processBatch(
                    mutableData,
                    coroutineScope,
                    sendChannel,
                    coroutineExceptionHandler
                )
            },
            dispatcher = dispatcher
        )
    }

    init {
        eventProcessor.start()
    }

    override fun apply(flagName: String, resolveToken: String) {
        eventProcessor.apply(FlagApplierInput(resolveToken, flagName))
    }

    private fun internalApply(
        flagName: String,
        resolveToken: String,
        data: FlagsAppliedMap
    ) {
        data.putIfAbsent(resolveToken, hashMapOf())
        data[resolveToken]?.putIfAbsent(flagName, ApplyInstance(Instant.now(), false))
        writeToFile(data)
    }

    private fun processBatch(
        data: FlagsAppliedMap,
        coroutineScope: CoroutineScope,
        sendChannel: SendChannel<FlagApplierBatchProcessedInput>,
        coroutineExceptionHandler: CoroutineExceptionHandler
    ) {
        data.entries.forEach { (token, flagsForToken) ->
            val appliedFlagsKeyed: List<AppliedFlag> = flagsForToken.entries
                .filter { e -> !e.value.sent }
                .map { e -> AppliedFlag(e.key, e.value.time) }
            // TODO chunk size 20 is an arbitrary value, replace with appropriate size
            appliedFlagsKeyed.chunked(CHUNK_SIZE).forEach { appliedFlagsKeyedChunk ->
                coroutineScope.launch(coroutineExceptionHandler) {
                    client.apply(appliedFlagsKeyedChunk, token)
                    sendChannel.send(FlagApplierBatchProcessedInput(token, appliedFlagsKeyedChunk))
                }
            }
        }
    }

    private fun writeToFile(
        data: FlagsAppliedMap
    ) {
        val fileData = gson.toJson(
            // All apply events have been sent for this token, don't add this token to the file
            data.filter { !it.value.values.all { applyInstance -> applyInstance.sent } }
        )
        // TODO Add a limit for the file size?
        file.writeText(fileData)
    }

    private suspend fun readFile(
        data: FlagsAppliedMap
    ) = coroutineScope {
        if (!file.exists()) return@coroutineScope
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return@coroutineScope
        val type = object : TypeToken<FlagsAppliedMap>() {}.type
        val newData: FlagsAppliedMap = gson.fromJson(fileText, type)
        // Append to `data` rather than overwrite it, in case `data` is not empty when the file is being read
        newData.entries.forEach { (resolveToken, eventsByFlagName) ->
            eventsByFlagName.entries.forEach { (flagName, applyInstance) ->
                data.putIfAbsent(resolveToken, hashMapOf())
                data[resolveToken]?.putIfAbsent(flagName, applyInstance)
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 20
    }
}