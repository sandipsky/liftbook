package com.example.liftbook.data.cloud

import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.repository.CloudBackupRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Backups in Cloud Firestore (FR-8.5), one per account:
 *
 * ```
 * users/{accountId}                       when it was saved, what it holds, and which pieces make it
 * users/{accountId}/chunks/{gen}-{index}  the compressed document, in pieces (BackupChunks)
 * ```
 *
 * The pieces of each upload carry a fresh generation id, and the account document names it only
 * once they're all in place — so a reader never mixes two uploads, and an upload that dies
 * halfway leaves the previous backup whole. Pieces of other generations are swept up afterwards.
 * The security rules (firebase/firestore.rules) let an account reach its own path and no other.
 */
class FirestoreBackupRepository(private val firestore: FirebaseFirestore) : CloudBackupRepository {

    override suspend fun describe(accountId: String): CloudBackupInfo? = firebaseCall {
        userDocument(accountId).get(Source.SERVER).await().toInfo()
    }

    override suspend fun upload(accountId: String, snapshot: BackupSnapshot) {
        firebaseCall {
            val user = userDocument(accountId)
            val chunks = BackupChunks.split(snapshot.document)
            val generation = UUID.randomUUID().toString()
            chunks.withIndex().chunked(CHUNKS_PER_BATCH).forEach { batchChunks ->
                val batch = firestore.batch()
                batchChunks.forEach { (index, bytes) ->
                    batch.set(
                        user.collection(CHUNKS).document(chunkId(generation, index)),
                        mapOf(FIELD_GENERATION to generation, FIELD_INDEX to index, FIELD_DATA to Blob.fromBytes(bytes)),
                    )
                }
                batch.commit().await()
            }
            user.set(
                mapOf(
                    FIELD_GENERATION to generation,
                    FIELD_CHUNK_COUNT to chunks.size,
                    FIELD_SAVED_AT to snapshot.exportedAt.toTimestamp(),
                    FIELD_WORKOUTS to snapshot.counts.workouts,
                    FIELD_ROUTINES to snapshot.counts.routines,
                    FIELD_CUSTOM_EXERCISES to snapshot.counts.customExercises,
                    FIELD_WEIGH_INS to snapshot.counts.weighIns,
                    FIELD_SCHEDULES to snapshot.counts.schedules,
                ),
            ).await()
            sweep(user, keep = generation)
        }
    }

    override suspend fun download(accountId: String): BackupSnapshot? = firebaseCall {
        val user = userDocument(accountId)
        val meta = user.get(Source.SERVER).await()
        val info = meta.toInfo() ?: return@firebaseCall null
        val generation = meta.getString(FIELD_GENERATION) ?: throw AccountException(AccountProblem.UNKNOWN)
        val count = meta.getLong(FIELD_CHUNK_COUNT)?.toInt() ?: throw AccountException(AccountProblem.UNKNOWN)
        val chunks = (0 until count).map { index ->
            user.collection(CHUNKS).document(chunkId(generation, index)).get(Source.SERVER).await()
                .getBlob(FIELD_DATA)?.toBytes()
                ?: throw AccountException(AccountProblem.UNKNOWN)
        }
        val document = try {
            BackupChunks.join(chunks)
        } catch (e: IOException) {
            throw AccountException(AccountProblem.UNKNOWN, e)
        }
        BackupSnapshot(document, info.savedAt, info.counts)
    }

    /** Pieces of earlier backups, and of uploads that never finished. Best effort: the backup is already in. */
    private suspend fun sweep(user: DocumentReference, keep: String) {
        val stale = user.collection(CHUNKS).whereNotEqualTo(FIELD_GENERATION, keep).get(Source.SERVER).await()
        stale.documents.chunked(DELETES_PER_BATCH).forEach { documents ->
            val batch = firestore.batch()
            documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun userDocument(accountId: String): DocumentReference = firestore.collection(USERS).document(accountId)

    private fun DocumentSnapshot.toInfo(): CloudBackupInfo? {
        if (!exists()) return null
        val savedAt = getTimestamp(FIELD_SAVED_AT) ?: return null
        return CloudBackupInfo(
            savedAt = savedAt.toInstant(),
            counts = DataCounts(
                workouts = getLong(FIELD_WORKOUTS)?.toInt() ?: 0,
                routines = getLong(FIELD_ROUTINES)?.toInt() ?: 0,
                customExercises = getLong(FIELD_CUSTOM_EXERCISES)?.toInt() ?: 0,
                weighIns = getLong(FIELD_WEIGH_INS)?.toInt() ?: 0,
                schedules = getLong(FIELD_SCHEDULES)?.toInt() ?: 0,
            ),
        )
    }

    /** Zero-padded, so the pieces list in order wherever they're looked at. */
    private fun chunkId(generation: String, index: Int) = "$generation-${index.toString().padStart(CHUNK_INDEX_DIGITS, '0')}"

    private fun Instant.toTimestamp() = Timestamp(epochSecond, nano)

    private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())

    companion object {
        /**
         * Firestore configured for a backup store: no cache on disk, so what's read is what the
         * server holds and nothing of one account lingers for the next.
         */
        fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }

        private const val USERS = "users"
        private const val CHUNKS = "chunks"
        private const val FIELD_GENERATION = "generation"
        private const val FIELD_INDEX = "index"
        private const val FIELD_DATA = "data"
        private const val FIELD_CHUNK_COUNT = "chunkCount"
        private const val FIELD_SAVED_AT = "savedAt"
        private const val FIELD_WORKOUTS = "workouts"
        private const val FIELD_ROUTINES = "routines"
        private const val FIELD_CUSTOM_EXERCISES = "customExercises"
        private const val FIELD_WEIGH_INS = "weighIns"
        private const val FIELD_SCHEDULES = "schedules"
        private const val CHUNK_INDEX_DIGITS = 6

        /** Well inside Firestore's 10 MiB request limit, at 900 KB a piece. */
        private const val CHUNKS_PER_BATCH = 4
        private const val DELETES_PER_BATCH = 400
    }
}
