package dev.bagja.streamvortex.pengolah_audio.dsp

import dev.bagja.streamvortex.pengolah_audio.data.EQ_FREQUENCIES
import dev.bagja.streamvortex.pengolah_audio.data.Movement
import dev.bagja.streamvortex.pengolah_audio.data.MixerSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/**
 * MESIN EFEK UTAMA. Menjalankan rantai yang sama dengan graf Web Audio di Mixer8D:
 *
 *  EQ 10 band -> Bass (low-shelf 100 Hz) -> High-pass -> Low-pass -> Saturasi (drive)
 *  -> Kompresor -> Echo -> Chorus -> Tremolo -> Wah -> Telepon -> Pemosisi 8D
 *  -> [Dry | Reverb] -> Volume utama
 *
 * (Tempo & nada ditangani Media3/Sonic sebelum masuk ke sini.)
 * Audio diproses per blok <= 128 sampel; semua parameter bergerak halus
 * (konstanta waktu 30 ms seperti setTargetAtTime di versi web).
 */
class MixerEngine(private val sampleRate: Int, private val onFrame: (Float, Float, Float) -> Unit) {

    private val fs = sampleRate.toDouble()

    /** Parameter yang bergeser halus menuju target. */
    private class Smooth(v: Double) {
        var cur = v; var tgt = v
        fun step(a: Double): Double { cur += (tgt - cur) * a; return cur }
        val settled get() = abs(tgt - cur) < 1e-6
    }

    // ---------- EQ, bass, filter ----------
    private val eqGain = Array(10) { Smooth(0.0) }
    private val eqL = Array(10) { Biquad() }
    private val eqR = Array(10) { Biquad() }
    private val eqLast = DoubleArray(10) { Double.NaN }
    private val bassGain = Smooth(0.0); private val bassL = Biquad(); private val bassR = Biquad(); private var bassLast = Double.NaN
    private val hpFreq = Smooth(20.0); private val hpL = Biquad(); private val hpR = Biquad(); private var hpLast = Double.NaN
    private val lpFreq = Smooth(20000.0); private val lpL = Biquad(); private val lpR = Biquad(); private var lpLast = Double.NaN

    // ---------- Saturasi & kompresor ----------
    private var driveK = 0.0
    private var driveNorm = 1.0
    private var compOn = false
    private var compThr = 0.0
    private var compRatio = 1.0
    private var compMakeupDb = 0.0
    private var grDb = 0.0
    private val atkCoef = 1 - exp(-1.0 / (0.003 * fs))
    private val relCoef = 1 - exp(-1.0 / (0.25 * fs))

    // ---------- Echo ----------
    private val echoBufL = FloatArray((fs * 2).toInt() + 8)
    private val echoBufR = FloatArray(echoBufL.size)
    private var echoW = 0
    private val echoDelay = Smooth(0.35 * fs); private val echoFb = Smooth(0.3); private val echoMix = Smooth(0.0)

    // ---------- Chorus ----------
    private val chBufL = FloatArray((fs * 0.1).toInt() + 8)
    private val chBufR = FloatArray(chBufL.size)
    private var chW = 0
    private val chWet = Smooth(0.0)
    private var chDepthSamples = 0.0; private var chRate = 1.5; private var chPhase = 0.0

    // ---------- Tremolo ----------
    private val tremBase = Smooth(1.0)
    private var tremHalf = 0.0; private var tremRate = 4.0; private var tremPhase = 0.0

    // ---------- Wah ----------
    private val wahDry = Smooth(1.0); private val wahWet = Smooth(0.0)
    private val wahL = Biquad(); private val wahR = Biquad()
    private var wahHz = 0.0; private var wahRate = 1.2; private var wahPhase = 0.0

    // ---------- Telepon ----------
    private val phoneDry = Smooth(1.0); private val phoneWet = Smooth(0.0)
    private val phHpL = Biquad(); private val phHpR = Biquad(); private val phLpL = Biquad(); private val phLpR = Biquad()

    // ---------- 8D & reverb ----------
    private val panner = BinauralPanner(sampleRate)
    private val motion = MotionOrbit()
    private val dryGain = Smooth(0.75); private val wetGain = Smooth(0.25); private val master = Smooth(0.9)
    @Volatile private var convL: PartitionedConvolver? = null
    @Volatile private var convR: PartitionedConvolver? = null
    private var room = 1f

    private var lastSettings: MixerSettings? = null
    private var lastPath: FloatArray? = null
    private var sinceReport = 0

    init {
        phHpL.highpass(fs, 350.0, 1.0); phHpR.highpass(fs, 350.0, 1.0)
        phLpL.lowpass(fs, 3400.0, 1.0); phLpR.lowpass(fs, 3400.0, 1.0)
    }

    // ================== Pengaturan ==================

    private fun applySettings(s: MixerSettings) {
        for (i in 0 until 10) eqGain[i].tgt = s.eq.getOrElse(i) { 0f }.toDouble()
        bassGain.tgt = s.bassDb.toDouble()
        hpFreq.tgt = s.highpassHz.toDouble(); lpFreq.tgt = s.lowpassHz.toDouble()

        if (s.drive > 0f) { driveK = 1.0 + s.drive * 40.0; driveNorm = tanh(driveK) } else driveK = 0.0

        val c = s.compressor.toDouble()
        compOn = c > 0.001
        compThr = if (c > 0) -10.0 - 30.0 * c else 0.0
        compRatio = 1.0 + 7.0 * c
        compMakeupDb = -0.6 * gainReductionDb(0.0)   // mirip "makeup gain" otomatis kompresor browser

        echoDelay.tgt = s.echoMs / 1000.0 * fs
        echoFb.tgt = s.echoFeedback.toDouble(); echoMix.tgt = s.echoMix.toDouble()

        chWet.tgt = if (s.chorusOn) 0.5 else 0.0
        chRate = s.chorusRate.toDouble()
        chDepthSamples = if (s.chorusOn) s.chorusDepth * 0.004 * fs else 0.0

        val td = if (s.tremoloOn) s.tremoloDepth.toDouble() else 0.0
        tremBase.tgt = 1 - td / 2; tremHalf = td / 2; tremRate = s.tremoloRate.toDouble()

        wahDry.tgt = if (s.wahOn) 0.4 else 1.0
        wahWet.tgt = if (s.wahOn) 1.4 else 0.0
        wahHz = if (s.wahOn) s.wahDepth * 900.0 else 0.0
        wahRate = s.wahRate.toDouble()

        phoneDry.tgt = if (s.phoneOn) 0.0 else 1.0
        phoneWet.tgt = if (s.phoneOn) 1.0 else 0.0

        dryGain.tgt = 1.0 - s.reverbMix; wetGain.tgt = s.reverbMix.toDouble()
        master.tgt = s.volume.toDouble()
        room = s.room

        motion.movement = when (s.movement) { Movement.LOOP -> 0; Movement.PINGPONG -> 1; Movement.RANDOM -> 2 }
        motion.direction = if (s.clockwise) 1 else -1
        motion.intervalSeconds = s.intervalMs / 1000f
        motion.rotationSeconds = s.rotationSeconds
        motion.width = s.width; motion.offsetX = s.offsetX; motion.offsetZ = s.offsetZ
        motion.transitionSeconds = s.transitionSeconds
        motion.roomRadius = ROOM_RADIUS

        if (s.reverbDecay != builtDecay) requestReverb(s.reverbDecay)
    }

    /** Kurva kompresor "soft knee" 30 dB: berapa dB penurunan gain untuk level masukan x dB. */
    private fun gainReductionDb(x: Double): Double {
        val over = x - compThr
        return when {
            2 * over < -KNEE -> 0.0
            2 * abs(over) <= KNEE -> (1 / compRatio - 1) * (over + KNEE / 2) * (over + KNEE / 2) / (2 * KNEE)
            else -> over / compRatio - over
        }
    }

    // ---------- Pembuatan impuls reverb di thread terpisah (tidak mengganggu audio) ----------
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "reverb-ir").apply { isDaemon = true } }
    private val building = AtomicBoolean(false)
    @Volatile private var wantedDecay = -1f
    @Volatile private var builtDecay = -1f

    private fun requestReverb(decay: Float) {
        wantedDecay = decay
        if (!building.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (true) {
                    val d = wantedDecay
                    val ir = PartitionedConvolver.buildReverbIr(d, sampleRate)
                    convL = PartitionedConvolver(ir[0]); convR = PartitionedConvolver(ir[1])
                    builtDecay = d
                    if (d == wantedDecay) break
                }
            } finally {
                building.set(false)
                if (wantedDecay != builtDecay) requestReverb(wantedDecay)
            }
        }
    }

    /** Hentikan thread pembuat reverb. Wajib dipanggil saat mesin dibuang (mis. onReset), agar thread tidak bocor. */
    fun close() {
        executor.shutdownNow()
    }

    /** Dipanggil saat seek/flush: bersihkan semua memori suara. */
    fun reset() {
        eqL.forEach { it.reset() }; eqR.forEach { it.reset() }
        bassL.reset(); bassR.reset(); hpL.reset(); hpR.reset(); lpL.reset(); lpR.reset()
        wahL.reset(); wahR.reset(); phHpL.reset(); phHpR.reset(); phLpL.reset(); phLpR.reset()
        echoBufL.fill(0f); echoBufR.fill(0f); chBufL.fill(0f); chBufR.fill(0f)
        panner.reset(); grDb = 0.0
        convL?.reset(); convR?.reset()
    }

    // ================== Pemrosesan audio ==================

    private fun readDelay(buf: FloatArray, w: Int, d: Double): Float {
        val dd = d.coerceIn(1.0, (buf.size - 3).toDouble())
        val di = dd.toInt()
        val frac = (dd - di).toFloat()
        var i0 = w - di; if (i0 < 0) i0 += buf.size
        var i1 = i0 - 1; if (i1 < 0) i1 += buf.size
        return buf[i0] * (1 - frac) + buf[i1] * frac
    }

    private fun shape(x: Float): Float {
        val c = x.coerceIn(-1f, 1f)
        return (tanh(c * driveK) / driveNorm).toFloat()
    }

    /** Proses `n` (<= 128) sampel stereo. */
    fun process(inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray, n: Int,
                s: MixerSettings, path: FloatArray, override: FloatArray?) {
        if (s !== lastSettings) { applySettings(s); lastSettings = s }
        if (path !== lastPath) { motion.path = path; lastPath = path }
        motion.override = override

        // --- Pembaruan per blok: parameter halus + koefisien filter ---
        val blockSec = n / fs
        val a = 1 - exp(-blockSec / 0.03)
        for (i in 0 until 10) {
            val g = eqGain[i].step(a)
            if (abs(g - eqLast[i]) > 1e-4 || eqLast[i].isNaN()) {
                eqL[i].peaking(fs, EQ_FREQUENCIES[i].toDouble(), 1.1, g); eqR[i].peaking(fs, EQ_FREQUENCIES[i].toDouble(), 1.1, g)
                eqLast[i] = g
            }
        }
        bassGain.step(a).let { if (abs(it - bassLast) > 1e-4 || bassLast.isNaN()) { bassL.lowShelf(fs, 100.0, it); bassR.lowShelf(fs, 100.0, it); bassLast = it } }
        hpFreq.step(a).let { if (abs(it - hpLast) > 0.01 || hpLast.isNaN()) { hpL.highpass(fs, it, 0.707); hpR.highpass(fs, it, 0.707); hpLast = it } }
        lpFreq.step(a).let { if (abs(it - lpLast) > 0.5 || lpLast.isNaN()) { lpL.lowpass(fs, it, 0.707); lpR.lowpass(fs, it, 0.707); lpLast = it } }
        echoDelay.step(a); echoFb.step(a); echoMix.step(a); chWet.step(a); tremBase.step(a)
        wahDry.step(a); wahWet.step(a); phoneDry.step(a); phoneWet.step(a)
        dryGain.step(a); wetGain.step(a); master.step(a)

        val wahActive = wahWet.cur > 0.001 || wahWet.tgt > 0
        if (wahActive) {
            val f = 1100.0 + sin(wahPhase) * wahHz
            wahL.bandpass(fs, f, 3.0); wahR.bandpass(fs, f, 3.0)
        }

        motion.step(blockSec)
        panner.setPosition(motion.outX, motion.outZ, room)

        val phoneActive = phoneWet.cur > 0.001 || phoneWet.tgt > 0
        val cl = convL; val cr = convR
        val reverbActive = cl != null && cr != null && (wetGain.cur > 0.001 || wetGain.tgt > 0)
        val chSize = chBufL.size
        val echoSize = echoBufL.size
        val twoPi = 2 * PI

        for (i in 0 until n) {
            var l = inL[i]; var r = inR[i]

            for (b in 0 until 10) { l = eqL[b].process(l); r = eqR[b].process(r) }
            l = bassL.process(l); r = bassR.process(r)
            l = hpL.process(l); r = hpR.process(r)
            l = lpL.process(l); r = lpR.process(r)

            if (driveK > 0) { l = shape(l); r = shape(r) }

            if (compOn) {
                val det = max(abs(l), abs(r))
                val xdb = if (det < 1e-6f) -120.0 else 20.0 * log10(det.toDouble())
                val target = gainReductionDb(xdb)
                grDb += (target - grDb) * (if (target < grDb) atkCoef else relCoef)
                val g = exp((grDb + compMakeupDb) * 0.11512925).toFloat()   // dB -> linear
                l *= g; r *= g
            }

            // Echo: keluaran = masukan + mix * tertunda; umpan balik = masukan + fb * tertunda
            val dl = readDelay(echoBufL, echoW, echoDelay.cur)
            val dr = readDelay(echoBufR, echoW, echoDelay.cur)
            echoBufL[echoW] = l + (echoFb.cur * dl).toFloat(); echoBufR[echoW] = r + (echoFb.cur * dr).toFloat()
            echoW = if (echoW + 1 == echoSize) 0 else echoW + 1
            l += (echoMix.cur * dl).toFloat(); r += (echoMix.cur * dr).toFloat()

            // Chorus: salinan tertunda yang waktu tundanya bergoyang
            val chDelay = 0.02 * fs + sin(chPhase) * chDepthSamples
            val cdl = readDelay(chBufL, chW, chDelay); val cdr = readDelay(chBufR, chW, chDelay)
            chBufL[chW] = l; chBufR[chW] = r
            chW = if (chW + 1 == chSize) 0 else chW + 1
            l += (chWet.cur * cdl).toFloat(); r += (chWet.cur * cdr).toFloat()
            chPhase += twoPi * chRate / fs; if (chPhase > twoPi) chPhase -= twoPi

            // Tremolo: volume bergetar
            val tg = (tremBase.cur + sin(tremPhase) * tremHalf).toFloat()
            l *= tg; r *= tg
            tremPhase += twoPi * tremRate / fs; if (tremPhase > twoPi) tremPhase -= twoPi

            // Wah-wah
            if (wahActive) {
                val wl = wahL.process(l); val wr = wahR.process(r)
                l = (wahDry.cur * l + wahWet.cur * wl).toFloat(); r = (wahDry.cur * r + wahWet.cur * wr).toFloat()
            } else { l = (wahDry.cur * l).toFloat(); r = (wahDry.cur * r).toFloat() }
            wahPhase += twoPi * wahRate / fs; if (wahPhase > twoPi) wahPhase -= twoPi

            // Efek telepon / radio jadul
            if (phoneActive) {
                val phl = phLpL.process(phHpL.process(l)); val phr = phLpR.process(phHpR.process(r))
                l = (phoneDry.cur * l + phoneWet.cur * phl).toFloat(); r = (phoneDry.cur * r + phoneWet.cur * phr).toFloat()
            }

            // Pemosisi 8D (masukan stereo diubah ke mono dulu, seperti PannerNode)
            panner.process((l + r) * 0.5f)
            val pl = panner.outL; val pr = panner.outR

            var sumL = (dryGain.cur * pl).toFloat(); var sumR = (dryGain.cur * pr).toFloat()
            if (reverbActive) {
                sumL += (wetGain.cur * cl!!.tick(pl)).toFloat(); sumR += (wetGain.cur * cr!!.tick(pr)).toFloat()
            }
            outL[i] = (sumL * master.cur).toFloat(); outR[i] = (sumR * master.cur).toFloat()
        }
        wahPhase %= twoPi

        sinceReport += n
        if (sinceReport >= sampleRate / 30) {
            sinceReport = 0
            onFrame(motion.normX, motion.normZ, motion.progress.toFloat())
        }
    }

    companion object {
        private const val ROOM_RADIUS = 2.5f
        private const val KNEE = 30.0
    }
}
