package dev.bagja.streamvortex.pengolah_audio.data

import org.json.JSONArray
import org.json.JSONObject

/** Satu titik pada bidang atas (x kiri-kanan, z depan-belakang), rentang -1..1. */
data class Pt(val x: Float, val z: Float)

enum class Movement(val id: String, val label: String) {
    LOOP("loop", "Putar"), PINGPONG("pingpong", "Bolak-balik"), RANDOM("random", "Acak");
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: LOOP }
}

/**
 * Semua pengaturan Mixer, setara MixerSettings di types.ts / DEFAULT_SETTINGS di presets.ts.
 * `enabled` adalah tambahan: saklar untuk mem-bypass seluruh efek.
 */
data class MixerSettings(
    val enabled: Boolean = false,
    // --- Jalur 8D ---
    val pathId: String = "lingkaran",
    val customRaw: List<Pt> = emptyList(),
    val smoothLevel: Int = 4,
    val lissajousFreqX: Int = 3,
    val lissajousFreqZ: Int = 2,
    val lissajousPhase: Int = 90,
    val movement: Movement = Movement.LOOP,
    val clockwise: Boolean = true,
    val intervalMs: Float = 3000f,
    val rotationSeconds: Float = 10f,
    // --- Ruang & posisi ---
    val width: Float = 1f,
    val room: Float = 1f,
    val offsetX: Float = 0f,
    val offsetZ: Float = 0f,
    val transitionSeconds: Float = 0.8f,
    // --- Equalizer & filter ---
    val eq: List<Float> = List(10) { 0f },
    val bassDb: Float = 0f,
    val highpassHz: Float = 20f,
    val lowpassHz: Float = 20000f,
    // --- Karakter ---
    val drive: Float = 0f,
    val compressor: Float = 0f,
    // --- Gema & echo ---
    val reverbMix: Float = 0.25f,
    val reverbDecay: Float = 2.5f,
    val echoMs: Float = 350f,
    val echoFeedback: Float = 0.3f,
    val echoMix: Float = 0f,
    // --- Tempo & nada ---
    val tempo: Float = 1f,
    val keepPitch: Boolean = true,
    val pitchSemitones: Float = 0f,
    // --- Efek fun ---
    val chorusOn: Boolean = false, val chorusRate: Float = 1.5f, val chorusDepth: Float = 0.5f,
    val tremoloOn: Boolean = false, val tremoloRate: Float = 4f, val tremoloDepth: Float = 0.6f,
    val wahOn: Boolean = false, val wahRate: Float = 1.2f, val wahDepth: Float = 0.7f,
    val phoneOn: Boolean = false,
    val volume: Float = 0.9f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled); put("pathId", pathId)
        put("customRaw", JSONArray().also { a -> customRaw.forEach { a.put(JSONArray().put(it.x.toDouble()).put(it.z.toDouble())) } })
        put("smoothLevel", smoothLevel); put("lissajousFreqX", lissajousFreqX); put("lissajousFreqZ", lissajousFreqZ)
        put("lissajousPhase", lissajousPhase); put("movement", movement.id); put("clockwise", clockwise)
        put("intervalMs", intervalMs.toDouble()); put("rotationSeconds", rotationSeconds.toDouble())
        put("width", width.toDouble()); put("room", room.toDouble()); put("offsetX", offsetX.toDouble()); put("offsetZ", offsetZ.toDouble())
        put("transitionSeconds", transitionSeconds.toDouble())
        put("eq", JSONArray().also { a -> eq.forEach { a.put(it.toDouble()) } })
        put("bassDb", bassDb.toDouble()); put("highpassHz", highpassHz.toDouble()); put("lowpassHz", lowpassHz.toDouble())
        put("drive", drive.toDouble()); put("compressor", compressor.toDouble())
        put("reverbMix", reverbMix.toDouble()); put("reverbDecay", reverbDecay.toDouble())
        put("echoMs", echoMs.toDouble()); put("echoFeedback", echoFeedback.toDouble()); put("echoMix", echoMix.toDouble())
        put("tempo", tempo.toDouble()); put("keepPitch", keepPitch); put("pitchSemitones", pitchSemitones.toDouble())
        put("chorusOn", chorusOn); put("chorusRate", chorusRate.toDouble()); put("chorusDepth", chorusDepth.toDouble())
        put("tremoloOn", tremoloOn); put("tremoloRate", tremoloRate.toDouble()); put("tremoloDepth", tremoloDepth.toDouble())
        put("wahOn", wahOn); put("wahRate", wahRate.toDouble()); put("wahDepth", wahDepth.toDouble())
        put("phoneOn", phoneOn); put("volume", volume.toDouble())
    }

    companion object {
        /** Setara mergeSettings(): field yang hilang memakai nilai bawaan. */
        fun fromJson(j: JSONObject): MixerSettings {
            val d = MixerSettings()
            fun f(k: String, def: Float) = if (j.has(k)) j.optDouble(k, def.toDouble()).toFloat() else def
            fun i(k: String, def: Int) = if (j.has(k)) j.optInt(k, def) else def
            fun b(k: String, def: Boolean) = if (j.has(k)) j.optBoolean(k, def) else def
            val eqArr = j.optJSONArray("eq")
            val eq = if (eqArr != null && eqArr.length() == 10) List(10) { eqArr.optDouble(it, 0.0).toFloat() } else d.eq
            val rawArr = j.optJSONArray("customRaw")
            val raw = if (rawArr != null) List(rawArr.length()) { n ->
                val p = rawArr.getJSONArray(n); Pt(p.optDouble(0).toFloat(), p.optDouble(1).toFloat())
            } else emptyList()
            return MixerSettings(
                enabled = b("enabled", d.enabled), pathId = j.optString("pathId", d.pathId), customRaw = raw,
                smoothLevel = i("smoothLevel", d.smoothLevel), lissajousFreqX = i("lissajousFreqX", d.lissajousFreqX),
                lissajousFreqZ = i("lissajousFreqZ", d.lissajousFreqZ), lissajousPhase = i("lissajousPhase", d.lissajousPhase),
                movement = Movement.of(j.optString("movement", "loop")), clockwise = b("clockwise", d.clockwise),
                intervalMs = f("intervalMs", d.intervalMs), rotationSeconds = f("rotationSeconds", d.rotationSeconds),
                width = f("width", d.width), room = f("room", d.room), offsetX = f("offsetX", d.offsetX), offsetZ = f("offsetZ", d.offsetZ),
                transitionSeconds = f("transitionSeconds", d.transitionSeconds), eq = eq,
                bassDb = f("bassDb", d.bassDb), highpassHz = f("highpassHz", d.highpassHz), lowpassHz = f("lowpassHz", d.lowpassHz),
                drive = f("drive", d.drive), compressor = f("compressor", d.compressor),
                reverbMix = f("reverbMix", d.reverbMix), reverbDecay = f("reverbDecay", d.reverbDecay),
                echoMs = f("echoMs", d.echoMs), echoFeedback = f("echoFeedback", d.echoFeedback), echoMix = f("echoMix", d.echoMix),
                tempo = f("tempo", d.tempo), keepPitch = b("keepPitch", d.keepPitch), pitchSemitones = f("pitchSemitones", d.pitchSemitones),
                chorusOn = b("chorusOn", d.chorusOn), chorusRate = f("chorusRate", d.chorusRate), chorusDepth = f("chorusDepth", d.chorusDepth),
                tremoloOn = b("tremoloOn", d.tremoloOn), tremoloRate = f("tremoloRate", d.tremoloRate), tremoloDepth = f("tremoloDepth", d.tremoloDepth),
                wahOn = b("wahOn", d.wahOn), wahRate = f("wahRate", d.wahRate), wahDepth = f("wahDepth", d.wahDepth),
                phoneOn = b("phoneOn", d.phoneOn), volume = f("volume", d.volume),
            )
        }
    }
}
