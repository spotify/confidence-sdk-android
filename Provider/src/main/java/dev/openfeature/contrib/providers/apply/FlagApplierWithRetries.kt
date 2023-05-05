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

const val APPLY_FILE_NAME = "confidence_apply_cache.json"

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val applyDispatcher: CoroutineDispatcher,
    context: Context) : FlagApplier {
    private var data : MutableMap<String, MutableMap<String, MutableList<Instant>>> = mutableMapOf()
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
        // TODO Is this thread safe?
        data
            .getOrPut(resolveToken) { mutableMapOf() }
            .getOrPut(flagName) { mutableListOf() }
            .add(Instant.now())
        writeToFile()
        try {
            triggerBatch()
        } catch (_: Throwable) {
            // "triggerBatch" should not introduce bad state in case of any failure, will retry later
        }
    }

    data class ApplyCacheData(
        var events: MutableMap<String, MutableMap<String, MutableList<Instant>>> = mutableMapOf()
    )

    // TODO Define the logic on when / how often to call this function
    // This function should never introduce bad state and any type of error is recoverable on the next try
    fun triggerBatch() {
        data.forEach { entry ->
            val appliedFlags = entry.value.flatMap { flag ->
                flag.value.map { time -> AppliedFlag(flag.key, time) }
            }
            val handler = CoroutineExceptionHandler { _, _ ->
                // "triggerBatch" should not introduce bad state in case of any failure, will retry later
            }
            CoroutineScope(applyDispatcher).launch(handler) {
                // TODO Check size limit, cap max amount of flags per request
                client.apply(appliedFlags, entry.key)
                data.remove(entry.key) // TODO more safely delete individual entries
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