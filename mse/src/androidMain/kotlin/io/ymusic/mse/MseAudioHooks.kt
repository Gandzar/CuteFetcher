package io.ymusic.mse

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi

/**
 * Titik sambung dari luar modul untuk jalur streaming MSE (mis. YouTube).
 * Cute Fetcher memasang pabrik prosesor mixer DSP/8D di sini. Setiap sink butuh instance sendiri
 * (prosesor menyimpan state), jadi yang disimpan adalah pabrik, bukan prosesornya.
 */
@OptIn(UnstableApi::class)
object MseAudioHooks {
    @Volatile
    var pabrikProsesor: (() -> AudioProcessor)? = null
}
