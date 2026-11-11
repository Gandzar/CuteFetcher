package dev.bagja.streamvortex.pengambil_media

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JurnalDebugTest {
    @BeforeTest fun siap() {
        JurnalDebug.bersihkan()
        JurnalDebug.jam = { 3_723_042L } // 01:02:03.042 UTC
        JurnalDebug.offsetZonaMs = 0
        JurnalDebug.penulis = null
        JurnalDebug.aturRinci(true)
    }

    @AfterTest fun bersih() {
        JurnalDebug.bersihkan()
    }

    @Test fun formatBarisLog() {
        JurnalDebug.i(AsalLog.SCRAPER, "halo")
        assertEquals("01:02:03.042 I SCRAPER halo", JurnalDebug.teks())
    }

    @Test fun batasEntri_membuangYangTertua() {
        repeat(JurnalDebug.MAKS_ENTRI + 25) { JurnalDebug.d(AsalLog.APLIKASI, "n$it") }
        val e = JurnalDebug.entri.value
        assertEquals(JurnalDebug.MAKS_ENTRI, e.size)
        assertEquals("n25", e.first().pesan)
        assertEquals("n${JurnalDebug.MAKS_ENTRI + 24}", e.last().pesan)
    }

    @Test fun pesanPanjangDipotong() {
        JurnalDebug.e(AsalLog.HTTP, "x".repeat(JurnalDebug.MAKS_PANJANG_PESAN + 100))
        assertTrue(JurnalDebug.entri.value.single().pesan.endsWith("…(dipotong)"))
    }

    @Test fun filterTingkatDanAsal() {
        JurnalDebug.d(AsalLog.HTTP, "a")
        JurnalDebug.e(AsalLog.UNDUH, "b")
        JurnalDebug.w(AsalLog.UNDUH, "c")
        assertEquals(2, JurnalDebug.teks(minimal = TingkatLog.PERINGATAN).lines().size)
        assertEquals("01:02:03.042 E UNDUH b", JurnalDebug.teks(minimal = TingkatLog.GALAT, asal = setOf(AsalLog.UNDUH)))
    }

    @Test fun sensorUrl_membuangQueryDanFragmen() {
        assertEquals("https://a.com/x/y?…", JurnalDebug.sensorUrl("https://a.com/x/y?token=RAHASIA&a=1#f"))
        assertEquals("https://a.com/x", JurnalDebug.sensorUrl("https://a.com/x"))
    }

    @Test fun cuplikan_memadatkanDanMengikutiModeRinci() {
        assertEquals("a b c", JurnalDebug.cuplikan("a\n  b\t c"))
        JurnalDebug.aturRinci(false)
        assertEquals("", JurnalDebug.cuplikan("apa pun"))
    }

    @Test fun penulisMenerimaBarisTerformat() {
        val tertulis = mutableListOf<String>()
        JurnalDebug.penulis = { tertulis += it }
        JurnalDebug.w(AsalLog.MIXER, "uji")
        assertEquals(listOf("01:02:03.042 W MIXER uji"), tertulis)
    }
}
