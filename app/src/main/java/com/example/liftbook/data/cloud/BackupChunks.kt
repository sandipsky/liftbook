package com.example.liftbook.data.cloud

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A backup document as Firestore can hold it (FR-8.5): compressed, then cut into pieces small
 * enough for one document each. Firestore caps a document at 1 MiB, and a year of logging is a
 * few megabytes of JSON — but it compresses about tenfold, so most backups are one piece.
 */
internal object BackupChunks {

    /** Under Firestore's 1 MiB document limit, with room for the document's other fields. */
    const val MAX_CHUNK_BYTES = 900_000

    fun split(document: String, maxChunkBytes: Int = MAX_CHUNK_BYTES): List<ByteArray> {
        require(maxChunkBytes > 0)
        val compressed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(document.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return (compressed.indices step maxChunkBytes).map { start ->
            compressed.copyOfRange(start, minOf(start + maxChunkBytes, compressed.size))
        }
    }

    /** The document back from its pieces, in order. Throws [java.io.IOException] if they don't make one. */
    fun join(chunks: List<ByteArray>): String {
        val compressed = ByteArrayOutputStream().also { out -> chunks.forEach(out::write) }.toByteArray()
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }.toString(Charsets.UTF_8)
    }
}
