package com.example.moneymanager.listener.transcribe

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SttModelStoreTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("stt").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, body) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(bos.toByteArray())
    }

    private fun model(prefix: String) = arrayOf(
        "$prefix/am/final.mdl" to "x",
        "$prefix/conf/mfcc.conf" to "y",
        "$prefix/graph/HCLr.fst" to "z"
    )

    @Test
    fun importsModelInsideTopFolder() {
        val store = SttModelStore(root)
        assertNull(store.installedName)
        val name = store.import(zip(*model("vosk-model-small-en-in-0.4")), "file.zip")
        assertEquals("vosk-model-small-en-in-0.4", name)
        assertEquals(name, store.installedName)
        assertTrue(File(store.currentDir, "am/final.mdl").isFile)
        assertEquals(listOf("current"), root.list()!!.toList()) // staging cleaned up
    }

    @Test
    fun importsFlatModelUsingFileName() {
        val store = SttModelStore(root)
        val flat = model("x").map { it.first.removePrefix("x/") to it.second }.toTypedArray()
        assertEquals("my-model", store.import(zip(*flat), "my-model.zip"))
    }

    @Test
    fun rejectsZipSlip_andKeepsPreviousModel() {
        val store = SttModelStore(root)
        store.import(zip(*model("good")), "good.zip")
        try {
            store.import(zip(*model("bad"), "../../evil.txt" to "pwn"), "bad.zip")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Unsafe path"))
        }
        assertFalse(File(root.parentFile, "evil.txt").exists())
        assertEquals("good", store.installedName)
    }

    @Test
    fun rejectsNonModelArchive() {
        val store = SttModelStore(root)
        try {
            store.import(zip("readme.txt" to "hello"), "x.zip")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Vosk model"))
        }
        assertNull(store.installedName)
    }

    @Test
    fun rejectsOversizedArchive() {
        val store = SttModelStore(root, maxUncompressedBytes = 10)
        try {
            store.import(zip(*model("m"), "m/big.bin" to "x".repeat(100)), "m.zip")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("too large"))
        }
        assertNull(store.installedName)
    }

    @Test
    fun replaceAndRemove() {
        val store = SttModelStore(root)
        store.import(zip(*model("one")), "one.zip")
        store.import(zip(*model("two")), "two.zip")
        assertEquals("two", store.installedName)
        assertTrue(store.remove())
        assertNull(store.installedName)
    }
}
