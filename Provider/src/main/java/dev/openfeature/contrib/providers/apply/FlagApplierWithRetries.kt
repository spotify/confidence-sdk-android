package dev.openfeature.contrib.providers.apply

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.DateTypeAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.File
import java.time.Instant
import java.util.*

const val APPLY_FILE_NAME = "confidence_apply_cache.json"

private sealed class FlagApplierEvent {
    data class WriteRequest(
        val flagName: String,
        val resolveToken: String
    ) : FlagApplierEvent()

    data class DataSentRequest(
        val resolveToken: String,
        val flags: List<Pair<UUID, AppliedFlag>>
    ) : FlagApplierEvent()
}

private typealias FlagsAppliedMap =
    MutableMap<String, MutableMap<String, MutableMap<UUID, Instant>>>

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val dispatcher: CoroutineDispatcher,
    context: Context
) : FlagApplier {
    private val coroutineScope: CoroutineScope by lazy {
        // the SupervisorJob makes sure that if one coroutine
        // throw an error, the whole scope is not cancelled
        // the thrown error can be handled individually
        CoroutineScope(SupervisorJob() + dispatcher)
    }

    private val writeRequestChannel: Channel<FlagApplierEvent.WriteRequest> = Channel()
    private val dataSentChannel: Channel<FlagApplierEvent.DataSentRequest> = Channel()
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Date::class.java, DateTypeAdapter())
        .create()

    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, _ -> }
    }

    init {
        coroutineScope.launch {
            // the data being in a coroutine like this
            // ensures we don't have any shared mutability
            val data: FlagsAppliedMap = mutableMapOf()
            readFile(data)

            // the select clause makes sure that we don't
            // share the data/file write operations between coroutines
            // at any certain time there is only one of these events being handled
            while (true) {
                select {
                    writeRequestChannel.onReceive { writeRequest ->
                        with(writeRequest) {
                            internalApply(flagName, resolveToken, data)
                        }
                    }

                    dataSentChannel.onReceive { event ->
                        event.flags.forEach {
                            data[event.resolveToken]?.get(it.second.flag)?.remove(it.first)
                        }
                        writeToFile(data)
                    }
                }
            }
        }
    }

    override fun apply(flagName: String, resolveToken: String) {
        coroutineScope.launch {
            writeRequestChannel.send(FlagApplierEvent.WriteRequest(flagName, resolveToken))
        }
    }

    private fun internalApply(
        flagName: String,
        resolveToken: String,
        data: FlagsAppliedMap
    ) {
        data.putIfAbsent(resolveToken, hashMapOf())
        data[resolveToken]?.putIfAbsent(flagName, hashMapOf())
        // Never delete entries from the maps above, only add. This should prevent racy conditions
        // Empty entries are not added to the cache file, so empty entries are removed when restarting
        data[resolveToken]?.get(flagName)?.putIfAbsent(UUID.randomUUID(), Instant.now())
        writeToFile(data)
        triggerSendBatch(data)
    }

    private fun triggerSendBatch(
        data: FlagsAppliedMap
    ) {
        data.entries.forEach { (token, flagsForToken) ->
            val appliedFlagsKeyed = flagsForToken.entries.flatMap { (flagName, events) ->
                events.entries.map { (uuid, date) ->
                    Pair(uuid, AppliedFlag(flagName, date))
                }
            }
            // TODO chunk size 20 is an arbitrary value, replace with appropriate size
            appliedFlagsKeyed.chunked(20).forEach { appliedFlagsKeyedChunk ->
                coroutineScope.launch(exceptionHandler) {
                    client.apply(appliedFlagsKeyedChunk.map { it.second }, token)
                    dataSentChannel.send(FlagApplierEvent.DataSentRequest(token, appliedFlagsKeyedChunk))
                }
            }
        }
    }

    private fun writeToFile(
        data: FlagsAppliedMap
    ) {
        val fileData = gson.toJson(
            data.filter {
                // All flags entries are empty for this token, don't add this token to the file
                !it.value.values.stream().allMatch { events -> events.isEmpty() }
            }
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
        val type = object :
            TypeToken<Map<String, Map<String, Map<UUID, Instant>>>>() {}.type
        val newData: Map<String, Map<String, Map<UUID, Instant>>> =
            gson.fromJson(fileText, type)
        newData.entries.forEach { (resolveToken, eventsByFlagName) ->
            eventsByFlagName.entries.forEach { (flagName, eventTimeEntries) ->
                data.putIfAbsent(resolveToken, hashMapOf())
                data[resolveToken]?.putIfAbsent(flagName, hashMapOf())
                eventTimeEntries.forEach { (id, time) ->
                    data[resolveToken]?.get(flagName)?.putIfAbsent(id, time)
                }
            }
        }
    }
}