package com.example.liftbook.testing

import com.example.liftbook.data.backup.BackupDocuments
import com.example.liftbook.domain.model.DocumentUri
import java.io.IOException

/** Files the user "picked", kept in memory by URI. */
class FakeBackupDocuments : BackupDocuments {
    val files = mutableMapOf<DocumentUri, String>()

    /** When set, reading or writing throws it, as a provider that's gone away would. */
    var failure: IOException? = null

    override fun read(uri: DocumentUri): String {
        failure?.let { throw it }
        return files[uri] ?: throw IOException("No such document: ${uri.value}")
    }

    override fun write(uri: DocumentUri, text: String) {
        failure?.let { throw it }
        files[uri] = text
    }
}
