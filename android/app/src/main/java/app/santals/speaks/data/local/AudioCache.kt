package app.santals.speaks.data.local

import android.content.Context
import app.santals.speaks.domain.model.SpeechException
import app.santals.speaks.domain.model.SpeechFailureKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private store for generated speech. Files are named "<cacheKey>.<extension>".
 * Writes go to a temporary file first and are renamed only when complete, so a crash never leaves
 * a half-written clip that would be played later.
 */
@Singleton
class AudioCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir: File = File(context.filesDir, "speech-cache").apply { mkdirs() }

    /** Returns the cached file for [key], or null. Never throws for a missing directory. */
    fun find(key: String): File? {
        if (!isValidKey(key)) return null
        return dir.listFiles { f -> f.name.startsWith("$key.") && !f.name.endsWith(".tmp") }?.firstOrNull()
    }

    /** Writes audio atomically and returns the final file. Throws [SpeechException] on storage errors. */
    fun write(key: String, bytes: ByteArray, extension: String): File {
        require(isValidKey(key)) { "invalid cache key" }
        require(extension.matches(Regex("[a-z0-9]{2,4}"))) { "invalid extension" }
        if (dir.usableSpace < bytes.size.toLong() * 2) {
            throw SpeechException(SpeechFailureKind.STORAGE_FULL, "usableSpace too small")
        }
        val target = File(dir, "$key.$extension")
        val tmp = File(dir, "$key.$extension.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                throw IOException("rename failed")
            }
            target.setLastModified(System.currentTimeMillis())
            return target
        } catch (e: IOException) {
            throw SpeechException(SpeechFailureKind.STORAGE_FULL, e.javaClass.simpleName)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Marks a file as recently used (LRU eviction). */
    fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    fun delete(key: String) {
        if (!isValidKey(key)) return
        dir.listFiles { f -> f.name.startsWith("$key.") }?.forEach { it.delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Evicts least-recently-used clips until the total is within [maxBytes]. */
    fun enforceLimit(maxBytes: Long) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun isValidKey(key: String): Boolean = key.matches(Regex("[0-9a-f]{64}"))
}
