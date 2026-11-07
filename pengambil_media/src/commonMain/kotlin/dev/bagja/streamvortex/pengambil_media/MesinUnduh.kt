package dev.bagja.streamvortex.pengambil_media

import kotlinx.coroutines.flow.StateFlow

enum class StatusUnduhan { MENUNGGU, BERJALAN, DIJEDA, SELESAI, GAGAL, DIBATALKAN }

/** Satu baris antrean unduhan. */
data class ItemUnduhan(
    val id: String,
    val judul: String,
    val label: String,
    val thumbnail: String?,
    val byteSelesai: Long,
    /** Null jika server tidak melaporkan ukuran. */
    val byteTotal: Long?,
    val status: StatusUnduhan,
    /** Alasan yang aman ditampilkan; null kecuali status GAGAL. */
    val pesanGagal: String? = null,
    /** Apakah jeda/lanjut tersedia (server mendukung Range). */
    val bisaDijeda: Boolean = false,
) {
    /** 0..1, atau null jika ukuran total tidak diketahui. */
    val progres: Float?
        get() = byteTotal?.takeIf { it > 0 }?.let { (byteSelesai.toFloat() / it).coerceIn(0f, 1f) }
}

/**
 * Kontrak mesin unduh HTTP umum (untuk hasil scraper). Implementasi nyata ada di modul lain
 * (:layanan_unduh, Tahap 7); `:download` milik YMusic khusus YouTube SABR dan tidak dipakai di sini.
 */
interface MesinUnduh {
    val antrean: StateFlow<List<ItemUnduhan>>

    /** Masukkan ke antrean; mengembalikan id item. */
    suspend fun tambah(hasil: HasilMedia, format: FormatMedia): String

    fun jeda(id: String)

    fun lanjutkan(id: String)

    fun batalkan(id: String)

    /** Hapus baris dari riwayat antrean (bukan berkasnya). */
    fun bersihkan(id: String)
}
