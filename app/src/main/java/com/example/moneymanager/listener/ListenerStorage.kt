package com.example.moneymanager.listener

import android.content.Context
import java.io.File

/**
 * Files for Listener sessions under app-private `filesDir/listener/`.
 * Nothing here touches shared storage or the network.
 */
class ListenerStorage(val rootDir: File) {

    constructor(context: Context) : this(File(context.applicationContext.filesDir, ROOT_DIR))

    fun sessionDir(dirName: String): File = File(rootDir, dirName)

    fun ensureSessionDir(dirName: String): File =
        sessionDir(dirName).apply { mkdirs() }

    /** Non-empty chunk files in recording order. */
    fun segments(dirName: String): List<File> =
        sessionDir(dirName).listFiles()
            ?.filter { it.isFile && ListenerPolicy.isSegmentFile(it.name) && it.length() > 0 }
            ?.sortedBy { ListenerPolicy.segmentIndex(it.name) ?: Int.MAX_VALUE }
            .orEmpty()

    fun totalBytes(dirName: String): Long = segments(dirName).sumOf { it.length() }

    /** Removes the whole session folder. Returns true if nothing is left on disk. */
    fun deleteSession(dirName: String): Boolean {
        val dir = sessionDir(dirName)
        if (!dir.exists()) return true
        dir.deleteRecursively()
        return !dir.exists()
    }

    fun freeBytes(): Long {
        rootDir.mkdirs()
        return rootDir.usableSpace
    }

    companion object {
        const val ROOT_DIR = "listener"
    }
}
