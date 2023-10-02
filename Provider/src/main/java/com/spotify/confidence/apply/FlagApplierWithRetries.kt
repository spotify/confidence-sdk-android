package com.spotify.confidence.apply

import android.content.Context
import com.spotify.confidence.EventProcessor
import com.spotify.confidence.client.AppliedFlag
import com.spotify.confidence.client.ConfidenceClient
import com.spotify.confidence.client.Result
import com.spotify.confidence.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
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
    val flags: List<AppliedFlag>,
    val result: Result
)

enum class EventStatus {
    CREATED,
    SENDING,
    SENT
}

@Serializable
data class ApplyInstance(
    @Contextual
    val time: Date,
    val eventStatus: EventStatus
)

internal typealias FlagsAppliedMap =
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
                // we consider all the sending events as not sent to not miss them
                // there is a chance that they are sending status because the network response is dropped
                changeSendingEventsToCreate(data)
            },
            onApply = { flagApplierInput, mutableData ->
                val data = internalApply(
                    flagApplierInput.flagName,
                    flagApplierInput.resolveToken,
                    mutableData
                )
                writeToFile(data)
            },
            processBatchAction = { event, mutableData ->
                when (event.result) {
                    is Result.Success -> {
                        changeFlagStatus(
                            event.resolveToken,
                            event.flags,
                            mutableData,
                            EventStatus.SENT
                        )
                        writeToFile(mutableData)
                    }
                    is Result.Failure -> {
                        changeFlagStatus(
                            event.resolveToken,
                            event.flags,
                            mutableData,
                            EventStatus.CREATED
                        )
                    }
                }
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
    ): FlagsAppliedMap {
        putIfAbsent(data, resolveToken, hashMapOf())
        putIfAbsent(data[resolveToken], flagName, ApplyInstance(Date(), EventStatus.CREATED))
        return data
    }

    private fun processBatch(
        data: FlagsAppliedMap,
        coroutineScope: CoroutineScope,
        sendChannel: SendChannel<FlagApplierBatchProcessedInput>,
        coroutineExceptionHandler: CoroutineExceptionHandler
    ) {
        data.entries.forEach { (token, flagsForToken) ->
            val appliedFlagsKeyed: List<AppliedFlag> = flagsForToken.entries
                .filter { e ->
                    e.value.eventStatus == EventStatus.CREATED
                }
                .map { e -> AppliedFlag(e.key, e.value.time) }
            // TODO chunk size 20 is an arbitrary value, replace with appropriate size
            appliedFlagsKeyed.chunked(CHUNK_SIZE).forEach { appliedFlagsKeyedChunk ->
                changeFlagStatus(
                    token,
                    appliedFlagsKeyedChunk,
                    data,
                    EventStatus.SENDING
                )
                coroutineScope.launch(coroutineExceptionHandler) {
                    val result = client.apply(appliedFlagsKeyedChunk, token)
                    val event = FlagApplierBatchProcessedInput(token, appliedFlagsKeyedChunk, result)
                    sendChannel.send(event)
                }
            }
        }
    }

    private fun changeFlagStatus(
        token: String,
        appliedFlag: List<AppliedFlag>,
        data: FlagsAppliedMap,
        eventStatus: EventStatus
    ) {
        appliedFlag.forEach { applied ->
            data[token]?.let { map ->
                computeIfPresent(map, applied.flag) { _, v ->
                    v.copy(eventStatus = eventStatus)
                }
            }
        }
    }

    private fun changeSendingEventsToCreate(data: FlagsAppliedMap) = data.mapValues { entryValue ->
        entryValue.value
            .mapValues { entry ->
                if (entry.value.eventStatus == EventStatus.SENDING) {
                    entry.value.copy(eventStatus = EventStatus.CREATED)
                } else {
                    entry.value
                }
            }
            .toMutableMap()
    }.toMutableMap()

    private fun writeToFile(
        data: Map<String, MutableMap<String, ApplyInstance>>
    ) {
        // All apply events have been sent for this token, don't add this token to the file
        val toStoreData = data
            .filter {
                !it.value.values.all { applyInstance -> applyInstance.eventStatus == EventStatus.SENT }
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
                putIfAbsent(data, resolveToken, hashMapOf())
                data[resolveToken]?.let { map ->
                    putIfAbsent(map, flagName, applyInstance)
                }
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 20
    }
}

private fun computeIfPresent(
    map: MutableMap<String, ApplyInstance>,
    key: String,
    remappingFunction: (key: String, value: ApplyInstance) -> ApplyInstance?
): ApplyInstance? {
    map[key]?.let { oldValue ->
        val newValue = remappingFunction(key, oldValue)
        if (newValue != null) {
            map[key] = newValue
            return newValue
        } else {
            map.remove(key)
            return null
        }
    }
    return null
}

/**
 * Method to replicate Map.putIfAbsent() that requires later java version
 */
private fun putIfAbsent(map: FlagsAppliedMap, key: String, value: MutableMap<String, ApplyInstance>) {
    if (!map.containsKey(key)) {
        map[key] = value
    }
}

/**
 * Method to replicate Map.putIfAbsent() that requires later java version
 */
private fun putIfAbsent(map: MutableMap<String, ApplyInstance>?, key: String, value: ApplyInstance) {
    if (map == null) return
    if (!map.containsKey(key)) {
        map[key] = value
    }
}

internal val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
    }
}