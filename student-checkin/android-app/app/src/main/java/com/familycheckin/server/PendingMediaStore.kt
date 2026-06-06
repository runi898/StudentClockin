package com.familycheckin.server

import android.content.Context
import java.io.File
import java.util.UUID

interface PendingMediaStore {
    fun save(fileName: String, bytes: ByteArray): String
    fun read(key: String): ByteArray?
    fun delete(key: String)
    fun clear()
}

class FilePendingMediaStore(context: Context) : PendingMediaStore {
    private val rootDirectory = File(context.applicationContext.filesDir, "pending-sync-media").apply {
        mkdirs()
    }

    override fun save(fileName: String, bytes: ByteArray): String {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "media.bin" }
        val key = "${UUID.randomUUID()}-$safeName"
        File(rootDirectory, key).writeBytes(bytes)
        return key
    }

    override fun read(key: String): ByteArray? {
        val file = File(rootDirectory, key)
        return if (file.exists()) file.readBytes() else null
    }

    override fun delete(key: String) {
        File(rootDirectory, key).delete()
    }

    override fun clear() {
        rootDirectory.listFiles()?.forEach(File::delete)
    }
}
