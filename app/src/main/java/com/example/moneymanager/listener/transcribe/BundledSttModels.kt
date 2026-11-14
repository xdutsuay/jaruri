package com.example.moneymanager.listener.transcribe

import android.content.Context
import androidx.annotation.StringRes
import com.example.moneymanager.R
import java.io.IOException

/**
 * Speech models shipped inside the APK (`assets/stt/<dirName>.zip`, added at build
 * time by `fetchSttModels`). Only one is unpacked into [SttModelStore] at a time;
 * the first one available is installed automatically on first use.
 *
 * Builds made with `-Pjaruri.stt.bundleModels=false` have no assets here, and the
 * user imports a model by hand as before.
 */
object BundledSttModels {
    enum class Model(val dirName: String, @StringRes val label: Int) {
        EN_IN("vosk-model-small-en-in-0.4", R.string.listener_stt_lang_en_in),
        HI("vosk-model-small-hi-0.22", R.string.listener_stt_lang_hi);

        val asset: String get() = "$ASSET_DIR/$dirName.zip"
    }

    private const val ASSET_DIR = "stt"

    fun available(context: Context): List<Model> {
        val files = try {
            context.assets.list(ASSET_DIR).orEmpty().toSet()
        } catch (_: IOException) {
            emptySet()
        }
        return Model.values().filter { "${it.dirName}.zip" in files }
    }

    /** The bundled model with this installed name, if any. */
    fun byName(name: String?): Model? = Model.values().firstOrNull { it.dirName == name }

    /** True if transcription can run now or after an automatic install. */
    fun canTranscribe(context: Context, store: SttModelStore = SttModelStore(context)): Boolean =
        store.isInstalled || available(context).isNotEmpty()

    /** Unpacks [model] into [store], replacing the current model. */
    @Synchronized
    @Throws(IOException::class)
    fun install(context: Context, store: SttModelStore, model: Model): String =
        context.assets.open(model.asset).use { store.import(it, model.dirName) }

    /**
     * Installs the first bundled model if none is installed.
     * @return the installed model's name, or null if there is none and nothing is bundled.
     */
    @Synchronized
    @Throws(IOException::class)
    fun ensureInstalled(context: Context, store: SttModelStore): String? {
        store.installedName?.let { return it }
        val model = available(context).firstOrNull() ?: return null
        return install(context, store, model)
    }
}
