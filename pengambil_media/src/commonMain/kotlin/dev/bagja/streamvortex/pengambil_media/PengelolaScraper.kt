package dev.bagja.streamvortex.pengambil_media

import kotlinx.coroutines.CancellationException

/**
 * Satu mesin ekstraksi nyata (adapter Scrapr, mesin YouTube YMusic, dst.).
 * Implementasi WAJIB benar-benar memanggil mesinnya; dilarang mengembalikan data contoh.
 */
interface PengambilMedia {
    val nama: String

    fun mendukung(platform: Platform): Boolean

    /** Lempar [KegagalanEkstraksi] jika gagal. Pembatalan coroutine tidak boleh ditelan. */
    suspend fun ekstrak(url: String, platform: Platform): HasilMedia
}

/**
 * Alur: validasi -> deteksi platform -> mesin berurutan (utama, lalu fallback) -> hasil.
 * Urutan [mesin] = prioritas. Setiap kegagalan dicatat, tidak ditelan.
 */
class PengelolaScraper(private val mesin: List<PengambilMedia>) {

    suspend fun ekstrak(masukan: String): HasilPengelola {
        val valid = when (val v = validasiUrl(masukan)) {
            is HasilValidasi.TidakValid -> return HasilPengelola.Gagal(KategoriGagal.URL_TIDAK_VALID, v.alasan, emptyList())
            is HasilValidasi.Valid -> v
        }
        val platform = deteksiPlatform(valid.host)
        if (platform == Platform.TIDAK_DIKENAL) {
            return HasilPengelola.Gagal(
                KategoriGagal.PLATFORM_TIDAK_DIDUKUNG,
                "Situs ${valid.host} belum didukung.",
                emptyList(),
            )
        }
        val kandidat = mesin.filter { it.mendukung(platform) }
        if (kandidat.isEmpty()) {
            return HasilPengelola.Gagal(
                KategoriGagal.PLATFORM_TIDAK_DIDUKUNG,
                "Belum ada mesin untuk ${platform.namaTampil}.",
                emptyList(),
            )
        }

        val gagal = mutableListOf<PercobaanMesin>()
        for (m in kandidat) {
            try {
                val hasil = m.ekstrak(valid.url, platform)
                if (hasil.formatMedia.isEmpty()) {
                    gagal += PercobaanMesin(m.nama, KegagalanEkstraksi(KategoriGagal.TIDAK_ADA_MEDIA, "Tidak ada media ditemukan."))
                    continue
                }
                return HasilPengelola.Sukses(hasil, gagal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KegagalanEkstraksi) {
                gagal += PercobaanMesin(m.nama, e)
            } catch (e: Exception) {
                gagal += PercobaanMesin(m.nama, KegagalanEkstraksi(KategoriGagal.INTERNAL, "Terjadi kesalahan pada mesin ${m.nama}.", e))
            }
        }
        // Pesan yang paling berguna bagi pengguna: pembatasan > jaringan > sisanya.
        val utama = gagal.map { it.kegagalan }.let { daftar ->
            daftar.firstOrNull { it.kategori == KategoriGagal.DIBATASI }
                ?: daftar.firstOrNull { it.kategori == KategoriGagal.JARINGAN }
                ?: daftar.last()
        }
        return HasilPengelola.Gagal(utama.kategori, utama.pesan, gagal)
    }
}
