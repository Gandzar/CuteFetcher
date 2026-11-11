package dev.bagja.streamvortex.pengolah_audio

import android.content.Context
import dev.bagja.streamvortex.pengolah_audio.data.BUILTIN_PRESETS
import dev.bagja.streamvortex.pengolah_audio.data.MixerRepository
import dev.bagja.streamvortex.pengolah_audio.data.MixerSettings
import dev.bagja.streamvortex.pengolah_audio.data.Movement
import dev.bagja.streamvortex.pengolah_audio.data.Pt
import dev.bagja.streamvortex.pengolah_audio.dsp.PathLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Pemetaan [PengendaliMixer] <-> [MixerRepository] (satu proses dengan [MixerProcessor]). */
class PengendaliMixerAndroid(context: Context) : PengendaliMixer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _pengaturan: MutableStateFlow<PengaturanMixer>
    private val _bola = MutableStateFlow(BolaSuara())

    init {
        MixerRepository.init(context.applicationContext)
        _pengaturan = MutableStateFlow(MixerRepository.settings.value.keUi())
        scope.launch { MixerRepository.settings.collect { _pengaturan.value = it.keUi() } }
        scope.launch { MixerRepository.frame.collect { _bola.value = BolaSuara(it.x, it.z, it.progress) } }
    }

    override val pengaturan: StateFlow<PengaturanMixer> = _pengaturan.asStateFlow()
    override val bola: StateFlow<BolaSuara> = _bola.asStateFlow()
    override val daftarJalur: List<InfoJalur> = PathLib.builtin.map { InfoJalur(it.id, it.label) } +
        InfoJalur(PengaturanMixer.JALUR_LISSAJOUS, "Lissajous") +
        InfoJalur(PengaturanMixer.JALUR_KUSTOM, "Gambar sendiri")
    override val daftarPreset: List<InfoPreset> = BUILTIN_PRESETS.map { InfoPreset(it.id, it.name, it.description) }

    override fun ubah(transformasi: (PengaturanMixer) -> PengaturanMixer) {
        MixerRepository.update { dasar -> transformasi(dasar.keUi()).terapkanKe(dasar) }
    }

    override fun titikJalur(pengaturan: PengaturanMixer): FloatArray = PathLib.resolve(
        pengaturan.jalurId,
        pengaturan.jalurKustom.chunked(2).filter { it.size == 2 }.map { Pt(it[0], it[1]) },
        pengaturan.halusKustom,
        pengaturan.lissajousFreqX,
        pengaturan.lissajousFreqZ,
        pengaturan.lissajousFase,
    )

    override fun terapkanPreset(id: String) {
        val preset = BUILTIN_PRESETS.firstOrNull { it.id == id } ?: return
        MixerRepository.set(preset.build().copy(enabled = true))
    }
}

private fun MixerSettings.keUi() = PengaturanMixer(
    enabled = enabled,
    jalurId = pathId,
    eq = if (eq.size == PengaturanMixer.JUMLAH_BAND_EQ) eq else List(PengaturanMixer.JUMLAH_BAND_EQ) { 0f },
    bassDb = bassDb,
    highpassHz = highpassHz,
    lowpassHz = lowpassHz,
    drive = drive,
    compressor = compressor,
    reverbMix = reverbMix,
    reverbDecay = reverbDecay,
    echoMix = echoMix,
    echoMs = echoMs,
    echoFeedback = echoFeedback,
    width = width,
    room = room,
    rotationSeconds = rotationSeconds,
    clockwise = clockwise,
    chorusOn = chorusOn,
    tremoloOn = tremoloOn,
    wahOn = wahOn,
    phoneOn = phoneOn,
    gerak = movement.id,
    intervalMs = intervalMs,
    lissajousFreqX = lissajousFreqX,
    lissajousFreqZ = lissajousFreqZ,
    lissajousFase = lissajousPhase,
    offsetX = offsetX,
    offsetZ = offsetZ,
    transisiDetik = transitionSeconds,
    volume = volume,
    jalurKustom = customRaw.flatMap { listOf(it.x, it.z) },
    halusKustom = smoothLevel,
)

/** Hanya field yang dikenal UI yang ditimpa; field lain (jalur kustom, tempo, dst.) tetap utuh. */
private fun PengaturanMixer.terapkanKe(dasar: MixerSettings) = dasar.copy(
    enabled = enabled,
    pathId = jalurId,
    eq = eq,
    bassDb = bassDb,
    highpassHz = highpassHz,
    lowpassHz = lowpassHz,
    drive = drive,
    compressor = compressor,
    reverbMix = reverbMix,
    reverbDecay = reverbDecay,
    echoMix = echoMix,
    echoMs = echoMs,
    echoFeedback = echoFeedback,
    width = width,
    room = room,
    rotationSeconds = rotationSeconds,
    clockwise = clockwise,
    chorusOn = chorusOn,
    tremoloOn = tremoloOn,
    wahOn = wahOn,
    phoneOn = phoneOn,    movement = Movement.of(gerak),
    intervalMs = intervalMs,
    lissajousFreqX = lissajousFreqX,
    lissajousFreqZ = lissajousFreqZ,
    lissajousPhase = lissajousFase,
    offsetX = offsetX,
    offsetZ = offsetZ,
    transitionSeconds = transisiDetik,
    volume = volume,
    customRaw = jalurKustom.chunked(2).filter { it.size == 2 }.map { Pt(it[0], it[1]) },
    smoothLevel = halusKustom,
)
