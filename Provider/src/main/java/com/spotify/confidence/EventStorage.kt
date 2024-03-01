package com.spotify.confidence

import android.content.Context
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

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
    private val semaphore: Semaphore = Semaphore(1)

    init {
        resetCurrentFile(false)
    }

    override suspend fun rollover() = withLock {
        currentFile.renameTo(getFileWithName(currentFile.name + READY_TO_SENT_EXTENSION))
        resetCurrentFile(true)
    }

    override suspend fun writeEvent(event: Event) = withLock {
        val delimiter = EVENT_WRITE_DELIMITER
        currentFile.writeText(eventsJson.encodeToString(event) + delimiter)
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

    override suspend fun eventsFor(file: File): List<Event> = file.readText()
        .split(EVENT_WRITE_DELIMITER)
        .map { eventsJson.decodeFromString(it) }

    private fun maxIndex(): Int {
        val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
        var maxIndex = 0
        for (file in directory.walk().iterator()) {
            if (!file.name.endsWith(READY_TO_SENT_EXTENSION)) {
                val index = indexForFile(file)
                if (maxIndex < index) {
                    maxIndex = index
                }
            }
        }

        return maxIndex
    }

    private fun indexForFile(file: File): Int {
        return file.name.split("-")[1].toInt()
    }

    private suspend fun withLock(body: () -> Unit) {
        semaphore.acquire()
        body()
        semaphore.release()
    }

    private fun resetCurrentFile(newFile: Boolean) {
        val maxIndex = maxIndex()
        val index = if (newFile) {
            maxIndex + 1
        } else {
            maxIndex
        }
        currentFile = getFileWithName(index.toString())
    }
    private fun getFileWithName(name: String): File {
        val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
        return File(directory, "events-$name")
    }

    companion object {
        const val DIRECTORY = "events"
        const val READY_TO_SENT_EXTENSION = "ready"
        const val EVENT_WRITE_DELIMITER = ",\n"
    }
}