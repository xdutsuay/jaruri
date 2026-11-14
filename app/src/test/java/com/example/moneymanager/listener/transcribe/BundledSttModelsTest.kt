package com.example.moneymanager.listener.transcribe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BundledSttModelsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SttModelStore

    @Before
    fun setUp() {
        store = SttModelStore(ctx)
        store.remove()
    }

    @Test
    fun byName_mapsInstalledDirToLanguage() {
        assertEquals(BundledSttModels.Model.HI, BundledSttModels.byName("vosk-model-small-hi-0.22"))
        assertNull(BundledSttModels.byName("some-imported-model"))
        assertNull(BundledSttModels.byName(null))
    }

    @Test
    fun ensureInstalled_thenSwitchLanguage_keepsOneValidModel() {
        val bundled = BundledSttModels.available(ctx)
        assumeTrue("built with -Pjaruri.stt.bundleModels=false", bundled.size == 2)

        assertEquals(BundledSttModels.Model.EN_IN.dirName, BundledSttModels.ensureInstalled(ctx, store))
        assertTrue(BundledSttModels.canTranscribe(ctx, store))
        // Already installed: no reinstall, same answer.
        assertEquals(BundledSttModels.Model.EN_IN.dirName, BundledSttModels.ensureInstalled(ctx, store))

        BundledSttModels.install(ctx, store, BundledSttModels.Model.HI)
        assertEquals(BundledSttModels.Model.HI.dirName, store.installedName)
        assertTrue(SttModelStore.isValidModelDir(store.currentDir))
        val leftovers = File(ctx.filesDir, SttModelStore.ROOT_DIR).list().orEmpty().filter { it != "current" }
        assertEquals(emptyList<String>(), leftovers)
    }
}
