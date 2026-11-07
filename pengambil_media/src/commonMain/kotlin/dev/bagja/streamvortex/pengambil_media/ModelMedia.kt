package dev.bagja.streamvortex.pengambil_media

/** Platform sumber yang dikenali. Fitur yang ditunda (terabox, anime, komik, bypass tautan) sengaja tidak ada. */
enum class Platform(val namaTampil: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    TWITTER("X / Twitter"),
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud"),
    PINTEREST("Pinterest"),
    APPLE_MUSIC("Apple Music"),
    BILIBILI("Bilibili"),
    DOUYIN("Douyin"),
    BANDCAMP("Bandcamp"),
    THREADS("Threads"),
    PIXIV("Pixiv"),
    REDNOTE("RedNote"),
    REDDIT("Reddit"),
    TIDAK_DIKENAL("Tidak dikenal"),
}

enum class JenisMedia { VIDEO, AUDIO, GAMBAR, CAMPURAN }

enum class StatusEkstraksi { BERHASIL, SEBAGIAN, GAGAL }

/** Satu pilihan unduhan (satu resolusi / satu bitrate / satu berkas gambar). */
data class FormatMedia(
    val id: String,
    val url: String,
    val jenis: JenisMedia,
    /** Tanpa titik, mis. "mp4", "m4a", "mp3", "jpg". */
    val ekstensi: String,
    /** Tinggi video dalam piksel; null untuk audio/gambar atau jika tidak diketahui. */
    val tinggi: Int? = null,
    val bitrateKbps: Int? = null,
    /** Null jika sumber tidak melaporkan ukuran. */
    val ukuranByte: Long? = null,
    /** Dukungan lanjutkan (HTTP Range); null = tidak diketahui. */
    val bisaDilanjutkan: Boolean? = null,
    /** True jika [url] masih tautan semu (mis. "ytmp3gg_resolve:...") yang harus diselesaikan mesin dulu. */
    val perluResolusi: Boolean = false,
    /** JSON asli dari mesin; dibutuhkan untuk tahap resolusi. Bukan untuk ditampilkan. */
    val dataMentah: String? = null,
) {
    /** Label ringkas untuk UI, mis. "MP4 · 720p" atau "MP3 · 128 kbps". */
    val label: String
        get() = buildString {
            append(ekstensi.uppercase())
            tinggi?.let { append(" · ${it}p") }
            bitrateKbps?.let { append(" · $it kbps") }
        }
}

/** Hasil ekstraksi standar dari mesin mana pun (Scrapr dan lainnya). */
data class HasilMedia(
    val judul: String,
    val deskripsi: String? = null,
    val thumbnail: String? = null,
    val durasiDetik: Long? = null,
    val penulis: String? = null,
    val urlSumber: String,
    val platform: Platform,
    val formatMedia: List<FormatMedia>,
    val jenisMedia: JenisMedia,
    val statusEkstraksi: StatusEkstraksi,
    /** Nama mesin yang menghasilkan data ini. */
    val mesin: String,
)

enum class KategoriGagal {
    URL_TIDAK_VALID,
    PLATFORM_TIDAK_DIDUKUNG,
    JARINGAN,

    /** Perlu login, konten privat, atau dibatasi/rate limit: ditampilkan jelas, tidak dilewati. */
    DIBATASI,
    TIDAK_ADA_MEDIA,
    INTERNAL,
}

/** Dilempar mesin ekstraksi saat gagal. [pesan] aman ditampilkan; detail teknis cukup di [cause]. */
class KegagalanEkstraksi(
    val kategori: KategoriGagal,
    val pesan: String,
    cause: Throwable? = null,
) : Exception(pesan, cause)

/** Catatan satu percobaan mesin (berguna untuk log dan UI "mengapa gagal"). */
data class PercobaanMesin(val mesin: String, val kegagalan: KegagalanEkstraksi)

/** Hasil akhir PengelolaScraper: sukses, atau gagal lengkap dengan seluruh percobaan. */
sealed interface HasilPengelola {
    data class Sukses(val hasil: HasilMedia, val percobaanGagal: List<PercobaanMesin>) : HasilPengelola

    data class Gagal(
        val kategori: KategoriGagal,
        val pesan: String,
        val percobaan: List<PercobaanMesin>,
    ) : HasilPengelola
}
