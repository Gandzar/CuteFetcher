package dev.bagja.streamvortex.pengolah_audio.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Posisi bola suara saat ini (untuk digambar di kanvas jalur). */
data class PanFrame(val x: Float = 0f, val z: Float = 0f, val progress: Float = 0f)

class SavedPreset(val id: String, val name: String, val settings: MixerSettings)

/**
 * Pusat data Mixer yang dipakai bersama UI dan PlaybackService (satu proses).
 *  - UI menulis `settings`; Service mengamati dan menerapkannya ke audio.
 *  - Mesin audio menulis `frame` (30x per detik) agar UI dapat menggambar bola.
 *  - Pengaturan terakhir & preset tersimpan di SharedPreferences.
 */
object MixerRepository {
    val settings = MutableStateFlow(MixerSettings())
    val frame = MutableStateFlow(PanFrame())

    /** Bila tidak null, bola diarahkan manual oleh jari (x,z). Dibaca thread audio. */
    @Volatile var override: FloatArray? = null

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var saveJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val raw = prefs().getString(KEY_LAST, null)
        if (raw != null) runCatching { settings.value = MixerSettings.fromJson(JSONObject(raw)) }
    }

    fun update(change: (MixerSettings) -> MixerSettings) {
        settings.update(change)
        scheduleSave()
    }

    fun set(s: MixerSettings) { settings.value = s; scheduleSave() }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600) // tunggu slider berhenti digeser agar tidak menulis terus-menerus
            prefs().edit().putString(KEY_LAST, settings.value.toJson().toString()).apply()
        }
    }

    // ---- Preset buatan sendiri ----
    fun loadPresets(): List<SavedPreset> = runCatching {
        val arr = JSONArray(prefs().getString(KEY_PRESETS, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            SavedPreset(o.getString("id"), o.getString("name"), MixerSettings.fromJson(o.getJSONObject("settings")))
        }
    }.getOrDefault(emptyList())

    fun savePreset(name: String): SavedPreset {
        val created = SavedPreset(UUID.randomUUID().toString(), name, settings.value)
        writePresets(listOf(created) + loadPresets())
        return created
    }

    fun deletePreset(id: String) = writePresets(loadPresets().filter { it.id != id })

    private fun writePresets(list: List<SavedPreset>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("settings", it.settings.toJson())) }
        prefs().edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    private fun prefs() = appContext!!.getSharedPreferences("mixer", Context.MODE_PRIVATE)
    private const val KEY_LAST = "last"
    private const val KEY_PRESETS = "presets"
}
