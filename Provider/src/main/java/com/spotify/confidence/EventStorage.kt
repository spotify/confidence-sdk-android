package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.OutputStream

internal interface EventStorage {
    suspend fun rollover()
    suspend fun writeEvent(event: Event)
    suspend fun batchReadyFiles(): List<File>
    suspend fun eventsFor(file: File): List<Event>
}

internal class EventStorageImpl(
    private val context: Context
) : EventStorage {
    private lateinit var currentFile: File
    private var outputStream: OutputStream? = null
    private val semaphore: Semaphore = Semaphore(1)

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
        val byteArray = (eventsJson.encodeToString(event) + delimiter).toByteArray()
        outputStream?.write(byteArray)
        outputStream?.flush()
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
            .map { eventsJson.decodeFromString(it) }
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
    }
}