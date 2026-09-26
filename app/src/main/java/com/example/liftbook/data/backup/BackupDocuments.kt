package com.example.liftbook.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.liftbook.domain.model.DocumentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Reads and writes the files a backup goes to and comes from. They're picked with the system's
 * file picker (the Storage Access Framework), so LiftBook needs no storage permission, and the
 * file can be anywhere a document provider reaches: this phone, an SD card, a USB drive.
 */
interface BackupDocuments {

    /** The whole of [uri] as UTF-8 text. Throws [IOException] if it can't be read. */
    fun read(uri: DocumentUri): String

    /** Replaces the contents of [uri] with [text]. Throws [IOException] if it can't be written. */
    fun write(uri: DocumentUri, text: String)
}

class ContentResolverBackupDocuments @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupDocuments {

    override fun read(uri: DocumentUri): String {
        val input = context.contentResolver.openInputStream(uri.value.toUri()) ?: throw IOException("Can't open the backup")
        return input.use { it.readCapped(MAX_BACKUP_BYTES) }.toString(Charsets.UTF_8)
    }

    override fun write(uri: DocumentUri, text: String) {
        openForWriting(uri.value.toUri()).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    /** "wt" truncates an existing file, so a shorter backup can't leave the old one's tail; not every provider has it. */
    private fun openForWriting(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return stream ?: resolver.openOutputStream(uri, "w") ?: throw IOException("Can't write the backup")
    }
}

/**
 * Reads at most [limit] bytes. Far beyond any real history — about 14,000 workouts — but it
 * stops a wrong pick, like a video, from filling memory.
 */
internal fun InputStream.readCapped(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) throw IOException("Too large to be a backup")
        out.write(buffer, 0, read)
    }
}

private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
private const val BUFFER_BYTES = 64 * 1024
