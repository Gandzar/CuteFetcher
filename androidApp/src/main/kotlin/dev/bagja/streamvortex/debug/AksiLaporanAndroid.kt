package dev.bagja.streamvortex.debug

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dev.bagja.streamvortex.pengambil_media.AksiLaporan
import dev.bagja.streamvortex.pengolah_audio.PengolahAudio

/** Aksi laporan di Android: info perangkat, log sesi lalu, bagikan teks, dan kirim email ke pengembang. */
class AksiLaporanAndroid(
    private val context: Context,
    /** Ringkasan status mesin scraper (disuplai pemanggil agar kelas ini tidak bergantung pada mesinnya). */
    private val statusMesin: () -> String,
) : AksiLaporan {

    override fun infoPerangkat(): String {
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val versi = info?.let { "${it.versionName} (kode ${PackageInfoCompat.getLongVersionCode(it)})" } ?: "tidak diketahui"
        return buildString {
            appendLine("Aplikasi : Cute Fetcher $versi")
            appendLine("Android  : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI      : ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Mixer DSP: ${if (PengolahAudio.sudahTerpasang()) "terpasang di semua jalur" else "BELUM terpasang"}")
            append("Mesin    : ${statusMesin()}")
        }
    }

    override fun logSesiSebelumnya(): String? = PenyimpanJurnal.logSesiSebelumnya()

    override fun bagikanTeks(judul: String, isi: String) {
        val kirim = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, judul)
            putExtra(Intent.EXTRA_TEXT, isi.take(BATAS_TEKS))
        }
        context.startActivity(Intent.createChooser(kirim, judul).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun kirimEmail(alamat: String, subjek: String, isi: String) {
        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(alamat))
            putExtra(Intent.EXTRA_SUBJECT, subjek)
            putExtra(Intent.EXTRA_TEXT, isi.take(BATAS_TEKS))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(email)
        } catch (_: ActivityNotFoundException) {
            // Tidak ada aplikasi email: jatuh ke pemilih "Bagikan" supaya laporan tetap bisa dikirim.
            bagikanTeks(subjek, isi)
        }
    }

    private companion object {
        /** Batas aman agar Intent tidak melebihi batas transaksi Binder (~1 MB). */
        const val BATAS_TEKS = 300_000
    }
}
