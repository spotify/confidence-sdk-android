package dev.openfeature.contrib.providers.apply

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.InstantTypeAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

const val APPLY_FILE_NAME = "confidence_apply_cache.json"

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val applyDispatcher: CoroutineDispatcher,
    private val context: Context) : FlagApplier {
    private var data : ApplyCacheData = ApplyCacheData()
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting() // TODO remove
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    init {
        readFile()
    }

    // TODO Ensure this fun is not called on a main thread
    override fun apply(flagName: String, resolveToken: String) {
        val newEvent = ApplyEvent(flagName, resolveToken, Instant.now())
        data.events.add(newEvent)
        writeToFile()
        try {
            triggerBatch()
        } catch (_: Throwable) {
            // "triggerBatch" should not introduce bad state in case of any failure, will retry later
        }
    }


    data class ApplyEvent(
        var flagName: String,
        var resolveToken: String,
        var applyTime: Instant
    )

    data class ApplyCacheData(
        var events: MutableList<ApplyEvent> = mutableListOf()
    )

    // TODO Define the logic on when / how often to call this function
    // This function should never introduce bad state and any type of error is recoverable on the next try
    fun triggerBatch() {
        val groupByResolveToken: Map<String, List<ApplyEvent>> = data.events.groupBy {
                entry -> entry.resolveToken
        }
        groupByResolveToken.forEach { entry ->
            val appliedFlags = entry.value.map { flag -> AppliedFlag(flag.flagName, flag.applyTime) }
            val handler = CoroutineExceptionHandler { _, _ ->
                // "triggerBatch" should not introduce bad state in case of any failure, will retry later
            }
            CoroutineScope(applyDispatcher).launch(handler) {
                // TODO Check size limit, cap max amount of flags per request
                client.apply(appliedFlags, entry.key)
                entry.value.forEach { flag ->
                    data.events = data.events.stream().filter { e -> e.resolveToken != entry.key }
                        .toList()
                        .toMutableList()
                }
                writeToFile()
            }
        }
    }

    private fun writeToFile() {
        val fileData = gson.toJson(data)
        file.writeText(fileData)
    }

    private fun readFile() {
        if (!file.exists()) return
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return
        val type = object : TypeToken<ApplyCacheData>() {}.type
        data = gson.fromJson(fileText, type)
    }
}