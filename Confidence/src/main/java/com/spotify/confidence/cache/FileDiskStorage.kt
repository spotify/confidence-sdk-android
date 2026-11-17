package com.spotify.confidence.cache

import android.content.Context
import android.os.Build
import com.spotify.confidence.FlagResolution
import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.client.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Date
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal const val FLAGS_FILE_NAME = "confidence_flags_cache.json"
internal const val APPLY_FILE_NAME = "confidence_apply_cache.json"
internal const val RESOLVE_METADATA_FILE_NAME = "confidence_flags_cache_metadata.json"

internal class FileDiskStorage internal constructor(
    private val flagsFile: File,
    private val applyFile: File,
    private val resolveMetadataFile: File,
    private val clock: Clock = Clock.CalendarBacked.systemUTC()
) : DiskStorage, ResolveStorageMetadataStorage {
    private val lock = ReentrantReadWriteLock()
    private val metadataJson = Json { ignoreUnknownKeys = true }

    override fun store(flagResolution: FlagResolution) {
        write(Json.encodeToString(flagResolution))
    }

    override fun clear() {
        lock.write {
            flagsFile.delete()
            resolveMetadataFile.delete()
        }
    }

    override fun markFetched() = lock.write {
        writeResolveMetadata()
    }

    private fun writeResolveMetadata() {
        resolveMetadataFile.writeText(
            metadataJson.encodeToString(
                StoredResolveStorageMetadata(lastFetchedAtMillis = clock.currentTime().time)
            )
        )
    }

    override fun getLastFetchedAt(): Date? = lock.read {
        if (!resolveMetadataFile.exists()) return null
        runCatching {
            metadataJson.decodeFromString<StoredResolveStorageMetadata>(resolveMetadataFile.readText())
                .lastFetchedAtMillis
                .let(::Date)
        }.getOrNull()
    }

    private fun write(data: String) = lock.write {
        flagsFile.writeAtomically(data)
    }

    override fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>) = lock.write {
        applyFile.writeAtomically(Json.encodeToString(applyData))
    }

    override fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>> = lock.read {
        if (!applyFile.exists()) return mutableMapOf()
        val fileText: String = try {
            applyFile.bufferedReader().use { it.readText() }
        } catch (e: FileNotFoundException) {
            // The file vanished between the exists() check and opening it.
            return mutableMapOf()
        }
        return if (fileText.isEmpty()) {
            mutableMapOf()
        } else {
            try {
                Json.decodeFromString(fileText)
            } catch (e: Throwable) {
                // Atomic writes plus the read lock rule out torn reads, so a decode
                // failure means the file is genuinely corrupt or incompatible. Leave it
                // on disk: deleting it would silently destroy persisted apply state.
                mutableMapOf()
            }
        }
    }

    override fun read(): FlagResolution = lock.read {
        if (!flagsFile.exists()) return FlagResolution.EMPTY
        val fileText: String = try {
            flagsFile.bufferedReader().use { it.readText() }
        } catch (e: FileNotFoundException) {
            // The file vanished between the exists() check and opening it.
            return FlagResolution.EMPTY
        }
        return if (fileText.isEmpty()) {
            FlagResolution.EMPTY
        } else {
            try {
                Json.decodeFromString(fileText)
            } catch (e: Throwable) {
                // Delete corrupted file - safe to do while holding read lock
                // since we're the only thread accessing it
                flagsFile.delete()
                resolveMetadataFile.delete()
                FlagResolution.EMPTY
            }
        }
    }

    companion object {
        fun create(context: Context): DiskStorage {
            return FileDiskStorage(
                flagsFile = File(context.filesDir, FLAGS_FILE_NAME),
                applyFile = File(context.filesDir, APPLY_FILE_NAME),
                resolveMetadataFile = File(context.filesDir, RESOLVE_METADATA_FILE_NAME)
            )
        }
    }
}

/**
 * Writes [data] to this file atomically: the content is written to a sibling temp file
 * that is then renamed over this file. Renaming within a directory is atomic, so
 * readers that do not take the storage lock (e.g. the confidence-sdk-flutter plugin
 * reading the raw cache file) observe either the previous or the new content, never
 * a truncated or partially written file.
 */
private fun File.writeAtomically(data: String) {
    val tempFile = File(parentFile, "$name.tmp")
    tempFile.writeText(data)
    val renamed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            Files.move(tempFile.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (e: AtomicMoveNotSupportedException) {
            // File systems without ATOMIC_MOVE support fall back to File.renameTo.
            tempFile.renameTo(this)
        }
    } else {
        // java.nio.file is only available from API 26. Renaming within the same
        // directory is atomic on POSIX file systems, which File.renameTo relies on.
        tempFile.renameTo(this)
    }
    check(renamed) { "Failed to replace $this with $tempFile" }
}
