package com.example.liftbook.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupChunksTest {

    @Test
    fun `a document comes back as it went`() {
        val document = """{"format":"liftbook-backup","formatVersion":1,"exercises":[{"name":"Épaulé-jeté"}]}"""

        assertEquals(document, BackupChunks.join(BackupChunks.split(document)))
    }

    @Test
    fun `a large document is cut into pieces no bigger than the limit, and joins back whole`() {
        val document = (0 until 30_000).joinToString(",") { "${it.toString(36)}-${it * 7919 % 10_007}" }

        val chunks = BackupChunks.split(document, maxChunkBytes = 1_000)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.size <= 1_000 })
        assertEquals(document, BackupChunks.join(chunks))
    }

    @Test
    fun `pieces are compressed`() {
        val document = "{\"workouts\":[" + (1..500).joinToString(",") { "{\"id\":\"w$it\",\"name\":\"Push\",\"sets\":[{\"weightKg\":80.0,\"reps\":8}]}" } + "]}"

        val chunks = BackupChunks.split(document)

        assertTrue(chunks.sumOf { it.size } < document.length / 4)
    }

    @Test
    fun `an empty document is still one piece`() {
        val chunks = BackupChunks.split("")

        assertEquals(1, chunks.size)
        assertEquals("", BackupChunks.join(chunks))
    }
}
