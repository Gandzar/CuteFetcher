package dev.bagja.streamvortex.mesin

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Penyimpan cookie di memori untuk mesin scraper.
 *
 * Kenapa perlu: banyak situs downloader bekerja dua langkah (buka halaman -> ambil token dan cookie sesi
 * -> kirim form dengan cookie itu). Tanpa jar, OkHttp membuang cookie antar-permintaan sehingga langkah
 * kedua selalu ditolak. Aplikasi Nimiyo asli menyimpan cookie lewat plugin CapacitorCookies; ini padanannya.
 *
 * Cookie hilang saat proses aplikasi berhenti (disengaja: tidak ada data sesi yang tersimpan di disk).
 */
class JarKukiMemori : CookieJar {
    private val simpanan = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val sekarang = System.currentTimeMillis()
        for (baru in cookies) {
            // Ganti cookie lama yang sama (nama + domain + path), lalu simpan bila belum kedaluwarsa.
            simpanan.removeAll { it.name == baru.name && it.domain == baru.domain && it.path == baru.path }
            if (baru.expiresAt > sekarang) simpanan.add(baru)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val sekarang = System.currentTimeMillis()
        simpanan.removeAll { it.expiresAt <= sekarang }
        return simpanan.filter { it.matches(url) }
    }

    @Synchronized
    fun bersihkan() {
        simpanan.clear()
    }

    @get:Synchronized
    val jumlah: Int get() = simpanan.size
}
