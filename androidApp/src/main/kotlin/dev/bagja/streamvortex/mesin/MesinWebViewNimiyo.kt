package dev.bagja.streamvortex.mesin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import dev.bagja.streamvortex.pengambil_media.AsalLog
import dev.bagja.streamvortex.pengambil_media.FormatMedia
import dev.bagja.streamvortex.pengambil_media.HasilUjiKoneksi
import dev.bagja.streamvortex.pengambil_media.HasilUjiServer
import dev.bagja.streamvortex.pengambil_media.JurnalDebug
import dev.bagja.streamvortex.pengambil_media.PenguiMesin
import dev.bagja.streamvortex.pengambil_media.HasilMedia
import dev.bagja.streamvortex.pengambil_media.KategoriGagal
import dev.bagja.streamvortex.pengambil_media.KegagalanEkstraksi
import dev.bagja.streamvortex.pengambil_media.PemetaHasilNimiyo
import dev.bagja.streamvortex.pengambil_media.PengambilMedia
import dev.bagja.streamvortex.pengambil_media.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mesin ekstraksi: scraper Nimiyo asli (JavaScript, assets/scraper) dijalankan di WebView tak terlihat.
 * Semua permintaan HTTP dari JS diteruskan ke OkHttp lewat jembatan `NimiyoShareBridge`
 * (kontrak yang sama dengan Nimiyo), jadi tidak ada masalah CORS dan WebView sendiri tidak
 * boleh mengakses jaringan (semua host selain aset lokal diblokir).
 *
 * Dipilih WebView (bukan QuickJS) karena 10 dari 16 scraper memakai DOMParser.
 * Catatan: banyak server di dalamnya adalah layanan pihak ketiga yang bisa berubah/mati sewaktu-waktu.
 */
@SuppressLint("SetJavaScriptEnabled")
class MesinWebViewNimiyo(
    private val context: Context,
    /** Klien HTTP bersama (satu pool koneksi untuk seluruh fitur Cute Fetcher). */
    private val http: OkHttpClient,
) : PengambilMedia, PenguiMesin {
    override val nama: String = "Nimiyo"

    // Platform yang punya rantai server di assets/scraper/host.js. REDDIT ikut (server rapidsave dari Scrapr).
    private val didukung = setOf(
        Platform.YOUTUBE, Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK, Platform.TWITTER,
        Platform.SPOTIFY, Platform.SOUNDCLOUD, Platform.PINTEREST, Platform.APPLE_MUSIC, Platform.BILIBILI,
        Platform.DOUYIN, Platform.BANDCAMP, Platform.THREADS, Platform.PIXIV, Platform.REDNOTE, Platform.REDDIT,
    )

    /** Penyimpan cookie sesi untuk situs downloader (lihat [JarKukiMemori]). */
    private val jarKuki = JarKukiMemori()

    /** Klien dengan jar cookie; dipakai bila JS tidak mengatur header Cookie sendiri. */
    private val klienDenganKuki: OkHttpClient = http.newBuilder().cookieJar(jarKuki).build()

    /** Klien TANPA jar; dipakai bila scraper menyusun header Cookie sendiri (agar tidak ditimpa jar). */
    private val klienTanpaKuki: OkHttpClient = http.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    override fun mendukung(platform: Platform): Boolean = platform in didukung

    private val utama = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webView: WebView? = null
    private var siap = CompletableDeferred<Unit>()
    private val menunggu = ConcurrentHashMap<String, CompletableDeferred<String>>()

    // Hemat memori/baterai: WebView dibuang bila tak ada pekerjaan selama [JEDA_TUTUP_MS], dibuat lagi saat dibutuhkan.
    private val sibuk = AtomicInteger(0)
    private val tutup = Runnable {
        if (sibuk.get() == 0) {
            webView?.destroy()
            webView = null
        }
    }

    private fun jadwalkanTutup() {
        utama.removeCallbacks(tutup)
        utama.postDelayed(tutup, JEDA_TUTUP_MS)
    }

    /** Dipanggil dari JS: jalankan HTTP di thread IO lalu balas lewat callback JS. */
    inner class JembatanHttp {
        @JavascriptInterface
        fun httpRequestAsync(optsJson: String, reqId: String) {
            io.launch {
                val hasil = try {
                    doHttp(JSONObject(optsJson))
                } catch (e: Exception) {
                    JurnalDebug.e(AsalLog.HTTP, "✗ ${e.javaClass.simpleName}: ${e.message}")
                    JSONObject().put("status", 0).put("headers", JSONObject()).put("data", "")
                        .put("error", "${e.javaClass.simpleName}: ${e.message ?: "jaringan"}").toString()
                }
                val js = "(function(){var c=(window.__nimiyoShareCallbacks||{})[${JSONObject.quote(reqId)}];" +
                    "if(c)c(${JSONObject.quote(hasil)});})()"
                utama.post { webView?.evaluateJavascript(js, null) }
            }
        }
    }

    inner class KaitanNative {
        @JavascriptInterface fun onReady() {
            siap.complete(Unit)
        }

        @JavascriptInterface fun onResult(reqId: String, json: String) {
            menunggu.remove(reqId)?.complete(json)
        }

        /** Log dari host.js: tingkat "D"/"I"/"W"/"E". */
        @JavascriptInterface fun log(tingkat: String, pesan: String) {
            when (tingkat) {
                "E" -> JurnalDebug.e(AsalLog.SCRAPER, pesan)
                "W" -> JurnalDebug.w(AsalLog.SCRAPER, pesan)
                "D" -> JurnalDebug.d(AsalLog.SCRAPER, pesan)
                else -> JurnalDebug.i(AsalLog.SCRAPER, pesan)
            }
        }
    }

    private fun doHttp(o: JSONObject): String {
        val url = o.getString("url")
        require(url.startsWith("http://") || url.startsWith("https://")) { "skema tidak diizinkan" }
        val method = o.optString("method", "GET").uppercase()
        val headers = o.optJSONObject("headers") ?: JSONObject()
        val builder = Request.Builder().url(url)
        var contentType: String? = null
        headers.keys().forEach { k ->
            val v = headers.optString(k)
            when (k.lowercase()) {
                "content-type" -> contentType = v
                "content-length", "host", "accept-encoding" -> Unit // diurus OkHttp
                else -> builder.header(k, v)
            }
        }
        val data = if (o.has("data") && !o.isNull("data")) o.get("data").toString() else null
        if (method == "GET" || method == "HEAD") {
            builder.method(method, null)
        } else {
            val type = contentType
                ?: if (data != null && (data.startsWith("{") || data.startsWith("["))) "application/json" else "application/x-www-form-urlencoded"
            builder.method(method, (data ?: "").toRequestBody(type.toMediaTypeOrNull()))
        }
        val adaKukiManual = headers.keys().asSequence().any { it.equals("cookie", ignoreCase = true) }
        var klien = if (adaKukiManual) klienTanpaKuki else klienDenganKuki
        val tanpaRedirect = o.optBoolean("noRedirect", false)
        if (tanpaRedirect) klien = klien.newBuilder().followRedirects(false).followSslRedirects(false).build()

        val label = "$method ${JurnalDebug.sensorUrl(url)}"
        JurnalDebug.d(AsalLog.HTTP, "→ $label")
        val mulai = System.nanoTime()
        return klien.newCall(builder.build()).execute().use { resp ->
            val hdrs = JSONObject()
            for (name in resp.headers.names()) {
                val values = resp.headers.values(name)
                if (name.equals("set-cookie", true)) hdrs.put("Set-Cookie", JSONArray(values)) else hdrs.put(name, values.joinToString(", "))
            }
            // Batas 8 MB di memori agar halaman raksasa tidak menghabiskan RAM.
            val badan = resp.peekBody(BATAS_BADAN_BYTE).string()
            val ms = (System.nanoTime() - mulai) / 1_000_000
            val ringkas = "← ${resp.code} ${badan.length}B ${ms}ms $label"
            if (resp.isSuccessful || resp.code in 300..399) {
                JurnalDebug.d(AsalLog.HTTP, ringkas)
            } else {
                // Respons gagal: sertakan cuplikan isi (mis. halaman Cloudflare/blokir) untuk analisis.
                JurnalDebug.w(AsalLog.HTTP, "$ringkas | ${JurnalDebug.cuplikan(badan)}")
            }
            JSONObject().put("status", resp.code).put("headers", hdrs).put("data", badan).toString()
        }
    }

    /** Buat WebView di thread utama bila belum ada, lalu tunggu host.js siap. Mengembalikan false bila gagal siap. */
    private suspend fun pastikanSiap(): Boolean {
        withContext(Dispatchers.Main) {
            utama.removeCallbacks(tutup)
            if (webView == null) {
                siap = CompletableDeferred()
                val pemuat = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.allowFileAccess = false
                wv.settings.allowContentAccess = false
                wv.settings.setSupportMultipleWindows(false)
                wv.addJavascriptInterface(JembatanHttp(), "NimiyoShareBridge")
                wv.addJavascriptInterface(KaitanNative(), "CuteNative")
                // Tangkap console.log/error dari JS ke jurnal debug.
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(pesan: ConsoleMessage): Boolean {
                        val teks = "${pesan.message()} (${pesan.sourceId().substringAfterLast('/')}:${pesan.lineNumber()})"
                        when (pesan.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> JurnalDebug.e(AsalLog.JS, teks)
                            ConsoleMessage.MessageLevel.WARNING -> JurnalDebug.w(AsalLog.JS, teks)
                            else -> JurnalDebug.d(AsalLog.JS, teks)
                        }
                        return true
                    }
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (request.url.host == HOST_ASET) return pemuat.shouldInterceptRequest(request.url)
                        // Semua selain aset lokal diblokir: jaringan hanya lewat jembatan OkHttp.
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != HOST_ASET
                }
                JurnalDebug.i(AsalLog.SCRAPER, "WebView mesin dibuat, memuat host.html")
                wv.loadUrl("https://$HOST_ASET/assets/scraper/host.html")
                webView = wv
            }
        }
        return withTimeoutOrNull(20_000) { siap.await() } != null
    }

    private suspend fun panggil(fungsi: String, argumen: String): JSONObject {
        sibuk.incrementAndGet()
        try {
            return panggilTanpaHitung(fungsi, argumen)
        } finally {
            sibuk.decrementAndGet()
            jadwalkanTutup()
        }
    }

    private suspend fun panggilTanpaHitung(fungsi: String, argumen: String): JSONObject {
        if (!pastikanSiap()) throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Mesin ekstraksi gagal dimulai.")
        val reqId = UUID.randomUUID().toString()
        val d = CompletableDeferred<String>()
        menunggu[reqId] = d
        try {
            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript(
                    "window.CuteHost.$fungsi(${JSONObject.quote(reqId)}, ${JSONObject.quote(argumen)})",
                    null,
                )
            }
            val mentah = withTimeoutOrNull(120_000) { d.await() }
                ?: throw KegagalanEkstraksi(KategoriGagal.JARINGAN, "Waktu habis menunggu server.")
            return try {
                JSONObject(mentah)
            } catch (e: org.json.JSONException) {
                throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Balasan mesin tidak terbaca.", e)
            }
        } finally {
            menunggu.remove(reqId)
        }
    }

    override suspend fun ekstrak(url: String, platform: Platform): HasilMedia {
        JurnalDebug.i(AsalLog.SCRAPER, "ekstrak ${platform.namaTampil}: ${JurnalDebug.sensorUrl(url)}")
        val r = panggil("scrape", url)
        if (!r.optBoolean("ok")) {
            val pesanJs = r.optString("pesan")
            JurnalDebug.e(AsalLog.SCRAPER, "ekstrak gagal: ${pesanJs.replace('\n', ' ').take(600)}")
            throw klasifikasi(pesanJs)
        }
        val hasil = r.optJSONObject("hasil")
            ?: throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Balasan mesin tidak lengkap.")
        val server = r.optString("server")
        return PemetaHasilNimiyo.petakan(platform, if (server.isNotEmpty()) "$nama/$server" else nama, url, hasil.toString())
    }

    /** Ubah tautan semu ("*_resolve:...") menjadi URL unduhan nyata. Dipakai mesin unduh. */
    suspend fun selesaikan(format: FormatMedia): String {
        val mentah = format.dataMentah
            ?: throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Data format tidak lengkap.")
        val r = panggil("resolve", mentah)
        if (!r.optBoolean("ok")) throw klasifikasi(r.optString("pesan"))
        return r.optString("url").takeIf { it.isNotBlank() }
            ?: throw KegagalanEkstraksi(KategoriGagal.TIDAK_ADA_MEDIA, "Server tidak memberi tautan unduhan.")
    }

    // ----- alat diagnosa untuk panel debug ([PenguiMesin]) -----------------------------------------

    override suspend fun ujiKoneksi(): List<HasilUjiKoneksi> = withContext(Dispatchers.IO) {
        val sasaran = listOf(
            "Internet (Google)" to "https://www.google.com/generate_204",
            "GitHub API" to "https://api.github.com/zen",
            "SaveTWT" to "https://savetwt.com/",
            "RapidSave" to "https://rapidsave.com/",
            "SpotiMate" to "https://spotimate.app/en1",
            "SnapSave" to "https://snapsave.app/",
        )
        sasaran.map { (nama, alamat) ->
            val mulai = System.nanoTime()
            try {
                val req = Request.Builder().url(alamat).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/124 Mobile").build()
                klienTanpaKuki.newCall(req).execute().use { resp ->
                    val ms = (System.nanoTime() - mulai) / 1_000_000
                    val ok = resp.code in 200..399
                    val ket = if (ok) "HTTP ${resp.code}" else "HTTP ${resp.code} (diblokir/ditolak situs?)"
                    JurnalDebug.i(AsalLog.HTTP, "uji $nama: $ket ${ms}ms")
                    HasilUjiKoneksi(nama, ok, ms, ket)
                }
            } catch (e: Exception) {
                val ms = (System.nanoTime() - mulai) / 1_000_000
                JurnalDebug.e(AsalLog.HTTP, "uji $nama gagal: ${e.javaClass.simpleName}: ${e.message}")
                HasilUjiKoneksi(nama, false, ms, "${e.javaClass.simpleName}: ${e.message ?: ""}")
            }
        }
    }

    override suspend fun ujiSemuaServer(url: String): List<HasilUjiServer> {
        val r = panggil("diagnose", url)
        if (!r.optBoolean("ok")) throw KegagalanEkstraksi(KategoriGagal.PLATFORM_TIDAK_DIDUKUNG, r.optString("pesan").ifBlank { "Tautan belum didukung." })
        val daftar = r.optJSONArray("hasilServer") ?: JSONArray()
        return (0 until daftar.length()).map { i ->
            val o = daftar.getJSONObject(i)
            HasilUjiServer(o.optString("server"), o.optBoolean("sukses"), o.optLong("ms"), o.optString("pesan"))
        }
    }

    override fun statusMesin(): String =
        "WebView: ${if (webView != null) "hidup" else "belum dibuat/dibuang (hemat memori)"}; " +
            "permintaan aktif: ${sibuk.get()}; cookie tersimpan: ${jarKuki.jumlah}; platform didukung: ${didukung.size}"

    /** Pesan JS bisa berisi potongan HTML/teknis; pengguna hanya melihat kategori + kalimat baku. */
    private fun klasifikasi(pesanJs: String): KegagalanEkstraksi {
        val m = pesanJs.lowercase()
        val sebab = Exception(pesanJs.take(500))
        return when {
            "belum didukung" in m || "not supported" in m ->
                KegagalanEkstraksi(KategoriGagal.PLATFORM_TIDAK_DIDUKUNG, "Tautan ini belum didukung.", sebab)
            listOf("blocked", "rate limit", "rate-limit", "too many", "private", "login", "protected", "429", "403", "age-restricted", "membatasi").any { it in m } ->
                KegagalanEkstraksi(KategoriGagal.DIBATASI, "Server membatasi atau konten ini privat.", sebab)
            listOf("network", "timeout", "timed out", "unable to resolve", "econn", "jaringan gagal", "waktu habis", "unknownhost", "sslhandshake").any { it in m } ->
                KegagalanEkstraksi(KategoriGagal.JARINGAN, "Gangguan jaringan saat menghubungi server.", sebab)
            else -> KegagalanEkstraksi(KategoriGagal.TIDAK_ADA_MEDIA, "Tidak ada media yang berhasil diambil.", sebab)
        }
    }

    private companion object {
        const val HOST_ASET = "appassets.androidplatform.net"
        const val BATAS_BADAN_BYTE = 8L * 1024 * 1024
        const val JEDA_TUTUP_MS = 2L * 60 * 1000
    }
}
