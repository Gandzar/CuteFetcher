package dev.bagja.streamvortex.pengolah_audio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pemosisi suara 3D ke headphone (pengganti PannerNode "HRTF" di Web Audio).
 * CATATAN JUJUR: browser memakai basis data HRTF nyata; Android tidak punya yang setara.
 * Di sini dipakai model binaural fisik sederhana yang menghasilkan petunjuk yang sama:
 *  - ITD : telinga jauh mendengar sedikit lebih lambat (rumus Woodworth, kepala 8,75 cm)
 *  - ILD : telinga jauh lebih pelan (hingga -6 dB) dan lebih redup (low-pass "bayangan kepala")
 *  - Depan/belakang: sumber di belakang diredam nada tingginya (efek daun telinga)
 *  - Jarak: model "inverse" refDistance = 1, rolloffFactor = pengaturan "Ruang"
 * Sumbu: +x = kanan, -z = depan (sama seperti Web Audio).
 */
class BinauralPanner(private val sampleRate: Int) {
    private val fs = sampleRate.toDouble()
    private val size = 256
    private val mask = size - 1
    private val lineL = FloatArray(size)
    private val lineR = FloatArray(size)
    private var w = 0

    private var delL = 0.0; private var delR = 0.0; private var tgtDelL = 0.0; private var tgtDelR = 0.0
    private var gL = 1.0; private var gR = 1.0; private var tgtGL = 1.0; private var tgtGR = 1.0
    private var shadowAL = 1.0; private var shadowAR = 1.0; private var pinnaA = 1.0
    private var s1L = 0f; private var s1R = 0f; private var s2L = 0f; private var s2R = 0f

    /** Hasil sampel terakhir (dibaca langsung agar tanpa alokasi objek). */
    var outL = 0f
        private set
    var outR = 0f
        private set

    /** Perbarui target posisi sumber (dipanggil sekali per blok 128 sampel). */
    fun setPosition(x: Float, z: Float, room: Float) {
        val dist = hypot(x.toDouble(), z.toDouble())
        val distGain = 1.0 / (1.0 + room * (max(dist, 1.0) - 1.0))
        var lat = 0.0; var front = 1.0
        if (dist > 1e-3) {
            lat = x / dist
            front = -z / dist
            if (dist < 0.25) lat *= dist / 0.25     // sangat dekat pusat kepala: condong ke tengah
        }
        val absLat = abs(lat)
        val phi = asin(min(1.0, absLat))
        val itdSamples = 0.0875 / 343.0 * (phi + sin(phi)) * fs

        val farGain = 10.0.pow(-6.0 * absLat / 20.0)
        val leftIsFar = lat > 0
        tgtDelL = if (leftIsFar) itdSamples else 0.0
        tgtDelR = if (leftIsFar) 0.0 else itdSamples
        tgtGL = distGain * (if (leftIsFar) farGain else 1.0)
        tgtGR = distGain * (if (leftIsFar) 1.0 else farGain)

        val fcShadow = 20000.0 * (1 - absLat) + 2500.0 * absLat
        val aFar = onePole(fcShadow)
        shadowAL = if (leftIsFar) aFar else 1.0
        shadowAR = if (leftIsFar) 1.0 else aFar

        val back = max(0.0, -front)
        pinnaA = onePole(20000.0 + (5500.0 - 20000.0) * back)
    }

    private fun onePole(fc: Double) = 1.0 - exp(-2 * PI * min(fc, fs * 0.45) / fs)

    private fun readLine(line: FloatArray, d: Double): Float {
        val di = d.toInt()
        val frac = (d - di).toFloat()
        val a = line[(w - di) and mask]
        val b = line[(w - di - 1) and mask]
        return a * (1 - frac) + b * frac
    }

    /** Proses satu sampel mono -> stereo binaural (hasil di outL/outR). */
    fun process(m: Float) {
        lineL[w] = m; lineR[w] = m
        delL += (tgtDelL - delL) * 0.002; delR += (tgtDelR - delR) * 0.002
        gL += (tgtGL - gL) * 0.002; gR += (tgtGR - gR) * 0.002

        val vl = readLine(lineL, delL)
        val vr = readLine(lineR, delR)
        s1L += (shadowAL * (vl - s1L)).toFloat(); s1R += (shadowAR * (vr - s1R)).toFloat()
        s2L += (pinnaA * (s1L - s2L)).toFloat(); s2R += (pinnaA * (s1R - s2R)).toFloat()
        outL = (s2L * gL).toFloat(); outR = (s2R * gR).toFloat()
        w = (w + 1) and mask
    }

    fun reset() {
        lineL.fill(0f); lineR.fill(0f); s1L = 0f; s1R = 0f; s2L = 0f; s2R = 0f
    }
}
