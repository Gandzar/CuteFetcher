package io.ymusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bagja.streamvortex.pengolah_audio.PengaturanMixer
import dev.bagja.streamvortex.pengolah_audio.PengendaliMixer
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.mixer_aktif
import io.ymusic.ui.resources.mixer_bass
import io.ymusic.ui.resources.mixer_chorus
import io.ymusic.ui.resources.mixer_echo
import io.ymusic.ui.resources.mixer_efek
import io.ymusic.ui.resources.mixer_eq
import io.ymusic.ui.resources.mixer_info_lokal
import io.ymusic.ui.resources.mixer_jalur
import io.ymusic.ui.resources.mixer_judul
import io.ymusic.ui.resources.mixer_lebar
import io.ymusic.ui.resources.mixer_preset
import io.ymusic.ui.resources.mixer_reset
import io.ymusic.ui.resources.mixer_reverb
import io.ymusic.ui.resources.mixer_rotasi
import io.ymusic.ui.resources.mixer_searah
import io.ymusic.ui.resources.mixer_telepon
import io.ymusic.ui.resources.mixer_tidak_tersedia
import io.ymusic.ui.resources.mixer_tremolo
import io.ymusic.ui.resources.mixer_volume
import io.ymusic.ui.resources.mixer_transisi
import io.ymusic.ui.resources.mixer_ruang
import io.ymusic.ui.resources.mixer_offset_z
import io.ymusic.ui.resources.mixer_offset_x
import io.ymusic.ui.resources.mixer_drive
import io.ymusic.ui.resources.mixer_lp
import io.ymusic.ui.resources.mixer_kompresor
import io.ymusic.ui.resources.mixer_interval
import io.ymusic.ui.resources.mixer_halus
import io.ymusic.ui.resources.mixer_hp
import io.ymusic.ui.resources.mixer_gerak_putar
import io.ymusic.ui.resources.mixer_gerak_bolak
import io.ymusic.ui.resources.mixer_gerak_acak
import io.ymusic.ui.resources.mixer_gambar_petunjuk
import io.ymusic.ui.resources.mixer_gerak
import io.ymusic.ui.resources.mixer_fase
import io.ymusic.ui.resources.mixer_freq_z
import io.ymusic.ui.resources.mixer_freq_x
import io.ymusic.ui.resources.mixer_filter
import io.ymusic.ui.resources.mixer_wah
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val EQ_MAKS_DB = 12f

private val UngguMixer = Color(0xFF7C4DFF)

/**
 * Tab Mixer: kontrol DSP dan audio 8D. [pengendali] null = platform tanpa mixer (dinyatakan jelas).
 * Efek hanya berlaku pada pemutaran lokal; teks info di layar menyatakan hal itu.
 */
@Composable
fun MixerScreen(pengendali: PengendaliMixer?, modifier: Modifier = Modifier) {
    if (pengendali == null) {
        Box(modifier.fillMaxSize().background(DesignColors.Surface), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.mixer_tidak_tersedia), color = DesignColors.InkMuted, fontSize = 14.sp)
        }
        return
    }
    val s by pengendali.pengaturan.collectAsState()
    val ubah = pengendali::ubah

    LazyColumn(
        modifier = modifier.fillMaxSize().background(DesignColors.Surface),
        contentPadding = PaddingValues(horizontal = DesignSpacing.MarginCompact, vertical = DesignSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space16),
    ) {
        item { KartuSaklar(aktif = s.enabled, onUbah = { v -> ubah { it.copy(enabled = v) } }) }
        item {
            // Titik jalur dihitung ulang hanya bila parameter jalurnya berubah (sama persis dengan mesin audio).
            val titik = remember(s.jalurId, s.jalurKustom, s.halusKustom, s.lissajousFreqX, s.lissajousFreqZ, s.lissajousFase) {
                pengendali.titikJalur(s)
            }
            BolaSuaraKanvas(pengendali, aktif = s.enabled, titikJalur = titik)
        }
        item {
            Bagian(stringResource(Res.string.mixer_preset)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pengendali.daftarPreset, key = { it.id }) { p ->
                        Pil(p.nama, dipilih = false, onClick = { pengendali.terapkanPreset(p.id) })
                    }
                }
            }
        }
        item {
            Bagian(stringResource(Res.string.mixer_jalur)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pengendali.daftarJalur, key = { it.id }) { j ->
                        Pil(j.label, dipilih = j.id == s.jalurId, onClick = { ubah { it.copy(jalurId = j.id) } })
                    }
                }
                BarisSlider(stringResource(Res.string.mixer_rotasi), satuDesimal(s.rotationSeconds), s.rotationSeconds, 2f..60f) { v ->
                    ubah { it.copy(rotationSeconds = v) }
                }
                BarisSlider(stringResource(Res.string.mixer_lebar), persen(s.width), s.width, 0.2f..1.5f) { v ->
                    ubah { it.copy(width = v) }
                }
                BarisSaklar(stringResource(Res.string.mixer_searah), s.clockwise) { v -> ubah { it.copy(clockwise = v) } }
                if (s.jalurId == PengaturanMixer.JALUR_KUSTOM) {
                    PapanGambarJalur(
                        titikAwal = s.jalurKustom,
                        onSelesai = { baru -> ubah { it.copy(jalurKustom = baru) } },
                    )
                    BarisSlider(stringResource(Res.string.mixer_halus), "${s.halusKustom}", s.halusKustom.toFloat(), 0f..8f) { v ->
                        ubah { it.copy(halusKustom = v.roundToInt()) }
                    }
                }
                Text(stringResource(Res.string.mixer_gerak), color = DesignColors.InkSecondary, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Pil(stringResource(Res.string.mixer_gerak_putar), s.gerak == PengaturanMixer.GERAK_PUTAR) { ubah { it.copy(gerak = PengaturanMixer.GERAK_PUTAR) } } }
                    item { Pil(stringResource(Res.string.mixer_gerak_bolak), s.gerak == PengaturanMixer.GERAK_BOLAK_BALIK) { ubah { it.copy(gerak = PengaturanMixer.GERAK_BOLAK_BALIK) } } }
                    item { Pil(stringResource(Res.string.mixer_gerak_acak), s.gerak == PengaturanMixer.GERAK_ACAK) { ubah { it.copy(gerak = PengaturanMixer.GERAK_ACAK) } } }
                }
                if (s.gerak != PengaturanMixer.GERAK_PUTAR) {
                    BarisSlider(stringResource(Res.string.mixer_interval), satuDesimal(s.intervalMs / 1000f), s.intervalMs, 500f..10000f) { v ->
                        ubah { it.copy(intervalMs = v) }
                    }
                }
                if (s.jalurId == PengaturanMixer.JALUR_LISSAJOUS) {
                    BarisSlider(stringResource(Res.string.mixer_freq_x), "${s.lissajousFreqX}", s.lissajousFreqX.toFloat(), 1f..8f) { v ->
                        ubah { it.copy(lissajousFreqX = v.roundToInt()) }
                    }
                    BarisSlider(stringResource(Res.string.mixer_freq_z), "${s.lissajousFreqZ}", s.lissajousFreqZ.toFloat(), 1f..8f) { v ->
                        ubah { it.copy(lissajousFreqZ = v.roundToInt()) }
                    }
                    BarisSlider(stringResource(Res.string.mixer_fase), "${s.lissajousFase}°", s.lissajousFase.toFloat(), 0f..180f) { v ->
                        ubah { it.copy(lissajousFase = v.roundToInt()) }
                    }
                }
                BarisSlider(stringResource(Res.string.mixer_offset_x), satuDesimal(s.offsetX), s.offsetX, -1f..1f) { v -> ubah { it.copy(offsetX = v) } }
                BarisSlider(stringResource(Res.string.mixer_offset_z), satuDesimal(s.offsetZ), s.offsetZ, -1f..1f) { v -> ubah { it.copy(offsetZ = v) } }
                BarisSlider(stringResource(Res.string.mixer_ruang), persen(s.room), s.room, 0.2f..1.5f) { v -> ubah { it.copy(room = v) } }
                BarisSlider(stringResource(Res.string.mixer_transisi), satuDesimal(s.transisiDetik), s.transisiDetik, 0f..3f) { v ->
                    ubah { it.copy(transisiDetik = v) }
                }
            }
        }
        item {
            Bagian(stringResource(Res.string.mixer_eq)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    s.eq.forEachIndexed { i, nilai ->
                        BatangEq(
                            label = PengaturanMixer.LabelBandEq.getOrElse(i) { "" },
                            nilai = nilai,
                            onUbah = { v -> ubah { cur -> cur.copy(eq = cur.eq.mapIndexed { j, g -> if (j == i) v else g }) } },
                        )
                    }
                }
                BarisSlider(stringResource(Res.string.mixer_bass), "+" + satuDesimal(s.bassDb) + " dB", s.bassDb, 0f..12f) { v ->
                    ubah { it.copy(bassDb = v) }
                }
            }
        }
        item {
            Bagian(stringResource(Res.string.mixer_reverb)) {
                BarisSlider(stringResource(Res.string.mixer_reverb), persen(s.reverbMix), s.reverbMix, 0f..1f) { v ->
                    ubah { it.copy(reverbMix = v) }
                }
                BarisSlider("Decay", satuDesimal(s.reverbDecay) + " s", s.reverbDecay, 0.5f..8f) { v ->
                    ubah { it.copy(reverbDecay = v) }
                }
            }
        }
        item {
            Bagian(stringResource(Res.string.mixer_echo)) {
                BarisSlider(stringResource(Res.string.mixer_echo), persen(s.echoMix), s.echoMix, 0f..1f) { v ->
                    ubah { it.copy(echoMix = v) }
                }
                BarisSlider("ms", "${s.echoMs.roundToInt()} ms", s.echoMs, 50f..1000f) { v -> ubah { it.copy(echoMs = v) } }
                BarisSlider("Feedback", persen(s.echoFeedback), s.echoFeedback, 0f..0.9f) { v -> ubah { it.copy(echoFeedback = v) } }
            }
        }
        item {
            Bagian(stringResource(Res.string.mixer_filter)) {
                BarisSlider(stringResource(Res.string.mixer_hp), "${s.highpassHz.roundToInt()} Hz", s.highpassHz, 20f..2000f) { v -> ubah { it.copy(highpassHz = v) } }
                BarisSlider(stringResource(Res.string.mixer_lp), "${s.lowpassHz.roundToInt()} Hz", s.lowpassHz, 2000f..20000f) { v -> ubah { it.copy(lowpassHz = v) } }
                BarisSlider(stringResource(Res.string.mixer_drive), persen(s.drive), s.drive, 0f..1f) { v -> ubah { it.copy(drive = v) } }
                BarisSlider(stringResource(Res.string.mixer_kompresor), persen(s.compressor), s.compressor, 0f..1f) { v -> ubah { it.copy(compressor = v) } }
                BarisSlider(stringResource(Res.string.mixer_volume), persen(s.volume), s.volume, 0f..1f) { v -> ubah { it.copy(volume = v) } }
            }
        }
        item { BagianEfek(s, ubah) }
        item {
            Pil(stringResource(Res.string.mixer_reset), dipilih = false, onClick = { ubah { PengaturanMixer(enabled = it.enabled) } })
        }
        item {
            Text(stringResource(Res.string.mixer_info_lokal), color = DesignColors.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(bottom = DesignSpacing.Space24))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BagianEfek(s: PengaturanMixer, ubah: ((PengaturanMixer) -> PengaturanMixer) -> Unit) {
    Bagian(stringResource(Res.string.mixer_efek)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pil(stringResource(Res.string.mixer_chorus), s.chorusOn) { ubah { it.copy(chorusOn = !it.chorusOn) } }
            Pil(stringResource(Res.string.mixer_tremolo), s.tremoloOn) { ubah { it.copy(tremoloOn = !it.tremoloOn) } }
            Pil(stringResource(Res.string.mixer_wah), s.wahOn) { ubah { it.copy(wahOn = !it.wahOn) } }
            Pil(stringResource(Res.string.mixer_telepon), s.phoneOn) { ubah { it.copy(phoneOn = !it.phoneOn) } }
        }
    }
}

@Composable
private fun KartuSaklar(aktif: Boolean, onUbah: (Boolean) -> Unit) {
    val brush = Brush.linearGradient(
        if (aktif) listOf(DesignColors.Accent, UngguMixer) else listOf(DesignColors.SettingsCard, DesignColors.SettingsCard),
    )
    val teks = if (aktif) DesignColors.OnAccent else DesignColors.InkStrong
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brush).padding(DesignSpacing.Space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.mixer_judul), color = teks, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(stringResource(Res.string.mixer_aktif), color = teks.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
        Switch(
            checked = aktif,
            onCheckedChange = onUbah,
            colors = SwitchDefaults.colors(checkedTrackColor = Color.White.copy(alpha = 0.35f), checkedThumbColor = Color.White),
        )
    }
}

/** Kepala dari atas: bola suara bergerak sesuai posisi nyata dari mesin DSP (bukan animasi palsu). */
@Composable
private fun BolaSuaraKanvas(pengendali: PengendaliMixer, aktif: Boolean, titikJalur: FloatArray) {
    val bola by pengendali.bola.collectAsState()
    val aksen = DesignColors.Accent
    val garis = DesignColors.Outline
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(24.dp))
            .background(DesignColors.SettingsCard)
            .border(1.dp, DesignColors.OutlineSoft, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pusat = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.42f
            for (k in 1..3) {
                drawCircle(garis.copy(alpha = 0.25f * (4 - k)), radius = r * k / 3f, center = pusat, style = Stroke(width = 1.5f))
            }
            // Pratinjau jalur gerak: garis tertutup dari titik-titik yang dipakai mesin audio.
            if (titikJalur.size >= 4) {
                val jalur = androidx.compose.ui.graphics.Path()
                for (i in 0 until titikJalur.size / 2) {
                    val px = pusat.x + titikJalur[2 * i].coerceIn(-1f, 1f) * r
                    val py = pusat.y + titikJalur[2 * i + 1].coerceIn(-1f, 1f) * r
                    if (i == 0) jalur.moveTo(px, py) else jalur.lineTo(px, py)
                }
                jalur.close()
                drawPath(jalur, aksen.copy(alpha = 0.55f), style = Stroke(width = 3f))
            }
            drawCircle(garis.copy(alpha = 0.5f), radius = r * 0.12f, center = pusat) // kepala
            if (aktif) {
                // z negatif = depan (atas layar), sama dengan konvensi jalur di mesin.
                val posisi = Offset(pusat.x + bola.x.coerceIn(-1f, 1f) * r, pusat.y + bola.z.coerceIn(-1f, 1f) * r)
                drawCircle(
                    brush = Brush.radialGradient(listOf(aksen.copy(alpha = 0.55f), Color.Transparent), center = posisi, radius = r * 0.35f),
                    radius = r * 0.35f,
                    center = posisi,
                )
                drawCircle(aksen, radius = r * 0.08f, center = posisi)
            }
        }
    }
}

@Composable
private fun Bagian(judul: String, isi: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DesignColors.SettingsCard)
            .padding(DesignSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(DesignSpacing.Space8),
    ) {
        Text(judul, color = DesignColors.InkStrong, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        isi()
    }
}

@Composable
private fun Pil(teks: String, dipilih: Boolean, onClick: () -> Unit) {
    val bentuk = RoundedCornerShape(50)
    val aksen = DesignColors.Accent
    Text(
        teks,
        color = DesignColors.InkStrong,
        fontSize = 13.sp,
        fontWeight = if (dipilih) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(bentuk)
            .background(if (dipilih) aksen.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (dipilih) aksen else DesignColors.Outline, bentuk)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun BarisSlider(label: String, nilaiTeks: String, nilai: Float, rentang: ClosedFloatingPointRange<Float>, onUbah: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = DesignColors.InkSecondary, fontSize = 13.sp)
            Text(nilaiTeks, color = DesignColors.InkMuted, fontSize = 13.sp)
        }
        Slider(
            value = nilai.coerceIn(rentang.start, rentang.endInclusive),
            onValueChange = onUbah,
            valueRange = rentang,
            colors = SliderDefaults.colors(
                thumbColor = DesignColors.Accent,
                activeTrackColor = DesignColors.Accent,
                inactiveTrackColor = DesignColors.OutlineSoft,
            ),
        )
    }
}

@Composable
private fun BarisSaklar(label: String, nilai: Boolean, onUbah: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DesignColors.InkSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = nilai, onCheckedChange = onUbah)
    }
}

/**
 * Papan untuk menggambar jalur gerak suara dengan jari. Titik disimpan ternormalisasi (-1..1) dan dikurangi
 * menjadi paling banyak [MAKS_TITIK_GAMBAR]; mesin audio menghaluskan dan menutup bentuknya.
 * Kepala pendengar ada di tengah; atas = depan, bawah = belakang.
 */
@Composable
private fun PapanGambarJalur(titikAwal: List<Float>, onSelesai: (List<Float>) -> Unit) {
    val garis = remember { mutableStateListOf<Offset>() }
    var ukuran by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val aksen = DesignColors.Accent
    val warnaGaris = DesignColors.Outline

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.mixer_gambar_petunjuk), color = DesignColors.InkMuted, fontSize = 12.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(16.dp))
                .background(DesignColors.FieldPill)
                .border(1.dp, DesignColors.OutlineSoft, RoundedCornerShape(16.dp))
                .onSizeChanged { ukuran = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { garis.clear(); garis.add(it) },
                        onDrag = { perubahan, _ ->
                            perubahan.consume()
                            garis.add(perubahan.position)
                        },
                        onDragEnd = {
                            val l = ukuran.width.toFloat().coerceAtLeast(1f)
                            val t = ukuran.height.toFloat().coerceAtLeast(1f)
                            if (garis.size >= 3) {
                                val langkah = (garis.size / MAKS_TITIK_GAMBAR).coerceAtLeast(1)
                                val hasil = ArrayList<Float>()
                                garis.filterIndexed { i, _ -> i % langkah == 0 }.forEach { o ->
                                    hasil.add(((o.x / l) * 2f - 1f).coerceIn(-1f, 1f))
                                    hasil.add(((o.y / t) * 2f - 1f).coerceIn(-1f, 1f))
                                }
                                onSelesai(hasil)
                            }
                            garis.clear()
                        },
                        onDragCancel = { garis.clear() },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(warnaGaris.copy(alpha = 0.4f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1f)
                drawLine(warnaGaris.copy(alpha = 0.4f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1f)
                drawCircle(warnaGaris.copy(alpha = 0.6f), radius = size.minDimension * 0.05f, center = Offset(size.width / 2f, size.height / 2f))
                // Sedang digambar: garis hidup; selain itu tampilkan gambar yang tersimpan.
                if (garis.size >= 2) {
                    val j = androidx.compose.ui.graphics.Path()
                    garis.forEachIndexed { i, o -> if (i == 0) j.moveTo(o.x, o.y) else j.lineTo(o.x, o.y) }
                    drawPath(j, aksen, style = Stroke(width = 5f))
                } else if (titikAwal.size >= 6) {
                    val j = androidx.compose.ui.graphics.Path()
                    for (i in 0 until titikAwal.size / 2) {
                        val px = (titikAwal[2 * i] + 1f) / 2f * size.width
                        val py = (titikAwal[2 * i + 1] + 1f) / 2f * size.height
                        if (i == 0) j.moveTo(px, py) else j.lineTo(px, py)
                    }
                    drawPath(j, aksen.copy(alpha = 0.7f), style = Stroke(width = 4f))
                }
            }
        }
    }
}

private const val MAKS_TITIK_GAMBAR = 96

/** Satu band EQ: batang vertikal yang bisa diseret atau diketuk (-12..+12 dB). */
@Composable
private fun BatangEq(label: String, nilai: Float, onUbah: (Float) -> Unit) {
    val aksen = DesignColors.Accent
    val jalur = DesignColors.OutlineSoft
    fun dariY(y: Float, tinggi: Float): Float =
        ((1f - (y / tinggi).coerceIn(0f, 1f)) * 2f * EQ_MAKS_DB - EQ_MAKS_DB).let { (it * 2).roundToInt() / 2f }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(nilai.roundToInt().toString(), color = DesignColors.InkMuted, fontSize = 10.sp)
        Box(
            Modifier
                .width(26.dp)
                .height(150.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        onUbah(dariY(change.position.y, size.height.toFloat()))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onUbah(dariY(offset.y, size.height.toFloat())) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val lebar = 6.dp.toPx()
                val x = (size.width - lebar) / 2f
                drawRoundRect(jalur, Offset(x, 0f), Size(lebar, size.height), CornerRadius(lebar / 2))
                val tengah = size.height / 2f
                val y = (1f - (nilai.coerceIn(-EQ_MAKS_DB, EQ_MAKS_DB) + EQ_MAKS_DB) / (2f * EQ_MAKS_DB)) * size.height
                val atas = minOf(y, tengah)
                val tinggiIsi = kotlin.math.abs(y - tengah)
                drawRoundRect(aksen, Offset(x, atas), Size(lebar, tinggiIsi.coerceAtLeast(2f)), CornerRadius(lebar / 2))
                drawCircle(aksen, radius = 9.dp.toPx(), center = Offset(size.width / 2f, y))
            }
        }
        Text(label, color = DesignColors.InkMuted, fontSize = 10.sp)
    }
}

private fun persen(v: Float): String = "${(v * 100).roundToInt()}%"

/** Satu angka desimal tanpa String.format (tidak ada di common). */
private fun satuDesimal(v: Float): String {
    val x = (v * 10).roundToInt()
    val tanda = if (x < 0) "-" else ""
    return "$tanda${kotlin.math.abs(x) / 10}.${kotlin.math.abs(x) % 10}"
}
