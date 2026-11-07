package dev.bagja.streamvortex.pengambil_media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PemetaHasilNimiyoTest {
    private fun petakan(json: String) = PemetaHasilNimiyo.petakan(Platform.TIKTOK, "nimiyo", "https://www.tiktok.com/@a/video/1", json)

    @Test fun videoDanAudio_dipetakanBenar() {
        val h = petakan(
            """{"title":"Halo","thumbnail":"https://i/x.jpg","author":"bagja","duration":"1:05",
               "downloads":[{"type":"MP4 720p","quality":"720p","url":"https://v/a.mp4","ext":"mp4","size":"1048576"},
                            {"type":"MP3 (128kbps)","url":"https://v/a.mp3"}]}""",
        )
        assertEquals("Halo", h.judul)
        assertEquals(65L, h.durasiDetik)
        assertEquals(JenisMedia.CAMPURAN, h.jenisMedia)
        val v = h.formatMedia[0]
        assertEquals(720, v.tinggi)
        assertEquals(1048576L, v.ukuranByte)
        assertEquals(JenisMedia.VIDEO, v.jenis)
        val a = h.formatMedia[1]
        assertEquals(JenisMedia.AUDIO, a.jenis)
        assertEquals("mp3", a.ekstensi)
        assertEquals(128, a.bitrateKbps)
        assertEquals(StatusEkstraksi.BERHASIL, h.statusEkstraksi)
    }

    @Test fun urlSemu_ditandaiPerluResolusi() {
        val f = petakan("""{"title":"x","downloads":[{"type":"MP3","url":"ytmp3gg_resolve:abc|||mp3|||128"}]}""").formatMedia.single()
        assertTrue(f.perluResolusi)
        assertEquals(JenisMedia.AUDIO, f.jenis)
    }

    @Test fun gambar_dikenali() {
        val h = petakan("""{"title":"x","downloads":[{"type":"IMAGE","url":"https://i/p.webp"}]}""")
        assertEquals(JenisMedia.GAMBAR, h.jenisMedia)
        assertEquals("webp", h.formatMedia.single().ekstensi)
    }

    @Test fun tanpaUnduhan_statusGagal_danUrlKosongDibuang() {
        val h = petakan("""{"title":"x","downloads":[{"type":"MP4","url":""}]}""")
        assertTrue(h.formatMedia.isEmpty())
        assertEquals(StatusEkstraksi.GAGAL, h.statusEkstraksi)
    }

    @Test fun durasiTakDikenal_null() {
        assertNull(petakan("""{"title":"x","duration":"abc","downloads":[]}""").durasiDetik)
    }

    @Test fun jsonRusak_melemparKegagalanInternal() {
        val e = assertFailsWith<KegagalanEkstraksi> { petakan("bukan json") }
        assertEquals(KategoriGagal.INTERNAL, e.kategori)
    }
}
