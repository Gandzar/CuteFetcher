package dev.bagja.streamvortex.pengolah_audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filter biquad (rumus RBJ "Audio EQ Cookbook") yang sama dipakai Web Audio API,
 * jadi hasilnya setara dengan BiquadFilterNode di Mixer8D.
 * Satu objek = satu filter untuk SATU channel (kiri/kanan butuh objek sendiri).
 */
class Biquad {
    // Koefisien yang sudah dibagi a0
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    // Memori filter (bentuk "transposed direct form II")
    private var z1 = 0.0; private var z2 = 0.0

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0
    }

    private fun clampFreq(fs: Double, f: Double) = f.coerceIn(10.0, fs * 0.45)

    /** Peaking EQ: menaikkan/menurunkan satu pita frekuensi (dipakai 10 band EQ). */
    fun peaking(fs: Double, freq: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * clampFreq(fs, freq) / fs
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    /** Low-shelf: menaikkan semua frekuensi di bawah `freq` (bass boost). */
    fun lowShelf(fs: Double, freq: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * clampFreq(fs, freq) / fs
        val alpha = sin(w0) / 2 * sqrt(2.0)       // kemiringan shelf S = 1
        val k = 2 * sqrt(a) * alpha
        val c = cos(w0)
        set(
            a * ((a + 1) - (a - 1) * c + k),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - k),
            (a + 1) + (a - 1) * c + k,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - k,
        )
    }

    /** Low-pass. NB: di Web Audio, Q untuk lowpass/highpass bersatuan dB. */
    fun lowpass(fs: Double, freq: Double, qDb: Double) {
        val w0 = 2 * PI * clampFreq(fs, freq) / fs
        val alpha = sin(w0) / (2 * 10.0.pow(qDb / 20.0))
        val c = cos(w0)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun highpass(fs: Double, freq: Double, qDb: Double) {
        val w0 = 2 * PI * clampFreq(fs, freq) / fs
        val alpha = sin(w0) / (2 * 10.0.pow(qDb / 20.0))
        val c = cos(w0)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Band-pass (dipakai efek wah-wah). Q biasa (bukan dB). */
    fun bandpass(fs: Double, freq: Double, q: Double) {
        val w0 = 2 * PI * clampFreq(fs, freq) / fs
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
    }
}
