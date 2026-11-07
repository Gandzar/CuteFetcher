package dev.bagja.streamvortex.pengolah_audio.data

/** Frekuensi & label 10 band EQ (sama dengan Mixer8D). */
val EQ_FREQUENCIES = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

class EqPreset(val label: String, val gains: List<Float>)

val EQ_PRESETS = listOf(
    EqPreset("Datar", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Bass", listOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Elektro", listOf(6f, 5f, 1f, 0f, -2f, -1f, 1f, 3f, 5f, 5f)),
    EqPreset("Rock", listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 3f, 4f, 4f)),
    EqPreset("Pop", listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f)),
    EqPreset("Vokal", listOf(-3f, -2f, -1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f)),
    EqPreset("Jazz", listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f)),
    EqPreset("Cerah", listOf(-4f, -3f, -2f, -1f, 0f, 1f, 3f, 4f, 5f, 5f)),
    EqPreset("Klasik", listOf(4f, 3f, 2f, 1f, 0f, 0f, -1f, -2f, -2f, -3f)),
)

class BuiltinPreset(val id: String, val name: String, val description: String, val build: () -> MixerSettings)

/** Preset bawaan (port dari BUILTIN_PRESETS Mixer8D). */
val BUILTIN_PRESETS = listOf(
    BuiltinPreset("klasik", "Klasik 8D", "Lingkaran pelan dengan ruang gema lembut") {
        MixerSettings(pathId = "lingkaran", rotationSeconds = 10f, reverbMix = 0.25f, reverbDecay = 2.5f)
    },
    BuiltinPreset("bolak-balik", "Bolak-balik Kiri Kanan", "Arah berubah setiap 2 detik di jalur kiri ke kanan") {
        MixerSettings(pathId = "kiri-kanan", movement = Movement.PINGPONG, intervalMs = 2000f, rotationSeconds = 6f, reverbMix = 0.15f)
    },
    BuiltinPreset("angka-8", "Angka 8 Melayang", "Jalur angka 8 dengan gema panjang dan bass hangat") {
        MixerSettings(pathId = "angka-8", rotationSeconds = 14f, reverbMix = 0.4f, reverbDecay = 4.5f, bassDb = 5f,
            eq = listOf(4f, 3f, 2f, 0f, 0f, 0f, 0f, 1f, 2f, 2f))
    },
    BuiltinPreset("lorong", "Lorong Gema", "Depan ke belakang dengan echo dan ruang luas") {
        MixerSettings(pathId = "depan-belakang", movement = Movement.PINGPONG, intervalMs = 4000f, rotationSeconds = 8f,
            reverbMix = 0.5f, reverbDecay = 6f, echoMs = 420f, echoFeedback = 0.45f, echoMix = 0.3f)
    },
    BuiltinPreset("acak", "Acak Melayang", "Loncat acak di sepanjang bentuk bintang") {
        MixerSettings(pathId = "bintang", movement = Movement.RANDOM, intervalMs = 1500f, rotationSeconds = 5f, reverbMix = 0.3f, reverbDecay = 3f)
    },
    BuiltinPreset("malam", "Malam Hangat", "Elips pelan, bass tebal, nada lembut") {
        MixerSettings(pathId = "elips", rotationSeconds = 18f, bassDb = 7f, lowpassHz = 9000f,
            eq = listOf(5f, 4f, 2f, 1f, 0f, -1f, -1f, -2f, -3f, -4f), reverbMix = 0.35f, reverbDecay = 3.5f, compressor = 0.3f)
    },
)
