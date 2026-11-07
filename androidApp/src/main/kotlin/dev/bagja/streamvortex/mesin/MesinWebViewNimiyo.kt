package dev.bagja.streamvortex.mesin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import dev.bagja.streamvortex.pengambil_media.FormatMedia
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
) : PengambilMedia {
    override val nama: String = "Nimiyo"

    // Platform yang benar-benar punya rantai server di host.js (REDDIT tidak ada di Nimiyo).
    private val didukung = setOf(
        Platform.YOUTUBE, Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK, Platform.TWITTER,
        Platform.SPOTIFY, Platform.SOUNDCLOUD, Platform.PINTEREST, Platform.APPLE_MUSIC, Platform.BILIBILI,
        Platform.DOUYIN, Platform.BANDCAMP, Platform.THREADS, Platform.PIXIV, Platform.REDNOTE,
    )

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
                    JSONObject().put("status", 0).put("headers", JSONObject()).put("data", "")
                        .put("error", e.message ?: "network").toString()
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
        return http.newCall(builder.build()).execute().use { resp ->
            val hdrs = JSONObject()
            for (name in resp.headers.names()) {
                val values = resp.headers.values(name)
                if (name.equals("set-cookie", true)) hdrs.put("Set-Cookie", JSONArray(values)) else hdrs.put(name, values.joinToString(", "))
            }
            // Batas 8 MB di memori agar halaman raksasa tidak menghabiskan RAM.
            val badan = resp.peekBody(BATAS_BADAN_BYTE).string()
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
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (request.url.host == HOST_ASET) return pemuat.shouldInterceptRequest(request.url)
                        // Semua selain aset lokal diblokir: jaringan hanya lewat jembatan OkHttp.
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != HOST_ASET
                }
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
        val r = panggil("scrape", url)
        if (!r.optBoolean("ok")) throw klasifikasi(r.optString("message"))
        val hasil = r.optJSONObject("result")
            ?: throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Balasan mesin tidak lengkap.")
        return PemetaHasilNimiyo.petakan(platform, nama, url, hasil.toString())
    }

    /** Ubah tautan semu ("*_resolve:...") menjadi URL unduhan nyata. Dipakai mesin unduh (Tahap 7). */
    suspend fun selesaikan(format: FormatMedia): String {
        val mentah = format.dataMentah
            ?: throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Data format tidak lengkap.")
        val r = panggil("resolve", mentah)
        if (!r.optBoolean("ok")) throw klasifikasi(r.optString("message"))
        return r.optString("url").takeIf { it.isNotBlank() }
            ?: throw KegagalanEkstraksi(KategoriGagal.TIDAK_ADA_MEDIA, "Server tidak memberi tautan unduhan.")
    }

    /** Pesan JS bisa berisi potongan HTML/teknis; pengguna hanya melihat kategori + kalimat baku. */
    private fun klasifikasi(pesanJs: String): KegagalanEkstraksi {
        val m = pesanJs.lowercase()
        val sebab = Exception(pesanJs.take(500))
        return when {
            "belum didukung" in m || "not supported" in m ->
                KegagalanEkstraksi(KategoriGagal.PLATFORM_TIDAK_DIDUKUNG, "Tautan ini belum didukung.", sebab)
            listOf("blocked", "rate", "private", "login", "protected", "429", "403", "age").any { it in m } ->
                KegagalanEkstraksi(KategoriGagal.DIBATASI, "Server membatasi atau konten ini privat.", sebab)
            listOf("network", "timeout", "timed out", "unable to resolve", "econn").any { it in m } ->
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
