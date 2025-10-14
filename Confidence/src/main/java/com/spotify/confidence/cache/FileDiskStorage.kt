package com.spotify.confidence.cache

import android.content.Context
import com.spotify.confidence.FlagResolution
import com.spotify.confidence.apply.ApplyInstance
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal const val FLAGS_FILE_NAME = "confidence_flags_cache.json"
internal const val APPLY_FILE_NAME = "confidence_apply_cache.json"

internal class FileDiskStorage internal constructor(
    private val flagsFile: File,
    private val applyFile: File
) : DiskStorage {
    private val lock = ReentrantReadWriteLock()

    override fun store(flagResolution: FlagResolution) {
        write(Json.encodeToString(flagResolution))
    }

    override fun clear() {
        lock.write {
            flagsFile.delete()
        }
    }

    override fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>) {
        applyFile.writeText(Json.encodeToString(applyData))
    }

    override fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>> {
        if (!applyFile.exists()) return mutableMapOf()
        val fileText: String = applyFile.bufferedReader().use { it.readText() }
        return if (fileText.isEmpty()) {
            mutableMapOf()
        } else {
            try {
                Json.decodeFromString(fileText)
            } catch (e: Throwable) {
                applyFile.delete()
                mutableMapOf()
            }
        }
    }

    private fun write(data: String) = lock.write {
        flagsFile.writeText(data)
    }

    override fun read(): FlagResolution = lock.read {
        try {
            if (!flagsFile.exists()) return FlagResolution.EMPTY
            val fileText: String = flagsFile.bufferedReader().use { it.readText() }
            return if (fileText.isEmpty()) {
                FlagResolution.EMPTY
            } else {
                try {
                    Json.decodeFromString(fileText)
                } catch (e: Throwable) {
                    // Delete corrupted file - safe to do while holding read lock
                    // since we're the only thread accessing it
                    flagsFile.delete()
                    FlagResolution.EMPTY
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            // File was deleted between exists check and read
            return FlagResolution.EMPTY
        }
    }

    companion object {
        fun create(context: Context): DiskStorage {
            return FileDiskStorage(
                flagsFile = File(context.filesDir, FLAGS_FILE_NAME),
                applyFile = File(context.filesDir, APPLY_FILE_NAME)
            )
        }
    }
}