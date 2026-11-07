package dev.bagja.streamvortex.pengolah_audio.dsp

import dev.bagja.streamvortex.pengolah_audio.data.Pt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Jalur gerak suara 8D (port dari paths.ts Mixer8D).
 * Sumbu: x = kiri(-1)..kanan(+1), z = depan(-1)..belakang(+1).
 * Hasil berupa FloatArray berselang-seling [x0,z0,x1,z1,...] sebanyak 160 titik.
 */
object PathLib {
    const val SAMPLES = 160
    const val CUSTOM = "custom"
    const val LISSAJOUS = "lissajous"
    private const val TAU = 2 * PI

    class Builtin(val id: String, val label: String, val hint: String, val build: () -> FloatArray)

    private fun sample(count: Int, fn: (Double) -> DoubleArray): FloatArray {
        val out = FloatArray(count * 2)
        for (i in 0 until count) {
            val p = fn(i.toDouble() / count)
            out[2 * i] = p[0].toFloat(); out[2 * i + 1] = p[1].toFloat()
        }
        return out
    }

    private fun line(ax: Double, az: Double, bx: Double, bz: Double) = sample(SAMPLES) { t ->
        val k = if (t < 0.5) t * 2 else (1 - t) * 2
        doubleArrayOf(ax + (bx - ax) * k, az + (bz - az) * k)
    }

    val builtin: List<Builtin> = listOf(
        Builtin("kiri-kanan", "Kiri ke Kanan", "Suara bergerak dari telinga kiri ke kanan") { line(-1.0, 0.0, 1.0, 0.0) },
        Builtin("depan-belakang", "Depan ke Belakang", "Suara bergerak dari depan ke belakang kepala") { line(0.0, -1.0, 0.0, 1.0) },
        Builtin("diagonal", "Diagonal", "Garis silang dari kiri depan ke kanan belakang") { line(-0.85, -0.85, 0.85, 0.85) },
        Builtin("lingkaran", "Lingkaran", "Mengelilingi kepala secara penuh") {
            sample(SAMPLES) { t -> doubleArrayOf(sin(t * TAU), -cos(t * TAU)) }
        },
        Builtin("elips", "Elips Lebar", "Lingkaran lonjong, lebih banyak di sisi kiri-kanan") {
            sample(SAMPLES) { t -> doubleArrayOf(sin(t * TAU), -0.5 * cos(t * TAU)) }
        },
        Builtin("angka-8", "Angka 8", "Pola melayang depan dan belakang bergantian") {
            sample(SAMPLES) { t -> doubleArrayOf(sin(t * TAU), -0.6 * sin(2 * t * TAU)) }
        },
        Builtin("bintang", "Bintang", "Lima sudut tajam berputar di sekeliling kepala") {
            sample(SAMPLES) { t ->
                val a = t * TAU
                val r = 0.6 + 0.4 * cos(5 * a / 2)
                doubleArrayOf(r * sin(a), -r * cos(a))
            }
        },
    )

    fun builtinById(id: String) = builtin.firstOrNull { it.id == id }

    fun lissajous(freqX: Int, freqZ: Int, phaseDeg: Int): FloatArray {
        val a = max(1, freqX); val b = max(1, freqZ)
        val phase = phaseDeg * PI / 180
        return sample(SAMPLES) { t -> doubleArrayOf(sin(a * t * TAU + phase), sin(b * t * TAU)) }
    }

    // ---- Penghalusan jalur gambar sendiri ----

    private fun resampleClosed(pts: List<DoubleArray>, count: Int): List<DoubleArray> {
        val n = pts.size
        val cum = DoubleArray(n + 1)
        for (i in 0 until n) {
            val a = pts[i]; val b = pts[(i + 1) % n]
            cum[i + 1] = cum[i] + hypot(b[0] - a[0], b[1] - a[1])
        }
        val total = cum[n]
        if (total == 0.0) return List(count) { pts[0].copyOf() }
        val result = ArrayList<DoubleArray>(count)
        var seg = 0
        for (k in 0 until count) {
            val target = k.toDouble() / count * total
            while (seg < n - 1 && cum[seg + 1] < target) seg++
            val segLen = (cum[seg + 1] - cum[seg]).let { if (it == 0.0) 1.0 else it }
            val t = (target - cum[seg]) / segLen
            val a = pts[seg]; val b = pts[(seg + 1) % n]
            result.add(doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
        }
        return result
    }

    /** Chaikin corner-cutting: setiap iterasi membuat sudut makin bulat. */
    private fun chaikin(pts: List<DoubleArray>, iterations: Int): List<DoubleArray> {
        var cur = pts
        repeat(iterations) {
            val next = ArrayList<DoubleArray>(cur.size * 2)
            for (i in cur.indices) {
                val p = cur[i]; val q = cur[(i + 1) % cur.size]
                next.add(doubleArrayOf(0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]))
                next.add(doubleArrayOf(0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]))
            }
            cur = next
        }
        return cur
    }

    private fun fitToUnitBox(pts: List<DoubleArray>): List<DoubleArray> {
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (p in pts) { minX = min(minX, p[0]); maxX = max(maxX, p[0]); minZ = min(minZ, p[1]); maxZ = max(maxZ, p[1]) }
        val cx = (minX + maxX) / 2; val cz = (minZ + maxZ) / 2
        val half = (max(maxX - minX, maxZ - minZ) / 2).let { if (it == 0.0) 1.0 else it }
        return pts.map { doubleArrayOf((it[0] - cx) / half, (it[1] - cz) / half) }
    }

    fun smoothCustom(raw: List<Pt>, level: Int): FloatArray {
        if (raw.size < 3) return builtinById("lingkaran")!!.build()
        val resampled = resampleClosed(raw.map { doubleArrayOf(it.x.toDouble(), it.z.toDouble()) }, 64)
        val smoothed = chaikin(resampled, level.coerceIn(0, 8))
        val fitted = fitToUnitBox(resampleClosed(smoothed, SAMPLES))
        val out = FloatArray(SAMPLES * 2)
        fitted.forEachIndexed { i, p -> out[2 * i] = p[0].toFloat(); out[2 * i + 1] = p[1].toFloat() }
        return out
    }

    /** Titik jalur sesuai pilihan pengaturan. */
    fun resolve(pathId: String, customRaw: List<Pt>, smoothLevel: Int, fx: Int, fz: Int, phase: Int): FloatArray = when (pathId) {
        CUSTOM -> smoothCustom(customRaw, smoothLevel)
        LISSAJOUS -> lissajous(fx, fz, phase)
        else -> (builtinById(pathId) ?: builtin[0]).build()
    }

}
