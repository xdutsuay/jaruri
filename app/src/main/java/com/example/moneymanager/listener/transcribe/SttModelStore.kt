package com.example.moneymanager.listener.transcribe

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Holds the one active speech model under `filesDir/stt-model/current/`.
 * Models are Vosk `.zip` files: one bundled in the APK (see [BundledSttModels])
 * or one the user imports through the system file picker. The app never
 * downloads anything.
 *
 * Import is safe against zip-slip and oversized archives, validates the model
 * layout, and swaps it in only after it unpacked cleanly, so a failed import
 * leaves the previous model working.
 */
class SttModelStore(
    private val root: File,
    private val maxUncompressedBytes: Long = DEFAULT_MAX_BYTES
) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, ROOT_DIR))

    val currentDir: File get() = File(root, CURRENT)

    val installedName: String?
        get() {
            if (!isValidModelDir(currentDir)) return null
            val f = File(currentDir, NAME_FILE)
            return if (f.isFile) f.readText().trim().ifEmpty { null } else currentDir.name
        }

    val isInstalled: Boolean get() = installedName != null

    /** @return the installed model's name. */
    @Throws(IOException::class)
    fun import(input: InputStream, fallbackName: String): String {
        root.mkdirs()
        val staging = File(root, "staging-${System.nanoTime()}")
        try {
            unzipSafely(input, staging)
            val modelRoot = findModelRoot(staging)
                ?: throw IOException("This doesn't look like a Vosk model (missing am/final.mdl or conf/mfcc.conf).")
            val name = if (modelRoot != staging) modelRoot.name else fallbackName.removeSuffix(".zip")
            File(modelRoot, NAME_FILE).writeText(name)
            val old = File(root, "old-${System.nanoTime()}")
            if (currentDir.exists() && !currentDir.renameTo(old)) {
                throw IOException("Couldn't replace the current model.")
            }
            if (!modelRoot.renameTo(currentDir)) {
                old.renameTo(currentDir) // put the previous one back
                throw IOException("Couldn't install the model.")
            }
            old.deleteRecursively()
            return name
        } finally {
            staging.deleteRecursively()
        }
    }

    fun remove(): Boolean {
        currentDir.deleteRecursively()
        return !currentDir.exists()
    }

    private fun unzipSafely(input: InputStream, dest: File) {
        dest.mkdirs()
        val destPath = dest.canonicalPath + File.separator
        var total = 0L
        val buf = ByteArray(64 * 1024)
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(destPath)) {
                    throw IOException("Unsafe path in archive: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { os ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > maxUncompressedBytes) {
                                throw IOException("Model archive is too large.")
                            }
                            os.write(buf, 0, n)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun findModelRoot(staging: File): File? {
        if (isValidModelDir(staging)) return staging
        val children = staging.listFiles()?.filter { it.isDirectory }.orEmpty()
        return children.singleOrNull()?.takeIf { isValidModelDir(it) }
    }

    companion object {
        const val ROOT_DIR = "stt-model"
        private const val CURRENT = "current"
        private const val NAME_FILE = ".jaruri-model-name"
        const val DEFAULT_MAX_BYTES = 3L * 1024 * 1024 * 1024

        fun isValidModelDir(dir: File): Boolean =
            File(dir, "am/final.mdl").isFile && File(dir, "conf/mfcc.conf").isFile
    }
}
