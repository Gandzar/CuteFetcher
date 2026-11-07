# MIGRATION_LOG
## Tahap 1 (parsial)
- Dibuat: NOTICE.md, PROJECT_STATUS.md, ARCHITECTURE.md, MIGRATION_LOG.md, TODO.md, KNOWN_ISSUES.md, third_party/*
- Diubah: androidApp/build.gradle.kts (applicationId, versionCode, archivesName), gradle.properties (versionName), settings.gradle.kts (rootProject.name), androidApp strings app_name, AndroidManifest (hapus 2 filter autoVerify), commonUi AppLinks.website
- Dihapus: secrets.enc, encrypt.sh, decrypt.sh
## Tahap 1 (lanjutan)
- AppLinks: source/issue/terms/changelog -> github.com/Gandzar/CuteFetcher; telegram -> bagja.dev. feedbackEmail masih feedback@ymusic.io (menunggu email Cute Fetcher).
- Ditambah: .github/workflows/build.yml (ffmpeg 4 ABI + android + rilis draf pada tag v*).
- Dipindah: release.yml YMusic -> docs_referensi/YMusic_release.yml.txt (agar tidak terpicu tag release-*).
## Tahap 2 (UI Downloader + kontrak) — ditulis, BELUM dikompilasi
- Dibuat: modul `:pengambil_media` (ModelMedia.kt, ValidasiUrl.kt, PengelolaScraper.kt, MesinUnduh.kt, tes commonTest).
- Dibuat: commonUi/PengunduhScreen.kt (hero gradien, kotak masukan dengan bingkai animasi saat memuat, lencana platform, kartu hasil + chip format, antrean dengan progres/jeda/lanjut/batal).
- Diubah: settings.gradle.kts (include modul), commonUi/build.gradle.kts (api :pengambil_media), App.kt (AppSection.Pengunduh id 11 + cabang when + import), strings.xml (values, values-id, values-in; 29 kunci pengunduh_*), email umpan balik -> ganjarboy033@gmail.com.
- Nama fungsi pakai camelCase Indonesia (validasiUrl, deteksiPlatform), bukan snake_case, agar lolos ktlint/Spotless.
## Tahap 3 (mesin ekstraksi) — ditulis, BELUM dikompilasi/diuji
- Keputusan runtime: WebView + OkHttp (bukan QuickJS), karena 10/16 scraper memakai DOMParser.
- Dibuat: pengambil_media/PemetaHasilNimiyo.kt (+tes), FormatMedia.perluResolusi/dataMentah.
- Dibuat: androidApp/.../mesin/MesinWebViewNimiyo.kt; assets/scraper/** (disalin dari Musikku = scraper Nimiyo yang hilang dari ZIP Nimiyo; host.js: MusikkuNative->CuteNative, MusikkuHost->CuteHost).
- Diubah: AppGraph (pengelolaScraper, mesinUnduh), App.kt (layar memakai graph), YMusicApplication (properti lazy + argumen), androidApp/build.gradle.kts (:pengambil_media, androidx.webkit 1.12.1).
- Keamanan: WebView memblokir semua host selain aset lokal; jembatan HTTP hanya http/https; badan respons dibatasi 8 MB.
## Tahap 4-5 (pemutar + DSP) — ditulis, BELUM dikompilasi/diuji
- Tahap 4 (verifikasi pemutar apa adanya): dibaca saja; AndroidLocalPlayer memakai DefaultAudioSink + AudioFxProcessorChain; jalur YouTube lewat :mse punya sink sendiri.
- Dibuat: player/src/androidMain/kotlin/dev/bagja/streamvortex/pengolah_audio/ (dsp/*, data/*, MixerProcessor.kt) = port Musikku (package com.musikku -> dev.bagja.streamvortex.pengolah_audio).
- Perbaikan saat porting: onFlush(StreamMetadata) untuk Media3 1.11; MixerEngine.close() menghentikan thread reverb (sebelumnya bocor satu thread per konfigurasi); jalur 8D di-cache; bypass bit-per-bit; mixer default MATI (enabled=false) agar putaran biasa tidak berubah.
- Diubah: media3/AudioFxProcessor.kt (rantai = silence -> fx -> mixer), AndroidLocalPlayer.kt (mixerProcessor + MixerRepository.init).
- Keputusan: DSP ditaruh di modul :player (androidMain), bukan modul Gradle baru, untuk menghindari risiko konfigurasi AGP 9 tanpa bisa build.
## Tahap 6 (lima tab + mixer + pengaturan) — ditulis, BELUM dikompilasi/diuji
- Dibuat: player/commonMain/.../pengolah_audio/PengendaliMixer.kt (antarmuka + PengaturanMixer lintas-platform); player/androidMain/.../PengendaliMixerAndroid.kt (pemetaan ke MixerRepository).
- Dibuat: commonUi/MixerScreen.kt (saklar, kanvas bola suara dari data nyata mesin, preset, jalur 8D, 10 batang EQ yang bisa diseret, bass/reverb/echo, efek seru, reset).
- Dibuat: commonUi/CuteTabsScreen.kt (HorizontalPager 5 tab + bilah tab; 5 gaya transisi: halus, kedalaman 3D, zoom, parallax, kubus 3D; pemilih gaya di tab Pengaturan).
- Diubah: AppSettings (gayaTransisiTab, tabTerakhir), AppGraph (pengendaliMixer), YMusicApplication (pengendaliMixer), App.kt (AppSection.CuteTabs id 12, jadi bagian awal bawaan; ResumableSections), strings (values/id/in).
## Mixer di streaming YouTube + mixer lengkap (permintaan pengguna) — ditulis, BELUM dikompilasi/diuji
- Dibuat: mse/androidMain/.../MseAudioHooks.kt (pabrik prosesor); MseAudioOutput memasang prosesor lewat DefaultAudioSink.Builder.setAudioProcessors (satu instance per sink); AndroidLocalPlayer memasang pabrik `{ MixerProcessor() }`.
- Hasil: mixer yang sama memproses pemutar lokal (ExoPlayer) DAN streaming MSE (YouTube). Sumber >2 kanal dilewati (bypass).
- PengaturanMixer diperluas: gerakan (putar/bolak-balik/acak) + interval, Lissajous (frekuensi X/Z, fase), offset X/Z, penghalusan, volume utama; UI menampilkan filter (HP/LP), drive, kompresor, echo ms/feedback, ukuran ruang. Teks info mixer diperbarui.
## Tahap 7 (mesin unduh + layanan latar) — ditulis, BELUM dikompilasi/diuji
- Dibuat: androidApp/.../layanan_unduh/MesinUnduhAndroid.kt (maks 2 bersamaan, HTTP Range untuk jeda/lanjut, validasi Content-Length, tolak halaman text/*, terbit ke MediaStore Music/Movies/Pictures "CuteFetcher" pada Android 10+, folder unduhan aplikasi pada Android 9-), ServisUnduhCute.kt (foreground dataSync, berhenti sendiri saat antrean kosong).
- Diubah: AndroidManifest (service), androidApp strings (en/id/in), YMusicApplication (mesinNimiyo bersama, mesinUnduh -> AppGraph).
## Tahap 8 (update engine GitHub) — ditulis, BELUM dikompilasi/diuji
- Dibuat: modul `:pembaruan_aplikasi` (PenguraiRilisGitHub: tag->versionCode, pilih APK sesuai ABI, wajib URL github.com/<repo>/releases/download/, wajib sha256 valid; 7 tes), androidApp/.../pembaruan/PemantauRilisGitHub.kt (GET releases/latest, throttle 6 jam, sha256 dari `digest` atau SHA256SUMS.txt).
- Diubah: AndroidAppUpdates (sumber data = periksaSekarang, bukan RemoteConfigData; nama berkas CuteFetcher-*), YMusicApplication (pemantauRilis, cek otomatis 8 dtk setelah start), androidApp versionCode = major*10000+minor*100+patch (0.1.0 = 100), build.yml (SHA256SUMS.txt + rilis draf).
- Updater tetap memakai mesin YMusic: unduh Range, cek ukuran+sha256, PackageInstaller (OS menolak APK bertanda tangan beda / versionCode lebih rendah).
## Tahap 9 (audit statis + optimasi) — BELUM dikompilasi
- Audit statis (skrip): kurung seimbang, tidak ada impor tak terpakai, tidak ada identitas tanpa impor, kunci string ada, impor fungsi ekstensi Compose lengkap, AsyncImage/IntSetting/ekstensi settings cocok dengan kode asli.
- Optimasi: MixerProcessor tanpa alokasi per buffer; WebView scraper dibuang setelah 2 menit menganggur (dibuat ulang saat perlu); satu OkHttpClient bersama (scraper, unduhan, pembaruan); drawer tidak lagi menampilkan "Pengunduh" ganda (hanya di layar lima tab); offset transisi pager memakai properti stabil (tanpa getOffsetDistanceInPages).
- Repo dipindah ke akun Gandzar/CuteFetcher (limit akun lama). Semua tautan/REPO_RILIS diperbarui; pencocokan URL rilis kini tidak peka huruf besar-kecil.
