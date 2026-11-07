package io.ymusic.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.settings.AppSettings
import io.ymusic.settings.flow
import io.ymusic.settings.put
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.tab_galeri
import io.ymusic.ui.resources.tab_mixer
import io.ymusic.ui.resources.tab_pengaturan
import io.ymusic.ui.resources.tab_pengunduh
import io.ymusic.ui.resources.tab_pustaka
import io.ymusic.ui.resources.transisi_halus
import io.ymusic.ui.resources.transisi_judul
import io.ymusic.ui.resources.transisi_kedalaman
import io.ymusic.ui.resources.transisi_kubus
import io.ymusic.ui.resources.transisi_parallax
import io.ymusic.ui.resources.transisi_zoom
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** Gaya transisi geser antar-tab. [id] disimpan di pengaturan: stabil, jangan dipakai ulang. */
enum class GayaTransisiTab(val id: Int, val label: StringResource) {
    HALUS(1, Res.string.transisi_halus),
    KEDALAMAN_3D(2, Res.string.transisi_kedalaman),
    ZOOM(3, Res.string.transisi_zoom),
    PARALLAX(4, Res.string.transisi_parallax),
    KUBUS_3D(5, Res.string.transisi_kubus),
    ;

    companion object {
        fun dariId(id: Int): GayaTransisiTab = entries.firstOrNull { it.id == id } ?: KEDALAMAN_3D
    }
}

private enum class TabCute(val label: StringResource, val ikon: ImageVector) {
    PENGUNDUH(Res.string.tab_pengunduh, Icons.Rounded.Download),
    PUSTAKA(Res.string.tab_pustaka, Icons.Rounded.LibraryMusic),
    MIXER(Res.string.tab_mixer, Icons.Rounded.GraphicEq),
    GALERI(Res.string.tab_galeri, Icons.Rounded.PhotoLibrary),
    PENGATURAN(Res.string.tab_pengaturan, Icons.Rounded.Settings),
}

/**
 * Layar utama Cute Fetcher: lima tab dalam satu slider (Pengunduh, Pustaka, Mixer, Galeri, Pengaturan),
 * dengan lima gaya transisi yang bisa dipilih di tab Pengaturan. Tab memakai ulang layar YMusic yang
 * sudah ada (Pustaka, Galeri unduhan, Pengaturan) dan layar baru (Pengunduh, Mixer).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CuteTabsScreen(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    signedIn: Boolean,
    onLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gayaId by remember(graph) { graph.settings.flow(AppSettings.gayaTransisiTab) }.collectAsState()
    val gaya = GayaTransisiTab.dariId(gayaId)
    val tabAwal = remember(graph) { graph.settings.flow(AppSettings.tabTerakhir).value.coerceIn(0, TabCute.entries.lastIndex) }
    val pager = rememberPagerState(initialPage = tabAwal) { TabCute.entries.size }

    // Ingat tab terakhir (lewati nilai awal agar tidak menulis saat pertama kali tampil).
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.drop(1).collect { graph.settings.put(AppSettings.tabTerakhir, it) }
    }

    Column(Modifier.fillMaxSize().background(DesignColors.Surface)) {
        // Layar di dalam pager berada di atas bilah tab, jadi tidak perlu ruang ekstra untuk mini-player.
        CompositionLocalProvider(LocalMiniPlayerInset provides 0.dp) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
                key = { it },
            ) { halaman ->
                Box(Modifier.fillMaxSize().transisiHalaman(gaya, pager, halaman)) {
                    when (TabCute.entries[halaman]) {
                        TabCute.PENGUNDUH -> PengunduhScreen(pengelola = graph.pengelolaScraper, mesinUnduh = graph.mesinUnduh)
                        TabCute.PUSTAKA -> LibraryScreen(graph, coordinator)
                        TabCute.MIXER -> MixerScreen(graph.pengendaliMixer)
                        TabCute.GALERI -> DownloadsScreen(graph, coordinator)
                        TabCute.PENGATURAN -> Column(Modifier.fillMaxSize()) {
                            PemilihGayaTransisi(gaya) { baru -> scope.launch { graph.settings.put(AppSettings.gayaTransisiTab, baru.id) } }
                            Box(Modifier.weight(1f)) { SettingsScreen(graph, signedIn = signedIn, onLogin = onLogin) }
                        }
                    }
                }
            }
        }
        BilahTab(
            terpilih = pager.currentPage,
            onPilih = { i -> scope.launch { pager.animateScrollToPage(i) } },
            modifier = Modifier.padding(bottom = LocalMiniPlayerInset.current),
        )
    }
}

/** Efek transisi per halaman. `offset` = jarak halaman dari posisi tengah (0 = di tengah, ±1 = tetangga). */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.transisiHalaman(gaya: GayaTransisiTab, pager: PagerState, halaman: Int): Modifier =
    graphicsLayer {
        // Jarak halaman dari tengah memakai properti stabil: (halaman - tengah) - pecahan geser.
        val offset = (halaman - pager.currentPage) - pager.currentPageOffsetFraction
        val jarak = abs(offset).coerceIn(0f, 1f)
        when (gaya) {
            GayaTransisiTab.HALUS -> Unit

            GayaTransisiTab.KEDALAMAN_3D -> if (offset > 0f && offset <= 1f) {
                // Halaman yang masuk dari kanan muncul "di bawah" halaman aktif, sambil membesar.
                alpha = 1f - offset
                translationX = size.width * -offset
                val skala = 0.75f + 0.25f * (1f - offset)
                scaleX = skala
                scaleY = skala
            }

            GayaTransisiTab.ZOOM -> {
                val skala = (1f - 0.15f * jarak).coerceAtLeast(0.85f)
                scaleX = skala
                scaleY = skala
                alpha = 0.5f + (skala - 0.85f) / 0.15f * 0.5f
            }

            GayaTransisiTab.PARALLAX -> {
                translationX = size.width * -offset * 0.35f
                alpha = 1f - jarak * 0.4f
            }

            GayaTransisiTab.KUBUS_3D -> {
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin(if (offset < 0f) 1f else 0f, 0.5f)
                rotationY = -90f * offset.coerceIn(-1f, 1f)
                alpha = 1f - jarak * 0.3f
            }
        }
    }

@Composable
private fun PemilihGayaTransisi(terpilih: GayaTransisiTab, onPilih: (GayaTransisiTab) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = DesignSpacing.Space8)) {
        Text(
            stringResource(Res.string.transisi_judul),
            color = DesignColors.InkSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = DesignSpacing.MarginCompact),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = DesignSpacing.MarginCompact, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(GayaTransisiTab.entries, key = { it.id }) { g ->
                val aktif = g == terpilih
                val bentuk = RoundedCornerShape(50)
                Text(
                    stringResource(g.label),
                    color = DesignColors.InkStrong,
                    fontSize = 13.sp,
                    fontWeight = if (aktif) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(bentuk)
                        .background(if (aktif) DesignColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (aktif) DesignColors.Accent else DesignColors.Outline, bentuk)
                        .clickable { onPilih(g) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BilahTab(terpilih: Int, onPilih: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(DesignColors.SurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabCute.entries.forEachIndexed { i, tab ->
            val aktif = i == terpilih
            val warna = if (aktif) DesignColors.Accent else DesignColors.InkMuted
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (aktif) DesignColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onPilih(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(tab.ikon, contentDescription = null, tint = warna, modifier = Modifier.size(22.dp))
                Text(stringResource(tab.label), color = warna, fontSize = 11.sp, fontWeight = if (aktif) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
