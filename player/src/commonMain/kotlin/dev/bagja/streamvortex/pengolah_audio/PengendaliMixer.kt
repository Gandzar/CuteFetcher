package dev.bagja.streamvortex.pengolah_audio

import kotlinx.coroutines.flow.StateFlow

/** Jalur gerak 8D yang bisa dipilih (id stabil, label bahasa Indonesia dari mesin). */
data class InfoJalur(val id: String, val label: String)

/** Preset bawaan mixer. */
data class InfoPreset(val id: String, val nama: String, val deskripsi: String)

/** Posisi bola suara saat ini: x kiri-kanan, z depan-belakang (-1..1), progres 0..1 sepanjang jalur. */
data class BolaSuara(val x: Float = 0f, val z: Float = 0f, val progres: Float = 0f)

/**
 * Kumpulan kontrol mixer yang dipakai UI lintas-platform. Sengaja lebih kecil dari MixerSettings
 * Android (yang memakai org.json); implementasi Android memetakan keduanya.
 * Default [enabled] = false: mixer mati sampai pengguna menyalakannya.
 */
data class PengaturanMixer(
    val enabled: Boolean = false,
    val jalurId: String = "lingkaran",
    val eq: List<Float> = List(JUMLAH_BAND_EQ) { 0f },
    val bassDb: Float = 0f,
    val highpassHz: Float = 20f,
    val lowpassHz: Float = 20000f,
    val drive: Float = 0f,
    val compressor: Float = 0f,
    val reverbMix: Float = 0.25f,
    val reverbDecay: Float = 2.5f,
    val echoMix: Float = 0f,
    val echoMs: Float = 350f,
    val echoFeedback: Float = 0.3f,
    val width: Float = 1f,
    val room: Float = 1f,
    val rotationSeconds: Float = 10f,
    val clockwise: Boolean = true,
    val chorusOn: Boolean = false,
    val tremoloOn: Boolean = false,
    val wahOn: Boolean = false,
    val phoneOn: Boolean = false,
    /** "loop" (putar), "pingpong" (bolak-balik) atau "acak". */
    val gerak: String = "loop",
    val intervalMs: Float = 3000f,
    val lissajousFreqX: Int = 3,
    val lissajousFreqZ: Int = 2,
    val lissajousFase: Int = 90,
    val offsetX: Float = 0f,
    val offsetZ: Float = 0f,
    val transisiDetik: Float = 0.8f,
    val volume: Float = 0.9f,
    /** Jalur gambar sendiri: titik berselang-seling [x0, z0, x1, z1, ...] dalam rentang -1..1 (jalurId = "custom"). */
    val jalurKustom: List<Float> = emptyList(),
    /** Tingkat penghalusan jalur gambar sendiri, 0..8. */
    val halusKustom: Int = 4,
) {
    companion object {
        const val JALUR_LISSAJOUS = "lissajous"
        const val JALUR_KUSTOM = "custom"
        const val GERAK_PUTAR = "loop"
        const val GERAK_BOLAK_BALIK = "pingpong"
        const val GERAK_ACAK = "random"

        const val JUMLAH_BAND_EQ = 10
        val LabelBandEq = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    }
}

/** Antarmuka mixer untuk UI. Implementasi nyata hanya ada di Android (jalur pemutar lokal). */
interface PengendaliMixer {
    val pengaturan: StateFlow<PengaturanMixer>
    val bola: StateFlow<BolaSuara>
    val daftarJalur: List<InfoJalur>
    val daftarPreset: List<InfoPreset>

    fun ubah(transformasi: (PengaturanMixer) -> PengaturanMixer)

    /**
     * Titik jalur gerak yang sedang dipilih untuk digambar sebagai pratinjau: [x0, z0, x1, z1, ...]
     * (x kiri-kanan, z depan-belakang, rentang -1..1). Sama persis dengan yang dipakai mesin audio.
     */
    fun titikJalur(pengaturan: PengaturanMixer): FloatArray

    /** Terapkan preset bawaan dan nyalakan mixer. */
    fun terapkanPreset(id: String)
}
