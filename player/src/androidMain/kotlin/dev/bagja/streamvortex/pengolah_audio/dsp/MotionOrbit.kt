package dev.bagja.streamvortex.pengolah_audio.dsp

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

/**
 * Penggerak posisi suara 8D (port 1:1 dari motion-orbit.worklet.js Mixer8D).
 * Setiap blok audio (128 sampel) posisi digeser sepanjang jalur sesuai mode:
 * putar (loop), bolak-balik (pingpong), atau loncat acak (random).
 */
class MotionOrbit {
    // ---- Konfigurasi (diisi dari pengaturan) ----
    var movement = 0            // 0 = loop, 1 = pingpong, 2 = random
    var direction = 1           // 1 = searah jarum jam, -1 = berlawanan
    var intervalSeconds = 1f
    var rotationSeconds = 12f
    var width = 1f
    var offsetX = 0f
    var offsetZ = 0f
    var transitionSeconds = 0f
    var roomRadius = 2.5f
    var path = FloatArray(2)    // [x0,z0,x1,z1,...]
    var override: FloatArray? = null

    // ---- Hasil ----
    var outX = 0f
        private set   // posisi sudah dikali radius ruang (untuk panner)
    var outZ = 0f
        private set
    var normX = 0f
        private set  // posisi -1..1 (untuk digambar di UI)
    var normZ = 0f
        private set
    var progress = 0.0
        private set

    private var flip = 1
    private var elapsed = 0.0
    private var randomTarget = 0.0
    private var smoothX = 0.0
    private var smoothZ = 0.0
    private var smoothReady = false

    private fun wrap01(v: Double) = ((v % 1.0) + 1.0) % 1.0

    private fun shortestDelta(from: Double, to: Double): Double {
        var d = to - from
        if (d > 0.5) d -= 1.0
        if (d < -0.5) d += 1.0
        return d
    }

    /** Ambil titik pada jalur untuk progres p (0..1) dengan interpolasi linear. */
    private fun samplePath(p: Double, out: DoubleArray) {
        val n = path.size / 2
        val f = p * n
        val index = floor(f).toInt() % n
        val t = f - floor(f)
        val a = index * 2
        val b = ((index + 1) % n) * 2
        out[0] = path[a] + (path[b] - path[a]) * t
        out[1] = path[a + 1] + (path[b + 1] - path[a + 1]) * t
    }

    private fun advance(dt: Double) {
        val minInterval = max(0.1, intervalSeconds.toDouble())
        if (movement == 2) {
            elapsed += dt
            if (elapsed >= minInterval) { elapsed = 0.0; randomTarget = Random.nextDouble() }
            val glide = 1 - exp(-dt * 2.5)
            progress = wrap01(progress + shortestDelta(progress, randomTarget) * glide)
            return
        }
        if (movement == 1) {
            elapsed += dt
            if (elapsed >= minInterval) { elapsed = 0.0; flip = -flip }
        } else { flip = 1; elapsed = 0.0 }
        progress = wrap01(progress + direction * flip * dt / max(1.0, rotationSeconds.toDouble()))
    }

    private val tmp = DoubleArray(2)

    /** Majukan satu blok audio sepanjang `blockSeconds` detik. */
    fun step(blockSeconds: Double) {
        advance(blockSeconds)
        val ov = override
        val targetX: Double; val targetZ: Double; val tau: Double
        if (ov != null) {
            targetX = ov[0].toDouble(); targetZ = ov[1].toDouble(); tau = 0.05
        } else {
            samplePath(progress, tmp)
            targetX = tmp[0] * width + offsetX
            targetZ = tmp[1] * width + offsetZ
            tau = transitionSeconds / 3.0
        }
        if (!smoothReady) { smoothX = targetX; smoothZ = targetZ; smoothReady = true }
        else {
            val alpha = if (tau <= 0.001) 1.0 else 1 - exp(-blockSeconds / tau)
            smoothX += (targetX - smoothX) * alpha
            smoothZ += (targetZ - smoothZ) * alpha
        }
        outX = (smoothX * roomRadius).toFloat()
        outZ = (smoothZ * roomRadius).toFloat()
        normX = smoothX.toFloat(); normZ = smoothZ.toFloat()
    }
}
