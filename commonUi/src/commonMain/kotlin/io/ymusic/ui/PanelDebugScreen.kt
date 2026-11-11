package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bagja.streamvortex.pengambil_media.AksiLaporan
import dev.bagja.streamvortex.pengambil_media.AsalLog
import dev.bagja.streamvortex.pengambil_media.EntriLog
import dev.bagja.streamvortex.pengambil_media.HasilUjiKoneksi
import dev.bagja.streamvortex.pengambil_media.HasilUjiServer
import dev.bagja.streamvortex.pengambil_media.JurnalDebug
import dev.bagja.streamvortex.pengambil_media.PenguiMesin
import dev.bagja.streamvortex.pengambil_media.TingkatLog
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.debug_bagikan
import io.ymusic.ui.resources.debug_disalin
import io.ymusic.ui.resources.debug_dipotong
import io.ymusic.ui.resources.debug_galat
import io.ymusic.ui.resources.debug_hapus
import io.ymusic.ui.resources.debug_hasil_uji
import io.ymusic.ui.resources.debug_info
import io.ymusic.ui.resources.debug_judul
import io.ymusic.ui.resources.debug_kosong
import io.ymusic.ui.resources.debug_lapor
import io.ymusic.ui.resources.debug_lapor_catatan
import io.ymusic.ui.resources.debug_lapor_subjek
import io.ymusic.ui.resources.debug_menguji
import io.ymusic.ui.resources.debug_peringatan
import io.ymusic.ui.resources.debug_rinci
import io.ymusic.ui.resources.debug_salin
import io.ymusic.ui.resources.debug_semua
import io.ymusic.ui.resources.debug_sesi_lalu
import io.ymusic.ui.resources.debug_sembunyikan
import io.ymusic.ui.resources.debug_tampilkan
import io.ymusic.ui.resources.debug_tutup
import io.ymusic.ui.resources.debug_uji_koneksi
import io.ymusic.ui.resources.debug_uji_server
import io.ymusic.ui.resources.debug_url_uji
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val EMAIL_PENGEMBANG = "ganjarboy033@gmail.com"

/** Jumlah baris log yang digambar di layar (daftar penuh tetap ikut saat disalin/dikirim). */
private const val BARIS_DITAMPILKAN = 400

private val WarnaGalat = Color(0xFFE5484D)
private val WarnaPeringatan = Color(0xFFE5A100)

/** Filter tingkat untuk daftar log. */
private enum class FilterTingkat(val minimal: TingkatLog) {
    SEMUA(TingkatLog.DEBUG),
    PERINGATAN(TingkatLog.PERINGATAN),
    GALAT(TingkatLog.GALAT),
}

/**
 * Panel debug: melihat log aplikasi, menguji koneksi dan semua server scraper, lalu menyalin, membagikan,
 * atau mengirim laporan ke pengembang. Dibuka dari tab Pengaturan.
 *
 * [penguiMesin] dan [aksiLaporan] null = tidak tersedia di platform ini (tombolnya otomatis disembunyikan).
 */
@Composable
fun PanelDebugScreen(
    penguiMesin: PenguiMesin?,
    aksiLaporan: AksiLaporan?,
    onTutup: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onTutup)

    val lingkup = rememberCoroutineScope()
    val papanKlip = LocalClipboard.current
    val semuaEntri by JurnalDebug.entri.collectAsState()
    val rinci by JurnalDebug.rinci.collectAsState()

    var filterTingkat by remember { mutableStateOf(FilterTingkat.SEMUA) }
    var asalTerpilih by remember { mutableStateOf<Set<AsalLog>>(emptySet()) } // kosong = semua asal
    var urlUji by remember { mutableStateOf("") }
    var sedangMenguji by remember { mutableStateOf(false) }
    var hasilKoneksi by remember { mutableStateOf<List<HasilUjiKoneksi>?>(null) }
    var hasilServer by remember { mutableStateOf<List<HasilUjiServer>?>(null) }
    var tampilSesiLalu by remember { mutableStateOf(false) }
    var pesanSalin by remember { mutableStateOf(false) }

    val info = remember(aksiLaporan) { aksiLaporan?.infoPerangkat().orEmpty() }
    val sesiLalu = remember(aksiLaporan) { aksiLaporan?.logSesiSebelumnya() }

    val tersaring = remember(semuaEntri, filterTingkat, asalTerpilih) {
        semuaEntri.filter {
            it.tingkat.ordinal >= filterTingkat.minimal.ordinal && (asalTerpilih.isEmpty() || it.asal in asalTerpilih)
        }
    }
    val ditampilkan = remember(tersaring) { tersaring.takeLast(BARIS_DITAMPILKAN).asReversed() } // terbaru di atas

    /** Teks laporan lengkap: info perangkat + log sesi ini + (bila ada) log sesi sebelumnya. */
    fun susunLaporan(catatan: String): String = buildString {
        appendLine("=== LAPORAN CUTE FETCHER ===")
        if (catatan.isNotBlank()) appendLine(catatan).appendLine()
        appendLine(info)
        appendLine()
        appendLine("--- Log sesi ini (${semuaEntri.size} baris) ---")
        appendLine(JurnalDebug.teks())
        if (!sesiLalu.isNullOrBlank()) {
            appendLine()
            appendLine("--- Log sesi sebelumnya ---")
            appendLine(sesiLalu.takeLast(60_000))
        }
    }

    val judulSubjek = stringResource(Res.string.debug_lapor_subjek)
    val teksCatatan = stringResource(Res.string.debug_lapor_catatan)

    Column(Modifier.fillMaxSize().background(DesignColors.Surface)) {
        // ---------- bilah atas ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.BugReport, contentDescription = null, tint = DesignColors.Accent, modifier = Modifier.padding(start = 8.dp))
            Text(
                stringResource(Res.string.debug_judul),
                color = DesignColors.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            IconButton(onClick = onTutup) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.debug_tutup), tint = DesignColors.InkStrong)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = DesignSpacing.MarginCompact, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------- tombol aksi ----------
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TombolAksi(Icons.Rounded.ContentCopy, stringResource(Res.string.debug_salin)) {
                            lingkup.launch { papanKlip.setClipEntry(plainTextClipEntry(susunLaporan(""))) }
                            pesanSalin = true
                        }
                    }
                    if (aksiLaporan != null) {
                        item {
                            TombolAksi(Icons.Rounded.Share, stringResource(Res.string.debug_bagikan)) {
                                aksiLaporan.bagikanTeks(judulSubjek, susunLaporan(""))
                            }
                        }
                        item {
                            TombolAksi(Icons.Rounded.Email, stringResource(Res.string.debug_lapor), menonjol = true) {
                                // Log lengkap juga disalin ke papan klip sebagai cadangan bila aplikasi email memotong isinya.
                                lingkup.launch { papanKlip.setClipEntry(plainTextClipEntry(susunLaporan(""))) }
                                aksiLaporan.kirimEmail(EMAIL_PENGEMBANG, judulSubjek, susunLaporan(teksCatatan))
                            }
                        }
                    }
                    item {
                        TombolAksi(Icons.Rounded.Delete, stringResource(Res.string.debug_hapus)) {
                            JurnalDebug.bersihkan()
                            pesanSalin = false
                        }
                    }
                }
                if (pesanSalin) {
                    Text(stringResource(Res.string.debug_disalin), color = DesignColors.Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // ---------- diagnosa ----------
            if (penguiMesin != null) {
                item {
                    Kartu {
                        Text(stringResource(Res.string.debug_hasil_uji), color = DesignColors.InkStrong, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TombolAksi(null, stringResource(Res.string.debug_uji_koneksi), aktif = !sedangMenguji) {
                                sedangMenguji = true
                                lingkup.launch {
                                    try {
                                        hasilKoneksi = penguiMesin.ujiKoneksi()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        JurnalDebug.e(AsalLog.APLIKASI, "uji koneksi gagal: ${e.message}")
                                    } finally {
                                        sedangMenguji = false
                                    }
                                }
                            }
                        }
                        KolomTeks(urlUji, { urlUji = it }, stringResource(Res.string.debug_url_uji))
                        TombolAksi(null, stringResource(Res.string.debug_uji_server), aktif = !sedangMenguji && urlUji.isNotBlank()) {
                            sedangMenguji = true
                            hasilServer = null
                            lingkup.launch {
                                try {
                                    hasilServer = penguiMesin.ujiSemuaServer(urlUji.trim())
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    JurnalDebug.e(AsalLog.APLIKASI, "uji server gagal: ${e.message}")
                                } finally {
                                    sedangMenguji = false
                                }
                            }
                        }
                        if (sedangMenguji) Text(stringResource(Res.string.debug_menguji), color = DesignColors.InkMuted, fontSize = 12.sp)
                        hasilKoneksi?.forEach { BarisHasil(it.nama, it.sukses, "${it.durasiMs} ms · ${it.pesan}") }
                        hasilServer?.forEach { BarisHasil(it.server, it.sukses, "${it.durasiMs} ms · ${it.pesan}") }
                    }
                }
            }

            // ---------- info perangkat ----------
            if (info.isNotBlank()) {
                item {
                    Kartu {
                        Text(stringResource(Res.string.debug_info), color = DesignColors.InkStrong, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(info, color = DesignColors.InkSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                    }
                }
            }

            // ---------- log sesi sebelumnya (mis. sebelum crash) ----------
            if (!sesiLalu.isNullOrBlank()) {
                item {
                    Kartu {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(Res.string.debug_sesi_lalu),
                                color = DesignColors.InkStrong,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(if (tampilSesiLalu) Res.string.debug_sembunyikan else Res.string.debug_tampilkan),
                                color = DesignColors.Accent,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { tampilSesiLalu = !tampilSesiLalu }.padding(8.dp),
                            )
                        }
                        if (tampilSesiLalu) {
                            Text(sesiLalu.takeLast(8_000), color = DesignColors.InkSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                        }
                    }
                }
            }

            // ---------- filter ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { PilFilter(stringResource(Res.string.debug_semua), filterTingkat == FilterTingkat.SEMUA) { filterTingkat = FilterTingkat.SEMUA } }
                        item { PilFilter(stringResource(Res.string.debug_peringatan), filterTingkat == FilterTingkat.PERINGATAN) { filterTingkat = FilterTingkat.PERINGATAN } }
                        item { PilFilter(stringResource(Res.string.debug_galat), filterTingkat == FilterTingkat.GALAT) { filterTingkat = FilterTingkat.GALAT } }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AsalLog.entries, key = { it.name }) { a ->
                            PilFilter(a.name, a in asalTerpilih) {
                                asalTerpilih = if (a in asalTerpilih) asalTerpilih - a else asalTerpilih + a
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.debug_rinci), color = DesignColors.InkSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = rinci, onCheckedChange = { JurnalDebug.aturRinci(it) })
                    }
                    if (tersaring.size > BARIS_DITAMPILKAN) {
                        Text(stringResource(Res.string.debug_dipotong, BARIS_DITAMPILKAN), color = DesignColors.InkFaint, fontSize = 11.sp)
                    }
                }
            }

            // ---------- daftar log ----------
            if (ditampilkan.isEmpty()) {
                item { Text(stringResource(Res.string.debug_kosong), color = DesignColors.InkMuted, fontSize = 13.sp) }
            } else {
                items(ditampilkan, key = { it.id }) { BarisLog(it) }
            }
        }
    }
}

@Composable
private fun BarisLog(e: EntriLog) {
    val warna = when (e.tingkat) {
        TingkatLog.GALAT -> WarnaGalat
        TingkatLog.PERINGATAN -> WarnaPeringatan
        TingkatLog.INFO -> DesignColors.InkStrong
        TingkatLog.DEBUG -> DesignColors.InkMuted
    }
    Text(
        JurnalDebug.format(e),
        color = warna,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun Kartu(isi: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DesignColors.SettingsCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { isi() }
}

@Composable
private fun TombolAksi(
    ikon: androidx.compose.ui.graphics.vector.ImageVector?,
    teks: String,
    menonjol: Boolean = false,
    aktif: Boolean = true,
    onClick: () -> Unit,
) {
    val bentuk = RoundedCornerShape(50)
    val latar = if (menonjol) DesignColors.Accent else Color.Transparent
    val isi = if (menonjol) DesignColors.OnAccent else if (aktif) DesignColors.InkStrong else DesignColors.InkFaint
    Row(
        Modifier
            .clip(bentuk)
            .background(latar)
            .border(1.dp, if (menonjol) DesignColors.Accent else DesignColors.Outline, bentuk)
            .clickable(enabled = aktif, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ikon != null) Icon(ikon, contentDescription = null, tint = isi, modifier = Modifier.padding(end = 2.dp))
        Text(teks, color = isi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PilFilter(teks: String, dipilih: Boolean, onClick: () -> Unit) {
    val bentuk = RoundedCornerShape(50)
    Text(
        teks,
        color = DesignColors.InkStrong,
        fontSize = 12.sp,
        fontWeight = if (dipilih) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(bentuk)
            .background(if (dipilih) DesignColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (dipilih) DesignColors.Accent else DesignColors.Outline, bentuk)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun BarisHasil(nama: String, sukses: Boolean, keterangan: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 5.dp).clip(RoundedCornerShape(50)).background(if (sukses) Color(0xFF2EAD5B) else WarnaGalat).padding(4.dp))
        Column {
            Text(nama, color = DesignColors.InkStrong, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(keterangan, color = DesignColors.InkMuted, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun KolomTeks(nilai: String, onUbah: (String) -> Unit, petunjuk: String) {
    TextField(
        value = nilai,
        onValueChange = onUbah,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(petunjuk, color = DesignColors.Placeholder, fontSize = 13.sp) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DesignColors.FieldPill,
            unfocusedContainerColor = DesignColors.FieldPill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = DesignColors.InkStrong,
            unfocusedTextColor = DesignColors.InkStrong,
            cursorColor = DesignColors.Accent,
        ),
    )
}
