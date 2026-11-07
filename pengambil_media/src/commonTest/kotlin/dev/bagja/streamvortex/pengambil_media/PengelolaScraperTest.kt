package dev.bagja.streamvortex.pengambil_media

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PengelolaScraperTest {
    private class Palsu(
        override val nama: String,
        private val platform: Set<Platform>,
        private val aksi: () -> HasilMedia,
    ) : PengambilMedia {
        var dipanggil = 0
        override fun mendukung(platform: Platform) = platform in this.platform
        override suspend fun ekstrak(url: String, platform: Platform): HasilMedia {
            dipanggil++
            return aksi()
        }
    }

    private fun hasil(mesin: String, format: List<FormatMedia> = listOf(FormatMedia("1", "https://x/y.mp4", JenisMedia.VIDEO, "mp4", tinggi = 720))) =
        HasilMedia("Judul", null, null, 10, "Penulis", "https://www.tiktok.com/@a/video/1", Platform.TIKTOK, format, JenisMedia.VIDEO, StatusEkstraksi.BERHASIL, mesin)

    @Test fun urlKosongDitolak() = runTest {
        val r = PengelolaScraper(emptyList()).ekstrak("  ")
        assertEquals(KategoriGagal.URL_TIDAK_VALID, assertIs<HasilPengelola.Gagal>(r).kategori)
    }

    @Test fun skemaAneh_ditolak() = runTest {
        val r = PengelolaScraper(emptyList()).ekstrak("ftp://tiktok.com/a")
        assertEquals(KategoriGagal.URL_TIDAK_VALID, assertIs<HasilPengelola.Gagal>(r).kategori)
    }

    @Test fun domainPolosDiberiHttps() {
        val v = assertIs<HasilValidasi.Valid>(validasiUrl("www.tiktok.com/@a/video/1"))
        assertEquals("https://www.tiktok.com/@a/video/1", v.url)
        assertEquals("www.tiktok.com", v.host)
    }

    @Test fun deteksiPlatform_domainMirip_tidakSalah() {
        assertEquals(Platform.TIKTOK, deteksiPlatform("vm.tiktok.com"))
        assertEquals(Platform.TIDAK_DIKENAL, deteksiPlatform("nottiktok.com"))
        assertEquals(Platform.TWITTER, deteksiPlatform("x.com"))
        assertEquals(Platform.TIDAK_DIKENAL, deteksiPlatform("box.com"))
    }

    @Test fun platformTakDikenal_gagalTanpaMemanggilMesin() = runTest {
        val m = Palsu("a", setOf(Platform.TIKTOK)) { hasil("a") }
        val r = assertIs<HasilPengelola.Gagal>(PengelolaScraper(listOf(m)).ekstrak("https://contoh.com/a"))
        assertEquals(KategoriGagal.PLATFORM_TIDAK_DIDUKUNG, r.kategori)
        assertEquals(0, m.dipanggil)
    }

    @Test fun fallback_dipakaiSetelahMesinPertamaGagal() = runTest {
        val a = Palsu("a", setOf(Platform.TIKTOK)) { throw KegagalanEkstraksi(KategoriGagal.JARINGAN, "putus") }
        val b = Palsu("b", setOf(Platform.TIKTOK)) { hasil("b") }
        val r = assertIs<HasilPengelola.Sukses>(PengelolaScraper(listOf(a, b)).ekstrak("https://www.tiktok.com/@a/video/1"))
        assertEquals("b", r.hasil.mesin)
        assertEquals(1, r.percobaanGagal.size)
        assertEquals("a", r.percobaanGagal.single().mesin)
    }

    @Test fun mesinTanpaFormat_dianggapGagal_lalu_fallback() = runTest {
        val a = Palsu("a", setOf(Platform.TIKTOK)) { hasil("a", emptyList()) }
        val b = Palsu("b", setOf(Platform.TIKTOK)) { hasil("b") }
        val r = assertIs<HasilPengelola.Sukses>(PengelolaScraper(listOf(a, b)).ekstrak("https://www.tiktok.com/@a/video/1"))
        assertEquals("b", r.hasil.mesin)
        assertEquals(KategoriGagal.TIDAK_ADA_MEDIA, r.percobaanGagal.single().kegagalan.kategori)
    }

    @Test fun semuaGagal_pesanDibatasiDiutamakan() = runTest {
        val a = Palsu("a", setOf(Platform.TIKTOK)) { throw KegagalanEkstraksi(KategoriGagal.DIBATASI, "Konten privat.") }
        val b = Palsu("b", setOf(Platform.TIKTOK)) { throw IllegalStateException("boom") }
        val r = assertIs<HasilPengelola.Gagal>(PengelolaScraper(listOf(a, b)).ekstrak("https://www.tiktok.com/@a/video/1"))
        assertEquals(KategoriGagal.DIBATASI, r.kategori)
        assertEquals("Konten privat.", r.pesan)
        assertEquals(2, r.percobaan.size)
        assertTrue(r.percobaan.last().kegagalan.kategori == KategoriGagal.INTERNAL)
    }
}
