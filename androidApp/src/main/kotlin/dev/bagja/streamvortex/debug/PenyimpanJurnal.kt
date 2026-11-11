package dev.bagja.streamvortex.debug

import android.content.Context
import android.util.Log
import dev.bagja.streamvortex.pengambil_media.AsalLog
import dev.bagja.streamvortex.pengambil_media.JurnalDebug
import java.io.File
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Menyimpan jurnal debug ke berkas supaya tetap ada setelah aplikasi tertutup atau crash.
 *
 * - Sesi berjalan  : files/debug/jurnal_aktif.txt
 * - Sesi sebelumnya: files/debug/jurnal_sebelumnya.txt (dipindahkan saat aplikasi mulai lagi)
 * - Crash yang tidak tertangani dicatat beserta stack trace, lalu diteruskan ke penangan bawaan.
 *
 * Penulisan dilakukan di satu thread latar belakang. Berkas dibatasi ±[BATAS_BYTE].
 */
object PenyimpanJurnal {
    private const val BATAS_BYTE = 1_000_000L
    private lateinit var berkasAktif: File
    private lateinit var berkasSebelumnya: File
    private val pelaksana = Executors.newSingleThreadExecutor { r -> Thread(r, "jurnal-tulis").apply { isDaemon = true } }

    fun mulai(context: Context) {
        val folder = File(context.filesDir, "debug").apply { mkdirs() }
        berkasAktif = File(folder, "jurnal_aktif.txt")
        berkasSebelumnya = File(folder, "jurnal_sebelumnya.txt")

        // Log sesi lalu dipindah dulu supaya tidak tertimpa oleh sesi baru.
        if (berkasAktif.exists() && berkasAktif.length() > 0) {
            berkasSebelumnya.delete()
            berkasAktif.renameTo(berkasSebelumnya)
        }

        JurnalDebug.jam = { System.currentTimeMillis() }
        JurnalDebug.offsetZonaMs = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
        JurnalDebug.penulis = { baris -> pelaksana.execute { tulis(baris) } }
        JurnalDebug.i(AsalLog.APLIKASI, "=== sesi baru dimulai ===")

        val penangananLama = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, galat ->
            try {
                // Tulis SINKRON: proses akan mati sesaat lagi, jadi tidak boleh lewat antrean thread.
                tulis("CRASH pada thread '${thread.name}': ${Log.getStackTraceString(galat)}")
            } catch (_: Throwable) {
                // Gagal menulis log crash tidak boleh menutupi crash aslinya.
            }
            penangananLama?.uncaughtException(thread, galat)
        }
    }

    @Synchronized
    private fun tulis(baris: String) {
        try {
            if (berkasAktif.length() > BATAS_BYTE) berkasAktif.writeText("(bagian awal log dipotong karena terlalu besar)\n")
            berkasAktif.appendText(baris + "\n")
        } catch (_: Exception) {
            // Penyimpanan penuh/terkunci: log di memori tetap jalan; berkas dilewati.
        }
    }

    /** Isi log sesi sebelumnya (maks. 200 KB terakhir), atau null bila tidak ada. */
    fun logSesiSebelumnya(): String? {
        if (!::berkasSebelumnya.isInitialized || !berkasSebelumnya.exists()) return null
        return try {
            val teks = berkasSebelumnya.readText()
            if (teks.length > 200_000) teks.takeLast(200_000) else teks
        } catch (_: Exception) {
            null
        }
    }
}
