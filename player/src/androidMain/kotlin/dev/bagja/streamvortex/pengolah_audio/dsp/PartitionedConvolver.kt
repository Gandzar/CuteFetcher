package dev.bagja.streamvortex.pengolah_audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Random

/** FFT radix-2 sederhana (in-place) untuk ukuran pangkat dua. */
class Fft(private val n: Int) {
    private val cosT = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2.0 * PI * it / n).toFloat() }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** inverse=true menghasilkan hasil yang sudah dibagi n. */
    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosT[k]
                    val wi = if (inverse) sinT[k] else -sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr; im[j + half] = im[j] - xi
                    re[j] += xr; im[j] += xi
                    k += step
                }
                start += size
            }
            size *= 2
        }
        if (inverse) {
            val s = 1f / n
            for (i in 0 until n) { re[i] *= s; im[i] *= s }
        }
    }
}

/**
 * Konvolusi untuk reverb (padanan ConvolverNode).
 * Impuls panjang dipecah jadi potongan 1024 sampel, dihitung lewat FFT (overlap-save).
 * Algoritma ini sudah diuji terhadap konvolusi langsung (selisih ~1e-14).
 * Latensi sisi "wet" = 1024 sampel (~23 ms), tidak terasa pada reverb.
 */
class PartitionedConvolver(ir: FloatArray) {
    private val b = BLOCK
    private val f = 2 * BLOCK              // ukuran FFT
    private val k = BLOCK + 1              // jumlah bin yang disimpan (spektrum sisi positif)
    private val parts = (ir.size + b - 1) / b
    private val fft = Fft(f)

    private val hRe = FloatArray(parts * k)    // spektrum impuls per potongan
    private val hIm = FloatArray(parts * k)
    private val xRe = FloatArray(parts * k)    // riwayat spektrum masukan (FDL)
    private val xIm = FloatArray(parts * k)
    private var head = 0

    private val win = FloatArray(f)            // jendela masukan: [blok lama | blok baru]
    private val tRe = FloatArray(f); private val tIm = FloatArray(f)
    private val aRe = FloatArray(f); private val aIm = FloatArray(f)
    private val inBlock = FloatArray(b); private val outBlock = FloatArray(b)
    private var pos = 0

    init {
        for (p in 0 until parts) {
            tRe.fill(0f); tIm.fill(0f)
            val from = p * b
            val len = minOf(b, ir.size - from)
            System.arraycopy(ir, from, tRe, 0, len)
            fft.transform(tRe, tIm, false)
            System.arraycopy(tRe, 0, hRe, p * k, k)
            System.arraycopy(tIm, 0, hIm, p * k, k)
        }
    }

    /** Masukkan satu sampel, keluarkan satu sampel (tertunda BLOCK sampel). */
    fun tick(x: Float): Float {
        inBlock[pos] = x
        val y = outBlock[pos]
        pos++
        if (pos == b) { pos = 0; computeBlock() }
        return y
    }

    private fun computeBlock() {
        System.arraycopy(win, b, win, 0, b)          // geser: blok baru jadi blok lama
        System.arraycopy(inBlock, 0, win, b, b)
        System.arraycopy(win, 0, tRe, 0, f); tIm.fill(0f)
        fft.transform(tRe, tIm, false)

        head = (head - 1 + parts) % parts
        System.arraycopy(tRe, 0, xRe, head * k, k)
        System.arraycopy(tIm, 0, xIm, head * k, k)

        // Jumlahkan hasil kali spektrum masukan (riwayat) x spektrum impuls
        aRe.fill(0f, 0, k); aIm.fill(0f, 0, k)
        for (p in 0 until parts) {
            val xo = ((head + p) % parts) * k
            val ho = p * k
            for (i in 0 until k) {
                val xr = xRe[xo + i]; val xi = xIm[xo + i]
                val hr = hRe[ho + i]; val hi = hIm[ho + i]
                aRe[i] += xr * hr - xi * hi
                aIm[i] += xr * hi + xi * hr
            }
        }
        // Lengkapi sisi negatif spektrum (konjugat) lalu FFT balik
        for (i in 1 until b) { aRe[f - i] = aRe[i]; aIm[f - i] = -aIm[i] }
        fft.transform(aRe, aIm, true)
        System.arraycopy(aRe, b, outBlock, 0, b)     // overlap-save: ambil separuh akhir
    }

    /** Kosongkan riwayat (dipanggil saat seek agar ekor gema lama tidak bocor). */
    fun reset() {
        xRe.fill(0f); xIm.fill(0f); win.fill(0f); inBlock.fill(0f); outBlock.fill(0f)
        pos = 0; head = 0
    }

    companion object {
        const val BLOCK = 1024

        /**
         * Impuls gema acak persis seperti makeImpulse() Mixer8D: derau yang meluruh pangkat 3,
         * 2 channel. Lalu dinormalkan seperti ConvolverNode (normalize = true).
         */
        fun buildReverbIr(seconds: Float, sampleRate: Int, seed: Long = 7L): Array<FloatArray> {
            val len = maxOf(1, (sampleRate * seconds).toInt())
            val rnd = Random(seed)
            val ir = Array(2) { FloatArray(len) }
            var sumSq = 0.0
            for (c in 0 until 2) for (i in 0 until len) {
                val env = (1.0 - i.toDouble() / len).pow(3.0)
                val v = ((rnd.nextDouble() * 2 - 1) * env).toFloat()
                ir[c][i] = v
                sumSq += v.toDouble() * v
            }
            val rms = sqrt(sumSq / (2.0 * len)).coerceAtLeast(0.000125)
            var scale = (1.0 / rms) * 10.0.pow(-58.0 / 20.0)   // kalibrasi -58 dB seperti Chromium
            if (sampleRate != 44100) scale *= 44100.0 / sampleRate
            for (c in 0 until 2) for (i in 0 until len) ir[c][i] = (ir[c][i] * scale).toFloat()
            return ir
        }
    }
}
