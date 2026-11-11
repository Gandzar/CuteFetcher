# Peta Kode Cute Fetcher (supaya mudah dipahami)

Kode buatan sendiri memakai nama dan komentar **bahasa Indonesia**. Kode bawaan YMusic (paket `io.ymusic.*`),
Nimiyo, dan Scrapr **tidak diubah namanya** agar sumber dan lisensinya jelas dan agar tidak merusak ratusan berkas.

## Alur singkat: dari tempel tautan sampai berkas tersimpan
```
[Tab Pengunduh]  PengunduhScreen.kt
      |  tempel tautan, tekan Analisis
      v
PengelolaScraper (pengambil_media/PengelolaScraper.kt)  -> validasi URL, deteksi platform
      v
MesinWebViewNimiyo (androidApp/.../mesin/)  -> menjalankan JavaScript di WebView tersembunyi
      v
assets/scraper/host.js  -> mencoba SETIAP server bergantian (Nimiyo + Scrapr), dibatasi waktu
      v  (HTTP lewat OkHttp, cookie disimpan JarKukiMemori)
HasilMedia (judul, thumbnail, daftar format)  -> kartu hasil di layar
      v  tekan Unduh
MesinUnduhAndroid (androidApp/.../layanan_unduh/) -> unduh, jeda/lanjut, simpan ke MediaStore
```

## Folder buatan sendiri
| Folder / file | Isi |
|---|---|
| `pengambil_media/` | Inti scraper yang murni Kotlin: `ModelMedia.kt` (bentuk data), `ValidasiUrl.kt`, `PengelolaScraper.kt` (urutan mesin + fallback), `PemetaHasilNimiyo.kt` (JSON -> model), `MesinUnduh.kt` (kontrak unduhan), `JurnalDebug.kt` (log), `Diagnosa.kt` (alat uji) |
| `pembaruan_aplikasi/` | `PenguraiRilisGitHub.kt`: membaca rilis GitHub -> info pembaruan |
| `player/.../pengolah_audio/` | Mixer DSP/8D dari Musikku: `dsp/` (mesin suara), `data/` (pengaturan, preset), `MixerProcessor.kt` (sambungan ke ExoPlayer), `PengolahAudio.kt` (pemasangan saat aplikasi mulai), `PengendaliMixer*.kt` (jembatan ke layar) |
| `mse/.../MseAudioHooks.kt` | Titik sambung agar mixer juga memproses streaming YouTube |
| `androidApp/.../dev/bagja/streamvortex/mesin/` | `MesinWebViewNimiyo.kt` (mesin ekstraksi), `JarKukiMemori.kt` |
| `androidApp/.../layanan_unduh/` | `MesinUnduhAndroid.kt`, `ServisUnduhCute.kt` (notifikasi latar) |
| `androidApp/.../pembaruan/` | `PemantauRilisGitHub.kt` |
| `androidApp/.../debug/` | `PenyimpanJurnal.kt` (simpan log + catat crash), `AksiLaporanAndroid.kt` (bagikan/email) |
| `androidApp/src/main/assets/scraper/` | `host.js` (pengatur server), `scrapers/` (server Nimiyo), `scrapers/scrapr.js` (server hasil port Scrapr), `resolver.js` |
| `commonUi/.../ui/` | Layar baru: `CuteTabsScreen.kt` (lima tab), `PengunduhScreen.kt`, `MixerScreen.kt`, `PanelDebugScreen.kt` |

## Dokumen penting
`PROJECT_STATUS.md` (kondisi sekarang), `KNOWN_ISSUES.md` (masalah yang diketahui), `TODO.md`, `MIGRATION_LOG.md`
(riwayat perubahan), `UJI_PERANGKAT.md` (daftar uji di HP), `NOTICE.md` (kredit dan lisensi).

## Kalau ada masalah, lihat ke mana
- Downloader gagal -> Pengaturan > **Panel debug** > Uji koneksi / Uji semua server.
- Efek suara tidak ada -> Panel debug, filter asal `MIXER`: harus ada "mixer dipasang" saat aplikasi mulai dan "[streaming] dikonfigurasi" saat memutar YouTube.
- Aplikasi crash -> buka aplikasi lagi, Panel debug > "Log sesi sebelumnya".
