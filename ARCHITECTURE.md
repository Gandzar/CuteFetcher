# ARCHITECTURE — Cute Fetcher

## Keputusan (jangan diubah tanpa alasan teknis)
1. Induk = YMusic V4 (KMP + Compose MP; Kotlin 2.4.0, AGP 9.2.1, Gradle 9.4.1, Media3 1.11.0). Versi toolchain mengikuti YMusic.
2. `namespace` kode tetap `io.ymusic.*` agar tidak merusak ratusan berkas; hanya `applicationId` yang berubah. Kode baru memakai `dev.bagja.streamvortex.*`.
3. UI = Compose; lima tab dengan HorizontalPager (setara ViewPager2). UI Downloader Nimiyo ditulis ulang di Compose, desain premium (CUTE CORE), bukan WebView.
4. DSP Musikku dipasang di `AudioFxProcessorChain` (DefaultAudioSink) pada `AndroidLocalPlayer`. Satu `MixerRepository` sebagai sumber status DSP. Jalur MSE (YouTube) menyusul; jika belum didukung, UI harus jujur menyatakan DSP nonaktif.
5. Scraper Scrapr berjalan lewat satu antarmuka `PengambilMedia` -> `MediaResult`. Runtime (QuickJS YMusic vs WebView) diputuskan setelah uji kecil di Tahap 3.
6. Ditunda: terabox, anime, comic, resolver bypass link.

## Modul baru yang direncanakan
`:pengolah_audio`, `:pengambil_media`, `:layanan_unduh` (jembatan ke `:download`), `:pengelola_pembaruan`.

## Alur data
URL -> PengelolaScraper (validasi, deteksi platform, scraper utama, fallback) -> MediaResult -> pilih format -> `:download` -> `:media-store` -> Pustaka/Galeri -> `:player` -> DSP -> output.
