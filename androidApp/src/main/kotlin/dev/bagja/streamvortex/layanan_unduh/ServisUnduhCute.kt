package dev.bagja.streamvortex.layanan_unduh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.bagja.streamvortex.pengambil_media.ItemUnduhan
import dev.bagja.streamvortex.pengambil_media.StatusUnduhan
import io.ymusic.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Layanan latar belakang untuk unduhan hasil scraper: menjaga proses tetap hidup dan menampilkan
 * progres. Berhenti sendiri saat tidak ada unduhan MENUNGGU/BERJALAN. Dibuat dengan tipe dataSync.
 */
class ServisUnduhCute : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pengamat: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        buatKanal()
        val mesin = MesinUnduhAndroid.instance
        // Wajib memanggil startForeground secepatnya setelah startForegroundService.
        tampilkan(mesin?.antrean?.value.orEmpty())
        if (mesin == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pengamat == null) pengamat = scope.launch { amati(mesin.antrean) }
        return START_NOT_STICKY
    }

    private suspend fun amati(aliran: StateFlow<List<ItemUnduhan>>) {
        aliran.collect { daftar ->
            if (daftar.none { it.status.aktif() }) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                tampilkan(daftar)
            }
        }
    }

    private fun StatusUnduhan.aktif() = this == StatusUnduhan.MENUNGGU || this == StatusUnduhan.BERJALAN

    private fun tampilkan(daftar: List<ItemUnduhan>) {
        val aktif = daftar.filter { it.status.aktif() }
        val notif = bangun(aktif)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID_NOTIF, notif)
        }
    }

    private fun bangun(aktif: List<ItemUnduhan>): Notification {
        val utama = aktif.firstOrNull { it.status == StatusUnduhan.BERJALAN } ?: aktif.firstOrNull()
        val progres = utama?.progres
        val b = NotificationCompat.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.unduh_cute_judul))
            .setContentText(
                if (aktif.size <= 1) utama?.judul.orEmpty() else getString(R.string.unduh_cute_banyak, aktif.size),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (progres != null) b.setProgress(100, (progres * 100).toInt(), false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun buatKanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL, getString(R.string.unduh_cute_kanal), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val KANAL = "cute_unduhan"
        const val ID_NOTIF = 7101
    }
}
