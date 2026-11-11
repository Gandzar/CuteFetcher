@file:androidx.annotation.OptIn(UnstableApi::class)

package dev.bagja.streamvortex.pengolah_audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.bagja.streamvortex.pengambil_media.AsalLog
import dev.bagja.streamvortex.pengambil_media.JurnalDebug
import dev.bagja.streamvortex.pengolah_audio.data.MixerRepository
import dev.bagja.streamvortex.pengolah_audio.data.MixerSettings
import dev.bagja.streamvortex.pengolah_audio.data.PanFrame
import dev.bagja.streamvortex.pengolah_audio.dsp.MixerEngine
import dev.bagja.streamvortex.pengolah_audio.dsp.PathLib
import java.nio.ByteBuffer

/**
 * Jembatan ExoPlayer -> MixerEngine (diadaptasi dari Musikku; GPL-3.0, lihat NOTICE.md).
 * PCM 16-bit diolah dalam float per blok 128 sampel dan dikembalikan sebagai PCM 16-bit stereo
 * (masukan mono dijadikan stereo karena efek 8D butuh dua telinga).
 *
 * Perbedaan dari versi Musikku:
 *  - membaca [MixerRepository.settings] langsung (tanpa pengamat terpisah);
 *  - saat `enabled = false` audio stereo diteruskan bit-per-bit (tanpa konversi float);
 *  - mesin lama ditutup (thread reverb dihentikan) saat dikonfigurasi ulang atau di-reset;
 *  - onFlush memakai tanda tangan Media3 1.11 (StreamMetadata).
 */
internal class MixerProcessor(private val asal: String = "lokal") : BaseAudioProcessor() {
    private var engine: MixerEngine? = null
    private var inChannels = 2
    private val inL = FloatArray(BLOCK)
    private val inR = FloatArray(BLOCK)
    private val outL = FloatArray(BLOCK)
    private val outR = FloatArray(BLOCK)

    // Jalur 8D hanya dihitung ulang saat parameter jalurnya berubah. Perbandingan lewat field (bukan
    // list) agar thread audio tidak mengalokasi objek di setiap buffer.
    private var jalurSiap = false
    private var kId = ""
    private var kRaw: List<*>? = null
    private var kHalus = 0
    private var kFx = 0
    private var kFz = 0
    private var kFase = 0
    private var jalur: FloatArray = PathLib.builtinById("lingkaran")!!.build()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            JurnalDebug.w(AsalLog.MIXER, "[$asal] format tidak didukung (encoding=${inputAudioFormat.encoding}, kanal=${inputAudioFormat.channelCount}); efek dilewati")
            return AudioProcessor.AudioFormat.NOT_SET // format tak didukung: lewati efek, lagu tetap main
        }
        inChannels = inputAudioFormat.channelCount
        JurnalDebug.i(AsalLog.MIXER, "[$asal] dikonfigurasi: ${inputAudioFormat.sampleRate} Hz, ${inputAudioFormat.channelCount} kanal")
        engine?.close()
        engine = MixerEngine(inputAudioFormat.sampleRate) { x, z, p -> MixerRepository.frame.value = PanFrame(x, z, p) }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    private fun perbaruiJalur(s: MixerSettings) {
        if (jalurSiap && kId == s.pathId && kRaw === s.customRaw && kHalus == s.smoothLevel &&
            kFx == s.lissajousFreqX && kFz == s.lissajousFreqZ && kFase == s.lissajousPhase
        ) {
            return
        }
        jalur = PathLib.resolve(s.pathId, s.customRaw, s.smoothLevel, s.lissajousFreqX, s.lissajousFreqZ, s.lissajousPhase)
        kId = s.pathId
        kRaw = s.customRaw
        kHalus = s.smoothLevel
        kFx = s.lissajousFreqX
        kFz = s.lissajousFreqZ
        kFase = s.lissajousPhase
        jalurSiap = true
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * inChannels)
        if (frames == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val out = replaceOutputBuffer(frames * 4)
        val s = MixerRepository.settings.value

        if (!s.enabled && inChannels == 2) {
            // Bypass bit-per-bit: tidak ada konversi, tidak ada perubahan suara.
            val batas = inputBuffer.position() + frames * 4
            val lama = inputBuffer.limit()
            inputBuffer.limit(batas)
            out.put(inputBuffer)
            inputBuffer.limit(lama)
            out.flip()
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val eng = engine
        if (s.enabled) perbaruiJalur(s)
        var done = 0
        while (done < frames) {
            val n = minOf(BLOCK, frames - done)
            for (i in 0 until n) {
                val l = inputBuffer.short / 32768f
                val r = if (inChannels == 2) inputBuffer.short / 32768f else l
                inL[i] = l
                inR[i] = r
            }
            if (eng != null && s.enabled) {
                eng.process(inL, inR, outL, outR, n, s, jalur, MixerRepository.override)
                for (i in 0 until n) {
                    out.putShort(keShort(outL[i]))
                    out.putShort(keShort(outR[i]))
                }
            } else {
                for (i in 0 until n) {
                    out.putShort(keShort(inL[i]))
                    out.putShort(keShort(inR[i]))
                }
            }
            done += n
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun keShort(v: Float): Short = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()

    /** Seek / ganti lagu: buang sisa gema lama. */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        engine?.reset()
    }

    override fun onReset() {
        JurnalDebug.d(AsalLog.MIXER, "[$asal] direset")
        engine?.close()
        engine = null
        jalurSiap = false
    }

    private companion object {
        const val BLOCK = 128
    }
}
