package dev.bagja.streamvortex.pengambil_media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TingkatLog(val kode: Char) { DEBUG('D'), INFO('I'), PERINGATAN('W'), GALAT('E') }

enum class AsalLog { APLIKASI, SCRAPER, HTTP, JS, UNDUH, MIXER, PEMUTAR, PEMBARUAN }

data class EntriLog(
    val id: Long,
    val waktuMs: Long,
    val tingkat: TingkatLog,
    val asal: AsalLog,
    val pesan: String,
)

/**
 * Jurnal debug seluruh aplikasi (di memori, dibatasi [MAKS_ENTRI]). Dipakai mesin scraper, unduhan, mixer,
 * pembaruan, dan panel debug. Aman dipanggil dari thread mana pun; JANGAN dipanggil dari thread audio
 * (mengalokasi objek per panggilan).
 *
 * Privasi: hanya catat host+path URL ([sensorUrl]); jangan pernah mencatat cookie, token, atau isi query.
 */
object JurnalDebug {
    const val MAKS_ENTRI = 1500
    const val MAKS_PANJANG_PESAN = 1500

    private val _entri = MutableStateFlow<List<EntriLog>>(emptyList())
    val entri: StateFlow<List<EntriLog>> = _entri.asStateFlow()

    /** Mencatat potongan isi respons untuk server yang gagal (membantu menganalisis perubahan situs). */
    private val _rinci = MutableStateFlow(true)
    val rinci: StateFlow<Boolean> = _rinci.asStateFlow()

    /** Diisi platform: waktu sekarang (epoch ms) dan selisih zona waktu lokal terhadap UTC. */
    @Volatile var jam: () -> Long = { 0L }

    @Volatile var offsetZonaMs: Long = 0L

    /** Diisi platform untuk menyimpan ke berkas (agar log sesi sebelumnya/crash tetap ada). */
    @Volatile var penulis: ((String) -> Unit)? = null

    fun aturRinci(nilai: Boolean) {
        _rinci.value = nilai
    }

    fun catat(asal: AsalLog, tingkat: TingkatLog, pesan: String) {
        val potong = if (pesan.length > MAKS_PANJANG_PESAN) pesan.take(MAKS_PANJANG_PESAN) + "…(dipotong)" else pesan
        val waktu = jam()
        var entriBaru: EntriLog? = null
        _entri.update { daftar ->
            val id = (daftar.lastOrNull()?.id ?: 0L) + 1
            val e = EntriLog(id, waktu, tingkat, asal, potong)
            entriBaru = e
            val gabung = daftar + e
            if (gabung.size > MAKS_ENTRI) gabung.subList(gabung.size - MAKS_ENTRI, gabung.size).toList() else gabung
        }
        entriBaru?.let { penulis?.invoke(format(it)) }
    }

    fun d(asal: AsalLog, pesan: String) = catat(asal, TingkatLog.DEBUG, pesan)

    fun i(asal: AsalLog, pesan: String) = catat(asal, TingkatLog.INFO, pesan)

    fun w(asal: AsalLog, pesan: String) = catat(asal, TingkatLog.PERINGATAN, pesan)

    fun e(asal: AsalLog, pesan: String) = catat(asal, TingkatLog.GALAT, pesan)

    fun bersihkan() {
        _entri.value = emptyList()
    }

    /** "13:05:09.042 E SCRAPER pesan" (zona waktu lokal bila [offsetZonaMs] diisi). */
    fun format(e: EntriLog): String = "${jamTeks(e.waktuMs + offsetZonaMs)} ${e.tingkat.kode} ${e.asal.name} ${e.pesan}"

    fun teks(minimal: TingkatLog = TingkatLog.DEBUG, asal: Set<AsalLog>? = null): String =
        _entri.value.asSequence()
            .filter { it.tingkat.ordinal >= minimal.ordinal && (asal == null || it.asal in asal) }
            .joinToString("\n") { format(it) }

    private fun jamTeks(ms: Long): String {
        val hari = 86_400_000L
        val s = ((ms % hari) + hari) % hari
        fun dua(n: Long) = n.toString().padStart(2, '0')
        return "${dua(s / 3_600_000)}:${dua(s / 60_000 % 60)}:${dua(s / 1000 % 60)}.${(s % 1000).toString().padStart(3, '0')}"
    }

    /** Hanya skema+host+path: query (sering berisi token) dan fragmen dibuang. */
    fun sensorUrl(url: String): String {
        val tanpa = url.substringBefore('#')
        val kepala = tanpa.substringBefore('?')
        return if (tanpa.contains('?')) "$kepala?…" else kepala
    }

    /** Potongan isi respons satu baris (spasi dipadatkan) untuk analisis; kosong bila mode rinci mati. */
    fun cuplikan(isi: String?, maks: Int = 220): String {
        if (!_rinci.value || isi.isNullOrEmpty()) return ""
        val padat = isi.replace(Regex("\\s+"), " ").trim()
        return if (padat.length > maks) padat.take(maks) + "…" else padat
    }
}
