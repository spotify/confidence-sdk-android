package dev.openfeature.contrib.providers.apply

import android.content.Context
import dev.openfeature.contrib.providers.EventProcessor
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.serializers.DateSerializer
import dev.openfeature.contrib.providers.client.serializers.UUIDSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File
import java.util.Date

const val APPLY_FILE_NAME = "confidence_apply_cache.json"
data class FlagApplierInput(
    val resolveToken: String,
    val flagName: String
)

data class FlagApplierBatchProcessedInput(
    val resolveToken: String,
    val flags: List<AppliedFlag>
)

@Serializable
data class ApplyInstance(
    @Contextual
    val time: Date,
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
        data[resolveToken]?.putIfAbsent(flagName, ApplyInstance(Date(), false))
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
        data: Map<String, MutableMap<String, ApplyInstance>>
    ) {
        // All apply events have been sent for this token, don't add this token to the file
        val toStoreData = data
            .filter {
                !it.value.values.all { applyInstance -> applyInstance.sent }
            }
        file.writeText(json.encodeToString(toStoreData))
    }

    private suspend fun readFile(
        data: FlagsAppliedMap
    ) = coroutineScope {
        if (!file.exists()) return@coroutineScope
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return@coroutineScope
        val newData = json.decodeFromString<FlagsAppliedMap>(fileText)
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
private val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
    }
}