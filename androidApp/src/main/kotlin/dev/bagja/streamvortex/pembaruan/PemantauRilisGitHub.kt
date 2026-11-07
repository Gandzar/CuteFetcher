package dev.bagja.streamvortex.pembaruan

import android.content.Context
import android.os.Build
import dev.bagja.streamvortex.pembaruan_aplikasi.PenguraiRilisGitHub
import io.ymusic.remoteconfig.AppUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Sumber pembaruan Cute Fetcher: rilis terbaru di GitHub (tanpa server perantara, tanpa token).
 * Hasilnya berupa [AppUpdate] yang dimakan updater YMusic apa adanya (unduh bertahap, cek ukuran dan
 * sha256, pasang lewat PackageInstaller; Android menolak APK yang tanda tangannya beda).
 *
 * Pemeriksaan otomatis dibatasi satu kali per [JEDA_OTOMATIS_MS] (API GitHub tanpa login dibatasi 60/jam/IP);
 * tombol "Periksa pembaruan" manual melewati batas itu.
 */
class PemantauRilisGitHub(
    context: Context,
    private val repo: String,
    private val klien: OkHttpClient,
) {
    private val _update = MutableStateFlow<AppUpdate?>(null)

    /** Rilis lebih baru yang sedang ditawarkan (null = belum ada/belum diperiksa). */
    val appUpdate: StateFlow<AppUpdate?> = _update.asStateFlow()

    private val prefs = context.applicationContext.getSharedPreferences("cute_pembaruan", Context.MODE_PRIVATE)

    /** Pemeriksaan otomatis saat aplikasi dibuka: dilewati bila baru saja diperiksa. */
    suspend fun periksaOtomatis() {
        val kini = System.currentTimeMillis()
        if (kini - prefs.getLong(KUNCI_TERAKHIR, 0L) < JEDA_OTOMATIS_MS) return
        periksa() // gagal jaringan cukup diam: pemeriksaan berikutnya mencoba lagi
    }

    /** Periksa sekarang. Failure = gagal memeriksa (jaringan/format); success(null) = tidak ada pembaruan. */
    suspend fun periksa(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        try {
            val hasil = ambil()
            prefs.edit().putLong(KUNCI_TERAKHIR, System.currentTimeMillis()).apply()
            _update.value = hasil
            Result.success(hasil)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ambil(): AppUpdate? {
        val json = unduhTeks("https://api.github.com/repos/$repo/releases/latest", "application/vnd.github+json")
        val calon = PenguraiRilisGitHub.urai(json, repo, Build.SUPPORTED_ABIS.toList()) ?: return null
        val sha = calon.sha256
            ?: calon.urlSums?.let { PenguraiRilisGitHub.sha256Dari(unduhTeks(it, "text/plain"), calon.namaApk) }
            ?: return null // tanpa hash tepercaya rilis tidak ditawarkan: tidak pernah memasang tanpa verifikasi
        return PenguraiRilisGitHub.keAppUpdate(calon, sha)
    }

    private fun unduhTeks(url: String, accept: String): String {
        require(url.startsWith("https://")) { "hanya https" }
        val req = Request.Builder().url(url).header("Accept", accept).header("User-Agent", "CuteFetcher-Updater").build()
        return klien.newCall(req).execute().use { resp ->
            // 404 pada /releases/latest = belum ada rilis: dianggap tanpa pembaruan (JSON kosong tidak punya tag).
            if (resp.code == 404) return@use "{}"
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.peekBody(MAKS_BYTE).string()
        }
    }

    private companion object {
        const val KUNCI_TERAKHIR = "terakhir_periksa"
        const val JEDA_OTOMATIS_MS = 6L * 60 * 60 * 1000
        const val MAKS_BYTE = 2L * 1024 * 1024
    }
}
