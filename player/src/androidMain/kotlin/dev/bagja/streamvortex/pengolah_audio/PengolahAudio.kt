package dev.bagja.streamvortex.pengolah_audio

import android.content.Context
import dev.bagja.streamvortex.pengolah_audio.data.MixerRepository
import dev.bagja.streamvortex.pengambil_media.AsalLog
import dev.bagja.streamvortex.pengambil_media.JurnalDebug
import io.ymusic.mse.MseAudioHooks

/**
 * Pasang mixer DSP/8D ke SEMUA jalur audio. Harus dipanggil sekali saat aplikasi mulai
 * (Application.onCreate), BUKAN dari pemutar lokal.
 *
 * Alasan (bug v0.1.0): pemutar lokal dibuat malas (lazy) saat pertama ada pemutaran file lokal. Hook
 * streaming (MSE/YouTube) dulu dipasang dari sana, jadi bila proses aplikasi baru hidup dan langsung
 * memutar streaming, hook belum ada dan efek tidak terdengar sampai satu file lokal diputar.
 */
object PengolahAudio {
    @Volatile
    private var terpasang = false

    fun pasang(context: Context) {
        MixerRepository.init(context.applicationContext)
        MseAudioHooks.pabrikProsesor = { MixerProcessor("streaming") }
        JurnalDebug.i(AsalLog.MIXER, "mixer dipasang di jalur streaming (MSE) dan lokal")
        terpasang = true
    }

    /** Untuk panel debug. */
    fun sudahTerpasang(): Boolean = terpasang && MseAudioHooks.pabrikProsesor != null
}
