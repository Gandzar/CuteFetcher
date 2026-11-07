package io.ymusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bagja.streamvortex.pengambil_media.FormatMedia
import dev.bagja.streamvortex.pengambil_media.HasilMedia
import dev.bagja.streamvortex.pengambil_media.HasilPengelola
import dev.bagja.streamvortex.pengambil_media.HasilValidasi
import dev.bagja.streamvortex.pengambil_media.ItemUnduhan
import dev.bagja.streamvortex.pengambil_media.JenisMedia
import dev.bagja.streamvortex.pengambil_media.KategoriGagal
import dev.bagja.streamvortex.pengambil_media.MesinUnduh
import dev.bagja.streamvortex.pengambil_media.PengelolaScraper
import dev.bagja.streamvortex.pengambil_media.Platform
import dev.bagja.streamvortex.pengambil_media.StatusUnduhan
import dev.bagja.streamvortex.pengambil_media.deteksiPlatform
import dev.bagja.streamvortex.pengambil_media.validasiUrl
import io.ymusic.imageloader.AsyncImage
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.pengunduh_analisis
import io.ymusic.ui.resources.pengunduh_antrean
import io.ymusic.ui.resources.pengunduh_antrean_kosong
import io.ymusic.ui.resources.pengunduh_batal
import io.ymusic.ui.resources.pengunduh_bersihkan
import io.ymusic.ui.resources.pengunduh_dibatalkan
import io.ymusic.ui.resources.pengunduh_didukung
import io.ymusic.ui.resources.pengunduh_dijeda
import io.ymusic.ui.resources.pengunduh_gagal_dibatasi
import io.ymusic.ui.resources.pengunduh_gagal_internal
import io.ymusic.ui.resources.pengunduh_gagal_jaringan
import io.ymusic.ui.resources.pengunduh_gagal_media
import io.ymusic.ui.resources.pengunduh_gagal_platform
import io.ymusic.ui.resources.pengunduh_gagal_url
import io.ymusic.ui.resources.pengunduh_hak
import io.ymusic.ui.resources.pengunduh_hero_judul
import io.ymusic.ui.resources.pengunduh_hero_sub
import io.ymusic.ui.resources.pengunduh_hint
import io.ymusic.ui.resources.pengunduh_jeda
import io.ymusic.ui.resources.pengunduh_lanjut
import io.ymusic.ui.resources.pengunduh_menganalisis
import io.ymusic.ui.resources.pengunduh_menunggu
import io.ymusic.ui.resources.pengunduh_mesin_belum
import io.ymusic.ui.resources.pengunduh_pilih_format
import io.ymusic.ui.resources.pengunduh_platform_terdeteksi
import io.ymusic.ui.resources.pengunduh_selesai
import io.ymusic.ui.resources.pengunduh_unduh
import io.ymusic.ui.resources.pengunduh_unduh_belum
import io.ymusic.ui.resources.pengunduh_via
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Tahap layar Downloader. */
private sealed interface TahapPengunduh {
    data object Diam : TahapPengunduh

    data object Memuat : TahapPengunduh

    data class Hasil(val hasil: HasilMedia) : TahapPengunduh

    data class Gagal(val kategori: KategoriGagal, val detail: String?) : TahapPengunduh

    /** Mesin ekstraksi belum dipasang pada build ini: ditampilkan apa adanya, bukan hasil palsu. */
    data object MesinBelumAda : TahapPengunduh
}

/** Platform yang ditampilkan di baris "didukung" (fitur yang ditunda tidak ikut). */
private val PlatformTampil = Platform.entries.filter { it != Platform.TIDAK_DIKENAL }

/**
 * Layar Downloader: tempel tautan -> analisis -> pilih format -> antrean.
 * [pengelola] dan [mesinUnduh] null = mesinnya belum terpasang di build ini; layar tetap tampil
 * dan menyatakannya dengan jelas (tidak pernah memalsukan hasil).
 */
@Composable
fun PengunduhScreen(
    pengelola: PengelolaScraper?,
    mesinUnduh: MesinUnduh?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var masukan by remember { mutableStateOf("") }
    var tahap by remember { mutableStateOf<TahapPengunduh>(TahapPengunduh.Diam) }
    var pekerjaan by remember { mutableStateOf<Job?>(null) }
    var pesanUnduh by remember { mutableStateOf<Boolean>(false) }

    val platformTerdeteksi = remember(masukan) {
        (validasiUrl(masukan) as? HasilValidasi.Valid)
            ?.let { deteksiPlatform(it.host) }
            ?.takeIf { it != Platform.TIDAK_DIKENAL }
    }

    val analisis: () -> Unit = {
        pekerjaan?.cancel()
        pesanUnduh = false
        if (pengelola == null) {
            tahap = TahapPengunduh.MesinBelumAda
        } else {
            tahap = TahapPengunduh.Memuat
            pekerjaan = scope.launch {
                try {
                    tahap = when (val r = pengelola.ekstrak(masukan)) {
                        is HasilPengelola.Sukses -> TahapPengunduh.Hasil(r.hasil)
                        is HasilPengelola.Gagal -> TahapPengunduh.Gagal(r.kategori, r.pesan)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Pengelola sudah membungkus kegagalan mesin; ini jaring pengaman terakhir. Rinciannya
                    // tidak ditampilkan ke pengguna (kategori INTERNAL saja).
                    tahap = TahapPengunduh.Gagal(KategoriGagal.INTERNAL, null)
                }
            }
        }
    }

    val aliranAntrean = remember(mesinUnduh) { mesinUnduh?.antrean ?: MutableStateFlow(emptyList<ItemUnduhan>()) }
    val antrean by aliranAntrean.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize().background(DesignColors.Surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = DesignSpacing.MarginCompact,
            vertical = DesignSpacing.Space16,
        ),
        verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space16),
    ) {
        item { Hero() }
        item {
            KotakMasukan(
                nilai = masukan,
                onNilai = { masukan = it },
                memuat = tahap is TahapPengunduh.Memuat,
                platform = platformTerdeteksi,
                onAnalisis = analisis,
            )
        }
        item {
            AnimatedVisibility(
                visible = tahap !is TahapPengunduh.Diam,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 },
                exit = fadeOut(tween(120)),
            ) {
                when (val t = tahap) {
                    TahapPengunduh.Diam -> Unit
                    TahapPengunduh.Memuat -> StatusMemuat()
                    TahapPengunduh.MesinBelumAda -> KartuPesan(stringResource(Res.string.pengunduh_mesin_belum), galat = false)
                    is TahapPengunduh.Gagal -> KartuPesan(
                        pesanGagal(t.kategori),
                        galat = true,
                        detail = t.detail.takeIf { t.kategori == KategoriGagal.DIBATASI || t.kategori == KategoriGagal.JARINGAN },
                    )
                    is TahapPengunduh.Hasil -> KartuHasil(
                        hasil = t.hasil,
                        pesanUnduh = pesanUnduh,
                        onUnduh = { format ->
                            if (mesinUnduh == null) {
                                pesanUnduh = true
                            } else {
                                scope.launch { mesinUnduh.tambah(t.hasil, format) }
                            }
                        },
                    )
                }
            }
        }
        item { BarisPlatform() }

        item {
            Text(
                stringResource(Res.string.pengunduh_antrean),
                color = DesignColors.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (antrean.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.pengunduh_antrean_kosong),
                    color = DesignColors.InkMuted,
                    fontSize = 14.sp,
                )
            }
        } else {
            items(antrean, key = { it.id }) { item ->
                BarisAntrean(
                    item = item,
                    onJeda = { mesinUnduh?.jeda(item.id) },
                    onLanjut = { mesinUnduh?.lanjutkan(item.id) },
                    onBatal = { mesinUnduh?.batalkan(item.id) },
                    onBersihkan = { mesinUnduh?.bersihkan(item.id) },
                )
            }
        }
        item {
            Text(
                stringResource(Res.string.pengunduh_hak),
                color = DesignColors.InkFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = DesignSpacing.Space8, bottom = DesignSpacing.Space24),
            )
        }
    }
}

@Composable
private fun Hero() {
    val aksen = DesignColors.Accent
    val brush = Brush.linearGradient(
        listOf(aksen, aksen.copy(alpha = 0.78f), Color(0xFF7C4DFF).copy(alpha = 0.85f)),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .padding(DesignSpacing.Space24),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space8)) {
            Text(
                stringResource(Res.string.pengunduh_hero_judul),
                color = DesignColors.OnAccent,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.pengunduh_hero_sub),
                color = DesignColors.OnAccent.copy(alpha = 0.85f),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun KotakMasukan(
    nilai: String,
    onNilai: (String) -> Unit,
    memuat: Boolean,
    platform: Platform?,
    onAnalisis: () -> Unit,
) {
    val aksen = DesignColors.Accent
    // Bingkai bergradien yang bergeser hanya saat memuat; diam = garis tipis biasa (hemat baterai).
    val geser = if (memuat) {
        val transisi = rememberInfiniteTransition(label = "bingkai")
        val g by transisi.animateFloat(
            initialValue = 0f,
            targetValue = 600f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "geser",
        )
        g
    } else {
        0f
    }
    val garis: Brush = if (memuat) {
        Brush.linearGradient(
            colors = listOf(aksen, Color(0xFF7C4DFF), aksen),
            start = Offset(geser, 0f),
            end = Offset(geser + 300f, 300f),
            tileMode = TileMode.Repeated,
        )
    } else {
        Brush.linearGradient(listOf(DesignColors.Outline, DesignColors.Outline))
    }
    val bentuk = RoundedCornerShape(22.dp)
    Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space8)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(bentuk)
                .background(DesignColors.FieldPill)
                .border(BorderStroke(if (memuat) 2.dp else 1.dp, garis), bentuk)
                .padding(start = DesignSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = DesignColors.InkMuted, modifier = Modifier.padding(start = 8.dp))
            TextField(
                value = nilai,
                onValueChange = onNilai,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.pengunduh_hint), color = DesignColors.Placeholder) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (nilai.isNotBlank() && !memuat) onAnalisis() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = DesignColors.InkStrong,
                    unfocusedTextColor = DesignColors.InkStrong,
                    cursorColor = aksen,
                ),
            )
            if (nilai.isNotEmpty()) {
                IconButton(onClick = { onNilai("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = DesignColors.InkMuted)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.weight(1f)) {
                // Crossfade (bukan AnimatedVisibility): di dalam Box-di-Row, AnimatedVisibility ambigu (RowScope/BoxScope).
                Crossfade(targetState = platform, label = "lencanaPlatform") { p ->
                    if (p != null) LencanaPlatform(stringResource(Res.string.pengunduh_platform_terdeteksi, p.namaTampil))
                }
            }
            TombolGradien(
                teks = stringResource(Res.string.pengunduh_analisis),
                ikon = Icons.Rounded.Search,
                aktif = nilai.isNotBlank() && !memuat,
                onClick = onAnalisis,
            )
        }
    }
}

@Composable
private fun LencanaPlatform(teks: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(DesignColors.Accent.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(DesignColors.Accent))
        Spacer(Modifier.width(8.dp))
        Text(teks, color = DesignColors.InkStrong, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TombolGradien(teks: String, ikon: ImageVector, aktif: Boolean, onClick: () -> Unit) {
    val aksen = DesignColors.Accent
    val brush = if (aktif) {
        Brush.linearGradient(listOf(aksen, Color(0xFF7C4DFF)))
    } else {
        Brush.linearGradient(listOf(DesignColors.OutlineSoft, DesignColors.OutlineSoft))
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(enabled = aktif, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(ikon, contentDescription = null, tint = if (aktif) DesignColors.OnAccent else DesignColors.InkFaint, modifier = Modifier.size(18.dp))
        Text(teks, color = if (aktif) DesignColors.OnAccent else DesignColors.InkFaint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun StatusMemuat() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = DesignSpacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = DesignColors.Accent)
        Text(stringResource(Res.string.pengunduh_menganalisis), color = DesignColors.InkMuted, fontSize = 14.sp)
    }
}

@Composable
private fun KartuPesan(teks: String, galat: Boolean, detail: String? = null) {
    val warna = if (galat) Color(0xFFE5484D) else DesignColors.Accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(warna.copy(alpha = 0.10f))
            .border(1.dp, warna.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(DesignSpacing.Space16),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = warna)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(teks, color = DesignColors.InkStrong, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            // Detail dari mesin hanya untuk pembatasan/jaringan (pesan sudah aman ditampilkan).
            if (!detail.isNullOrBlank()) Text(detail, color = DesignColors.InkMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun pesanGagal(kategori: KategoriGagal): String = stringResource(
    when (kategori) {
        KategoriGagal.URL_TIDAK_VALID -> Res.string.pengunduh_gagal_url
        KategoriGagal.PLATFORM_TIDAK_DIDUKUNG -> Res.string.pengunduh_gagal_platform
        KategoriGagal.JARINGAN -> Res.string.pengunduh_gagal_jaringan
        KategoriGagal.DIBATASI -> Res.string.pengunduh_gagal_dibatasi
        KategoriGagal.TIDAK_ADA_MEDIA -> Res.string.pengunduh_gagal_media
        KategoriGagal.INTERNAL -> Res.string.pengunduh_gagal_internal
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KartuHasil(hasil: HasilMedia, pesanUnduh: Boolean, onUnduh: (FormatMedia) -> Unit) {
    var terpilih by remember(hasil) { mutableStateOf(formatTerbaik(hasil.formatMedia)) }
    val pemuat = LocalImageLoader.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DesignColors.SettingsCard)
            .border(1.dp, DesignColors.OutlineSoft, RoundedCornerShape(24.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp).background(DesignColors.SurfaceDim)) {
            if (pemuat != null && hasil.thumbnail != null) {
                AsyncImage(
                    loader = pemuat,
                    url = hasil.thumbnail,
                    contentDescription = hasil.judul,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(ikonJenis(hasil.jenisMedia), contentDescription = null, tint = DesignColors.InkFaint, modifier = Modifier.align(Alignment.Center).size(44.dp))
            }
            hasil.durasiDetik?.let { d ->
                Text(
                    formatDurasi(d),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DesignColors.BadgeScrim)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.padding(DesignSpacing.Space16), verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space8)) {
            Text(hasil.judul, color = DesignColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(hasil.penulis, hasil.platform.namaTampil).joinToString(" · "),
                color = DesignColors.InkMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(Res.string.pengunduh_pilih_format), color = DesignColors.InkSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = DesignSpacing.Space8))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hasil.formatMedia.forEach { f ->
                    ChipFormat(f, dipilih = f.id == terpilih?.id, onClick = { terpilih = f })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = DesignSpacing.Space8), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.pengunduh_via, hasil.mesin), color = DesignColors.InkFaint, fontSize = 12.sp)
                TombolGradien(
                    teks = stringResource(Res.string.pengunduh_unduh),
                    ikon = Icons.Rounded.Download,
                    aktif = terpilih != null,
                    onClick = { terpilih?.let(onUnduh) },
                )
            }
            if (pesanUnduh) Text(stringResource(Res.string.pengunduh_unduh_belum), color = Color(0xFFE5484D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChipFormat(format: FormatMedia, dipilih: Boolean, onClick: () -> Unit) {
    val aksen = DesignColors.Accent
    val bentuk = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(bentuk)
            .background(if (dipilih) aksen.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (dipilih) aksen else DesignColors.Outline, bentuk)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(ikonJenis(format.jenis), contentDescription = null, tint = if (dipilih) aksen else DesignColors.InkMuted, modifier = Modifier.size(16.dp))
        Text(format.label + (format.ukuranByte?.let { " · " + formatUkuran(it) } ?: ""), color = DesignColors.InkStrong, fontSize = 13.sp, fontWeight = if (dipilih) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun BarisPlatform() {
    Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space8)) {
        Text(stringResource(Res.string.pengunduh_didukung), color = DesignColors.InkSecondary, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlatformTampil, key = { it.name }) { p ->
                Text(
                    p.namaTampil,
                    color = DesignColors.InkMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, DesignColors.OutlineSoft, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun BarisAntrean(
    item: ItemUnduhan,
    onJeda: () -> Unit,
    onLanjut: () -> Unit,
    onBatal: () -> Unit,
    onBersihkan: () -> Unit,
) {
    val aktif = item.status == StatusUnduhan.BERJALAN || item.status == StatusUnduhan.MENUNGGU || item.status == StatusUnduhan.DIJEDA
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DesignColors.SettingsCard)
            .padding(DesignSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.judul, color = DesignColors.InkStrong, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.label + " · " + teksStatus(item), color = DesignColors.InkMuted, fontSize = 12.sp)
            }
            if (item.status == StatusUnduhan.BERJALAN && item.bisaDijeda) {
                IconButton(onClick = onJeda) { Icon(Icons.Rounded.Pause, contentDescription = stringResource(Res.string.pengunduh_jeda), tint = DesignColors.InkStrong) }
            }
            if (item.status == StatusUnduhan.DIJEDA) {
                IconButton(onClick = onLanjut) { Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(Res.string.pengunduh_lanjut), tint = DesignColors.InkStrong) }
            }
            if (aktif) {
                IconButton(onClick = onBatal) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.pengunduh_batal), tint = DesignColors.InkStrong) }
            } else {
                IconButton(onClick = onBersihkan) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.pengunduh_bersihkan), tint = DesignColors.InkMuted) }
            }
        }
        if (aktif) {
            val p = item.progres
            if (p != null) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = DesignColors.Accent, trackColor = DesignColors.OutlineSoft)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DesignColors.Accent, trackColor = DesignColors.OutlineSoft)
            }
        }
        if (item.status == StatusUnduhan.GAGAL && !item.pesanGagal.isNullOrBlank()) {
            Text(item.pesanGagal.orEmpty(), color = Color(0xFFE5484D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun teksStatus(item: ItemUnduhan): String = when (item.status) {
    StatusUnduhan.MENUNGGU -> stringResource(Res.string.pengunduh_menunggu)
    StatusUnduhan.BERJALAN -> item.progres?.let { "${(it * 100).toInt()}%" } ?: formatUkuran(item.byteSelesai)
    StatusUnduhan.DIJEDA -> stringResource(Res.string.pengunduh_dijeda)
    StatusUnduhan.SELESAI -> stringResource(Res.string.pengunduh_selesai)
    StatusUnduhan.GAGAL -> stringResource(Res.string.pengunduh_gagal_internal)
    StatusUnduhan.DIBATALKAN -> stringResource(Res.string.pengunduh_dibatalkan)
}

private fun ikonJenis(jenis: JenisMedia): ImageVector = when (jenis) {
    JenisMedia.AUDIO -> Icons.Rounded.MusicNote
    JenisMedia.GAMBAR -> Icons.Rounded.Image
    JenisMedia.VIDEO, JenisMedia.CAMPURAN -> Icons.Rounded.Videocam
}

/** Format awal terpilih: video tertinggi; jika tak ada video, yang pertama. */
private fun formatTerbaik(daftar: List<FormatMedia>): FormatMedia? =
    daftar.filter { it.jenis == JenisMedia.VIDEO }.maxByOrNull { it.tinggi ?: 0 } ?: daftar.firstOrNull()

private fun formatDurasi(detik: Long): String {
    val j = detik / 3600
    val m = (detik % 3600) / 60
    val s = detik % 60
    fun dua(n: Long) = n.toString().padStart(2, '0')
    return if (j > 0) "$j:${dua(m)}:${dua(s)}" else "$m:${dua(s)}"
}

private fun formatUkuran(byte: Long): String {
    if (byte < 1024) return "$byte B"
    val kb = byte / 1024.0
    if (kb < 1024) return "${(kb * 10).toLong() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toLong() / 10.0} MB"
    return "${(mb / 1024.0 * 100).toLong() / 100.0} GB"
}
