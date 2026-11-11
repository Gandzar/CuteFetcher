package dev.bagja.streamvortex.pengambil_media

/** Hasil uji satu server scraper (untuk panel debug). */
data class HasilUjiServer(
    val server: String,
    val sukses: Boolean,
    val durasiMs: Long,
    val pesan: String,
)

/** Hasil uji satu alamat (koneksi dasar, di luar scraper). */
data class HasilUjiKoneksi(
    val nama: String,
    val sukses: Boolean,
    val durasiMs: Long,
    val pesan: String,
)

/** Alat diagnosa mesin ekstraksi, dipakai panel debug. */
interface PenguiMesin {
    /** Uji koneksi HTTP dasar lewat OkHttp (di luar WebView) ke beberapa host. */
    suspend fun ujiKoneksi(): List<HasilUjiKoneksi>

    /** Jalankan SETIAP server untuk [url] satu per satu (tanpa berhenti di yang pertama berhasil). */
    suspend fun ujiSemuaServer(url: String): List<HasilUjiServer>

    /** Teks ringkas status mesin (WebView siap/tidak, jumlah permintaan, dll.). */
    fun statusMesin(): String
}

/** Aksi laporan yang butuh platform (Android: Intent). */
interface AksiLaporan {
    /** Informasi perangkat/aplikasi untuk disertakan di laporan (versi, Android, model, ABI, status mixer). */
    fun infoPerangkat(): String

    /** Log sesi sebelumnya (mis. sebelum crash) bila ada. */
    fun logSesiSebelumnya(): String?

    /** Buka pemilih "Bagikan" dengan teks. */
    fun bagikanTeks(judul: String, isi: String)

    /** Buka aplikasi email ke [alamat] dengan subjek dan isi. */
    fun kirimEmail(alamat: String, subjek: String, isi: String)
}
