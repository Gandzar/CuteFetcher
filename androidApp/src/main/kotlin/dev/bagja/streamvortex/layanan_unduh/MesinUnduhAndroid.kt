package dev.bagja.streamvortex.layanan_unduh

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import dev.bagja.streamvortex.pengambil_media.AsalLog
import dev.bagja.streamvortex.pengambil_media.FormatMedia
import dev.bagja.streamvortex.pengambil_media.HasilMedia
import dev.bagja.streamvortex.pengambil_media.ItemUnduhan
import dev.bagja.streamvortex.pengambil_media.JenisMedia
import dev.bagja.streamvortex.pengambil_media.JurnalDebug
import dev.bagja.streamvortex.pengambil_media.KegagalanEkstraksi
import dev.bagja.streamvortex.pengambil_media.MesinUnduh
import dev.bagja.streamvortex.pengambil_media.StatusUnduhan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Mesin unduh HTTP umum untuk hasil scraper (kontrak [MesinUnduh]).
 *
 * - Maksimal 2 unduhan berjalan; sisanya antre (MENUNGGU).
 * - Jeda/lanjut memakai HTTP Range; jika server mengabaikan Range, unduhan mengulang dari awal.
 * - Data ditulis ke berkas sementara (cache), lalu diterbitkan ke MediaStore (Android 10+, tanpa izin
 *   penyimpanan) atau ke folder unduhan aplikasi (Android 9 ke bawah). Berkas yang ukurannya tidak
 *   cocok dengan Content-Length tidak pernah dianggap selesai.
 * - Tautan semu hasil scraper (`*_resolve:`) diselesaikan lewat [penyelesai] tepat sebelum mengunduh.
 */
class MesinUnduhAndroid(
    private val context: Context,
    /** Klien HTTP bersama. */
    private val http: OkHttpClient,
    private val penyelesai: suspend (FormatMedia) -> String,
) : MesinUnduh {
    private class Meta(val hasil: HasilMedia, val format: FormatMedia) {
        @Volatile var urlNyata: String? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAKS_BERSAMAAN)
    private val meta = ConcurrentHashMap<String, Meta>()
    private val tugas = ConcurrentHashMap<String, Job>()
    private val _antrean = MutableStateFlow<List<ItemUnduhan>>(emptyList())
    override val antrean: StateFlow<List<ItemUnduhan>> = _antrean.asStateFlow()

    private val folderSementara: File = File(context.cacheDir, "unduhan_cute").apply { mkdirs() }

    init {
        instance = this
    }

    override suspend fun tambah(hasil: HasilMedia, format: FormatMedia): String {
        val id = UUID.randomUUID().toString()
        val judul = hasil.judul.ifBlank { hasil.penulis ?: "Cute Fetcher" }
        meta[id] = Meta(hasil, format)
        _antrean.update {
            listOf(
                ItemUnduhan(
                    id = id, judul = judul, label = format.label, thumbnail = hasil.thumbnail,
                    byteSelesai = 0, byteTotal = format.ukuranByte, status = StatusUnduhan.MENUNGGU,
                ),
            ) + it
        }
        mulai(id)
        return id
    }

    override fun jeda(id: String) {
        val item = _antrean.value.firstOrNull { it.id == id } ?: return
        if (item.status != StatusUnduhan.BERJALAN || !item.bisaDijeda) return
        ubah(id) { it.copy(status = StatusUnduhan.DIJEDA) } // set status dulu, baru batalkan job
        tugas[id]?.cancel()
    }

    override fun lanjutkan(id: String) {
        val item = _antrean.value.firstOrNull { it.id == id } ?: return
        if (item.status != StatusUnduhan.DIJEDA && item.status != StatusUnduhan.GAGAL) return
        ubah(id) { it.copy(status = StatusUnduhan.MENUNGGU, pesanGagal = null) }
        mulai(id)
    }

    override fun batalkan(id: String) {
        val item = _antrean.value.firstOrNull { it.id == id } ?: return
        if (item.status == StatusUnduhan.SELESAI || item.status == StatusUnduhan.DIBATALKAN) return
        ubah(id) { it.copy(status = StatusUnduhan.DIBATALKAN) }
        tugas[id]?.cancel()
        berkasSementara(id).delete()
    }

    override fun bersihkan(id: String) {
        val item = _antrean.value.firstOrNull { it.id == id } ?: return
        if (item.status == StatusUnduhan.MENUNGGU || item.status == StatusUnduhan.BERJALAN) return
        tugas.remove(id)?.cancel()
        meta.remove(id)
        berkasSementara(id).delete()
        _antrean.update { daftar -> daftar.filterNot { it.id == id } }
    }

    private fun mulai(id: String) {
        mulaiLayanan()
        tugas[id] = scope.launch {
            semaphore.withPermit { kerjakan(id) }
        }
    }

    private fun mulaiLayanan() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ServisUnduhCute::class.java))
        } catch (e: Exception) {
            // Android 12+ melarang start FGS dari latar belakang. Unduhan tetap jalan selama proses hidup;
            // hanya perlindungan latar belakangnya yang hilang. Dicatat lewat status, bukan ditelan diam-diam.
            android.util.Log.w(TAG, "Layanan latar belakang tidak bisa dimulai: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun kerjakan(id: String) {
        val m = meta[id] ?: return
        // Status bisa sudah dibatalkan saat menunggu giliran.
        if (_antrean.value.firstOrNull { it.id == id }?.status != StatusUnduhan.MENUNGGU) return
        ubah(id) { it.copy(status = StatusUnduhan.BERJALAN, pesanGagal = null) }
        JurnalDebug.i(AsalLog.UNDUH, "[${id.take(6)}] mulai: ${m.format.label}, resolusi=${m.format.perluResolusi}, sumber=${m.hasil.mesin}")
        try {
            val url = m.urlNyata ?: (if (m.format.perluResolusi) penyelesai(m.format) else m.format.url).also { m.urlNyata = it }
            require(url.startsWith("http://") || url.startsWith("https://")) { "skema tidak diizinkan" }
            val job = currentCoroutineContext()[Job]!!
            // runInterruptible: pembatalan (jeda/batal) memutus pembacaan jaringan yang sedang memblokir.
            runInterruptible { unduhBlok(id, m, url, job) }
            withContext(Dispatchers.IO) { terbitkan(berkasSementara(id), m) }
            berkasSementara(id).delete()
            ubah(id) { it.copy(status = StatusUnduhan.SELESAI, byteSelesai = it.byteTotal ?: it.byteSelesai) }
            JurnalDebug.i(AsalLog.UNDUH, "[${id.take(6)}] SELESAI dan diterbitkan ke penyimpanan")
        } catch (e: CancellationException) {
            throw e // status (DIJEDA/DIBATALKAN) sudah ditetapkan oleh pemanggil sebelum membatalkan
        } catch (e: KegagalanEkstraksi) {
            gagal(id, e.pesan)
        } catch (e: IOException) {
            // Pemutusan koneksi akibat jeda/batal muncul sebagai IOException: itu bukan kegagalan.
            if (!currentCoroutineContext().isActive) throw CancellationException("dibatalkan", e)
            val habis = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", true) == true
            gagal(id, if (habis) "Penyimpanan penuh." else e.message?.takeIf { it.startsWith("Server") || it.startsWith("Berkas") } ?: "Gangguan jaringan saat mengunduh.")
        } catch (e: Exception) {
            gagal(id, "Terjadi kesalahan saat mengunduh.")
        }
    }

    private fun unduhBlok(id: String, m: Meta, url: String, job: Job) {
        val part = berkasSementara(id)
        var mulaiByte = if (part.exists()) part.length() else 0L
        val req = Request.Builder().url(url).header("User-Agent", UA)
            .apply { if (mulaiByte > 0) header("Range", "bytes=$mulaiByte-") }
            .build()
        http.newCall(req).execute().use { resp ->
            JurnalDebug.d(AsalLog.UNDUH, "[${id.take(6)}] respons HTTP ${resp.code}, tipe=${resp.header("Content-Type")}, panjang=${resp.body.contentLength()}, lanjut=${resp.code == 206}")
            if (!resp.isSuccessful) throw IOException("Server menolak permintaan (HTTP ${resp.code}).")
            if (resp.header("Content-Type").orEmpty().startsWith("text/", ignoreCase = true)) {
                throw IOException("Server mengirim halaman web, bukan berkas media.")
            }
            val lanjut = resp.code == 206
            if (mulaiByte > 0 && !lanjut) { // server mengabaikan Range: ulang dari nol
                part.delete()
                mulaiByte = 0
            }
            val panjang = resp.body.contentLength()
            val total = if (panjang >= 0) mulaiByte + panjang else m.format.ukuranByte
            val bisaDijeda = lanjut || resp.header("Accept-Ranges").orEmpty().contains("bytes", ignoreCase = true)
            ubah(id) { it.copy(byteTotal = total, byteSelesai = mulaiByte, bisaDijeda = bisaDijeda) }

            var terakhirLapor = 0L
            var tertulis = mulaiByte
            FileOutputStream(part, mulaiByte > 0).use { keluar ->
                val buf = ByteArray(64 * 1024)
                resp.body.byteStream().use { masuk ->
                    while (true) {
                        job.ensureActive()
                        val n = masuk.read(buf)
                        if (n < 0) break
                        keluar.write(buf, 0, n)
                        tertulis += n
                        val kini = System.nanoTime()
                        if (kini - terakhirLapor > 300_000_000L) {
                            terakhirLapor = kini
                            ubah(id) { it.copy(byteSelesai = tertulis) }
                        }
                    }
                }
                keluar.fd.sync()
            }
            if (total != null && panjang >= 0 && part.length() != total) {
                throw IOException("Berkas tidak lengkap (koneksi terputus).")
            }
            ubah(id) { it.copy(byteSelesai = tertulis) }
        }
    }

    /** Pindahkan berkas sementara ke penyimpanan publik dan kembalikan lokasinya. */
    private fun terbitkan(sumber: File, m: Meta): String {
        val ekstensi = m.format.ekstensi.takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
            ?: when (m.format.jenis) {
                JenisMedia.AUDIO -> "mp3"
                JenisMedia.GAMBAR -> "jpg"
                else -> "mp4"
            }
        val nama = bersihkanNama(m.hasil.judul.ifBlank { m.hasil.penulis ?: "Cute Fetcher" }) + "." + ekstensi
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ekstensi)
            ?: when (m.format.jenis) {
                JenisMedia.AUDIO -> "audio/mpeg"
                JenisMedia.GAMBAR -> "image/jpeg"
                else -> "video/mp4"
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (koleksi, folder) = when (m.format.jenis) {
                JenisMedia.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Music/CuteFetcher"
                JenisMedia.GAMBAR -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Pictures/CuteFetcher"
                else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Movies/CuteFetcher"
            }
            val nilai = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nama)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(koleksi, nilai) ?: throw IOException("Tidak bisa membuat berkas di penyimpanan.")
            try {
                resolver.openOutputStream(uri)?.use { keluar -> sumber.inputStream().use { it.copyTo(keluar) } }
                    ?: throw IOException("Tidak bisa menulis ke penyimpanan.")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null) // jangan tinggalkan berkas setengah jadi
                throw e
            }
            return uri.toString()
        }
        // Android 9 ke bawah: folder unduhan khusus aplikasi (tanpa izin tulis penyimpanan publik).
        val dasar = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val folder = File(dasar, "CuteFetcher").apply { mkdirs() }
        var tujuan = File(folder, nama)
        var n = 1
        while (tujuan.exists()) tujuan = File(folder, nama.substringBeforeLast('.') + "_" + n++ + "." + ekstensi)
        sumber.copyTo(tujuan)
        return tujuan.absolutePath
    }

    private fun bersihkanNama(s: String): String {
        val bersih = s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().trim('.').take(100)
        return bersih.ifBlank { "Cute Fetcher" }
    }

    private fun berkasSementara(id: String) = File(folderSementara, "$id.part")

    private fun gagal(id: String, pesan: String) {
        JurnalDebug.e(AsalLog.UNDUH, "[${id.take(6)}] GAGAL: $pesan")
        ubah(id) { it.copy(status = StatusUnduhan.GAGAL, pesanGagal = pesan) }
    }

    private fun ubah(id: String, f: (ItemUnduhan) -> ItemUnduhan) {
        _antrean.update { daftar -> daftar.map { if (it.id == id) f(it) else it } }
    }

    companion object {
        private const val MAKS_BERSAMAAN = 2
        private const val TAG = "MesinUnduh"
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"

        /** Satu instance per proses; dipakai [ServisUnduhCute] untuk membaca antrean. */
        @Volatile
        var instance: MesinUnduhAndroid? = null
            private set
    }
}
