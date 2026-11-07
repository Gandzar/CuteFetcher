package dev.bagja.streamvortex.pengambil_media

/** Hasil validasi URL masukan pengguna. */
sealed interface HasilValidasi {
    data class Valid(val url: String, val host: String) : HasilValidasi

    data class TidakValid(val alasan: String) : HasilValidasi
}

private val SkemaDiizinkan = setOf("http", "https")

/**
 * Validasi ringan: hanya http/https, host berisi minimal satu titik, tanpa spasi.
 * Teks yang diawali "www." atau berupa domain polos tanpa skema diberi "https://".
 */
fun validasiUrl(masukan: String): HasilValidasi {
    val teks = masukan.trim()
    if (teks.isEmpty()) return HasilValidasi.TidakValid("Tautan kosong.")
    if (teks.any { it.isWhitespace() }) return HasilValidasi.TidakValid("Tautan tidak boleh mengandung spasi.")

    val punyaSkema = teks.contains("://")
    val lengkap = if (punyaSkema) teks else "https://$teks"
    val skema = lengkap.substringBefore("://").lowercase()
    if (skema !in SkemaDiizinkan) return HasilValidasi.TidakValid("Hanya tautan http/https yang didukung.")

    val sisa = lengkap.substringAfter("://")
    val hostBagian = sisa.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = hostBagian.substringAfterLast('@').substringBefore(':').lowercase()
    if (host.isEmpty() || !host.contains('.')) return HasilValidasi.TidakValid("Alamat situs tidak valid.")
    val hurufHostSah = host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    if (!hurufHostSah || host.startsWith('.') || host.endsWith('.')) {
        return HasilValidasi.TidakValid("Alamat situs tidak valid.")
    }
    return HasilValidasi.Valid(url = lengkap, host = host)
}

private fun String.cocok(domain: String): Boolean = this == domain || endsWith(".$domain")

/** Tebak platform dari host. Tidak menjamin bahwa ekstraksi akan berhasil. */
fun deteksiPlatform(host: String): Platform {
    val h = host.lowercase()
    return when {
        h.cocok("youtube.com") || h.cocok("youtu.be") || h.cocok("youtube-nocookie.com") -> Platform.YOUTUBE
        h.cocok("instagram.com") || h.cocok("instagr.am") -> Platform.INSTAGRAM
        h.cocok("tiktok.com") || h.cocok("tt.site") -> Platform.TIKTOK
        h.cocok("facebook.com") || h.cocok("fb.watch") || h.cocok("fb.com") -> Platform.FACEBOOK
        h.cocok("twitter.com") || h.cocok("x.com") || h.cocok("t.co") -> Platform.TWITTER
        h.cocok("spotify.com") || h.cocok("spotify.link") -> Platform.SPOTIFY
        h.cocok("soundcloud.com") || h.cocok("snd.sc") -> Platform.SOUNDCLOUD
        h.cocok("pinterest.com") || h.cocok("pin.it") -> Platform.PINTEREST
        h.cocok("music.apple.com") -> Platform.APPLE_MUSIC
        h.cocok("bilibili.com") || h.cocok("b23.tv") -> Platform.BILIBILI
        h.cocok("douyin.com") -> Platform.DOUYIN
        h.cocok("bandcamp.com") -> Platform.BANDCAMP
        h.cocok("threads.net") || h.cocok("threads.com") -> Platform.THREADS
        h.cocok("pixiv.net") -> Platform.PIXIV
        h.cocok("xiaohongshu.com") || h.cocok("xhslink.com") -> Platform.REDNOTE
        h.cocok("reddit.com") || h.cocok("redd.it") -> Platform.REDDIT
        else -> Platform.TIDAK_DIKENAL
    }
}
