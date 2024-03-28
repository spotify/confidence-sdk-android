package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream

internal interface EventStorage {
    suspend fun rollover()
    suspend fun writeEvent(event: Event)
    suspend fun batchReadyFiles(): List<File>
    suspend fun eventsFor(file: File): List<Event>
    fun onLowMemoryChannel(): Channel<List<File>>
    fun stop()
}

internal class EventStorageImpl(
    private val context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxStorage: Long = MAX_STORAGE
) : EventStorage {
    private lateinit var currentFile: File
    private var outputStream: OutputStream? = null
    private val semaphore: Semaphore = Semaphore(1)
    private val onLowMemoryChannel = Channel<List<File>>()
    private val coroutineScope = CoroutineScope(dispatcher)

    init {
        resetCurrentFile()
    }

    override suspend fun rollover() = withLock {
        outputStream?.flush()
        outputStream?.close()
        currentFile.renameTo(getFileWithName(currentFile.name + READY_TO_SENT_EXTENSION))
        resetCurrentFile()
    }

    override suspend fun writeEvent(event: Event) = withLock {
        val delimiter = EVENT_WRITE_DELIMITER
        val byteArray = (Json.encodeToString(event) + delimiter).toByteArray()
        outputStream?.write(byteArray)
        outputStream?.flush()
        coroutineScope.launch {
            val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
            val size = directory.walk().filter { it.isDirectory }.first().length()
            if (size > STORAGE_THRESHOLD * maxStorage) {
                onLowMemoryChannel.send(directory.walkFiles().toList())
            }
        }
    }

    override suspend fun batchReadyFiles(): List<File> {
        val list = mutableListOf<File>()
        withLock {
            val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
            for (file in directory.walk().iterator()) {
                if (file.name.endsWith(READY_TO_SENT_EXTENSION)) {
                    list.add(file)
                }
            }
        }
        return list
    }

    override suspend fun eventsFor(file: File): List<Event> {
        val text = file.readText()
        return text
            .split(EVENT_WRITE_DELIMITER)
            .filter { it.isNotEmpty() }
            .map { Json.decodeFromString(it) }
    }

    override fun onLowMemoryChannel(): Channel<List<File>> = onLowMemoryChannel
    override fun stop() {
        coroutineScope.cancel()
    }

    private fun latestWriteFile(): File? {
        val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
        for (file in directory.walk().iterator()) {
            if (!file.isDirectory) {
                if (!file.name.endsWith(READY_TO_SENT_EXTENSION) && !file.isDirectory) {
                    return file
                }
            }
        }
        return null
    }

    private suspend fun withLock(body: () -> Unit) {
        semaphore.acquire()
        body()
        semaphore.release()
    }

    private fun resetCurrentFile() {
        outputStream?.close()
        currentFile = latestWriteFile()
            ?: getFileWithName("events-${System.currentTimeMillis()}")
        outputStream = currentFile.outputStream()
    }
    private fun getFileWithName(name: String): File {
        val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
        return File(directory, name)
    }

    companion object {
        const val DIRECTORY = "events"
        const val READY_TO_SENT_EXTENSION = ".ready"
        const val EVENT_WRITE_DELIMITER = ",\n"
        const val MAX_STORAGE: Long = 4L * 1024 * 1024 // 4MB
        const val STORAGE_THRESHOLD = 0.9
    }
}

internal fun File.walkFiles() = walk().filter { !it.isDirectory }