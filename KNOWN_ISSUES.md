# KNOWN_ISSUES
1. **Build bergantung ffmpeg-build (diselesaikan lewat CI, belum terbukti):** `:androidApp` men-stage pack ffmpeg dari `../ffmpeg-build/out/<slug>/pack` (task syncFfmpegAndroid, FfmpegBuild.kt). Repo ffmpeg-build (ymusic-io/ffmpeg-build, ref d0a89492...) tidak ada di unggahan. Tanpa itu build Android gagal. Release workflow YMusic mem-build-nya per ABI di CI (lama).
2. Belum ada build/test yang dijalankan di sandbox ini (jaringan mati, tanpa Android SDK/NDK 28.2.13676358).
3. Mesin YouTube YMusic (SABR/MSE, engine.js remote) dapat berubah/rusak mengikuti perubahan YouTube; remote-config dan bundle-signing key kosong = hanya bundel bawaan.
4. Musikku DSP belum pernah dikompilasi (kata README-nya).
5. Nimiyo ZIP tidak memuat src/scrapers, src/utils, assets Android.
6. build.yml belum pernah dijalankan; langkah Uji unit diset continue-on-error sampai terbukti hijau.
7. Tanpa secret CUTE_KEYSTORE_BASE64/CUTE_KEYSTORE_PASSWORD/CUTE_KEY_ALIAS/CUTE_KEY_PASSWORD, APK rilis bertanda tangan debug.
9. `:download` YMusic khusus YouTube SABR (YtStreamDownloader, SabrSpecBuilder): tidak bisa mengunduh URL langsung hasil scraper. Perlu mesin unduh HTTP umum (kontrak `MesinUnduh` sudah ada; implementasi di :layanan_unduh, Tahap 7).
10. PengunduhScreen dipasang dengan pengelola=null dan mesinUnduh=null: layar menyatakan "mesin belum terhubung", belum ada ekstraksi/unduhan nyata sampai Tahap 3 dan 7.
11. Tombol "tempel dari papan klip" belum ada (membaca ClipEntry butuh kode per-platform). Tempel manual dan menu Bagikan Android (IncomingLinks) tetap bisa.
12. String baru hanya di values, values-id, values-in; locale lain jatuh ke bahasa Inggris. Perlu dicek apakah ada tes kelengkapan locale.
13. Seluruh kode Tahap 2 belum dikompilasi (tidak ada kotlinc/Gradle di sandbox).
14. Mesin ekstraksi memakai server pihak ketiga (SnapTik, ssstik, SnapSave, dll.) lewat scraper Nimiyo: rapuh, bisa berubah/mati/rate-limit kapan saja, dan berada di area syarat layanan platform. Belum pernah dijalankan nyata.
15. Scrapr (coflyn) TIDAK ikut dijalankan di perangkat: ia library Node (axios+cheerio, redirect/cookie jar, stream). Menjalankannya di WebView butuh bundel browser + shim axios berbasis OkHttp (maxRedirects, validateStatus, cookie) yang belum terbukti. Ditunda sampai mesin Nimiyo terbukti jalan. Kredit Coflyn tetap ada di NOTICE.md.
16. REDDIT dikenali di Kotlin tetapi tidak punya scraper di Nimiyo, jadi dinyatakan tidak didukung oleh mesin (bukan dipalsukan). Shopee ada di JS tapi diblokir di sisi Kotlin.
17. (SELESAI di Tahap 7: dipanggil MesinUnduhAndroid) `selesaikan()` (resolusi tautan semu *_resolve:) sudah ada di mesin tetapi belum dipanggil siapa pun sampai MesinUnduh (Tahap 7).
18. (DIPERBAIKI: mixer kini juga dipasang di jalur MSE lewat MseAudioHooks; belum diuji dengar.) Semula DSP hanya aktif di jalur pemutar lokal ExoPlayer. Jalur YouTube lewat :mse (MseAudioOutput membuat DefaultAudioSink sendiri) BELUM dipasangi mixer, jadi efek 8D tidak terdengar di sana sampai dikerjakan. UI harus menyatakan ini (Tahap 6).
19. Tidak ada UI mixer (tab 3) dan MixerRepository ada di modul Android-only; UI Compose Multiplatform perlu antarmuka/expect-actual (Tahap 6). Mixer saat ini tidak bisa dinyalakan pengguna.
20. Dua EQ 10-band: EQ native YMusic (di pengaturan) dan EQ mixer Musikku. Keduanya aktif bersamaan bila dua-duanya diatur; putuskan satu sumber di Tahap 6.
21. MixerSettings.tempo/pitchSemitones/volume dari Musikku belum dipetakan ke pemutar (YMusic punya kontrol kecepatan sendiri). Convolver reverb menambah latensi kecil; spektrum YMusic mungkin sedikit meleset.
22. Kode DSP belum pernah dikompilasi; algoritmanya (Biquad, konvolusi) hanya diuji di repo Musikku secara terpisah.
23. Tab: Pustaka = LibraryScreen YMusic, Galeri = DownloadsScreen YMusic (unduhan YouTube). Unduhan hasil scraper memakai antrean di tab Pengunduh sampai Tahap 7. Dialog hapus permanen di Pustaka belum diperiksa (bawaan YMusic).
24. (Kemungkinan aman: di halaman pertama pager tidak menyerap geser kanan sehingga drawer menerimanya; verifikasi di perangkat.) Gestur: pager lima tab belum menyediakan zona tepi untuk geser-buka drawer (SwipeTabSwitcher YMusic memakainya). Di tab pertama, geser dari tepi kiri bisa berebut dengan drawer. Perlu diuji di perangkat.
25. String Tahap 2/6 baru ada di locale en/id/in; ~50 locale lain jatuh ke bahasa Inggris. Nama preset/jalur mixer berbahasa Indonesia langsung dari data mesin (belum dilokalkan).
26. (getOffsetDistanceInPages sudah diganti formula stabil.) Sifat graphicsLayer (cameraDistance, transformOrigin) dipakai tanpa kompilasi; bila API berbeda di versi Compose proyek, perbaiki di CuteTabsScreen.kt.
27. (SELESAI Tahap 9: item Pengunduh ganda dihapus dari drawer.)
28. Bola suara hanya bergerak bila mixer aktif dan ada audio yang diputar (lokal atau YouTube).
29. Mixer di MSE: prosesor ditambahkan sebelum AudioTrack; latensi reverb/konvolusi tidak dikompensasi di jam A/V video YouTube (kemungkinan selisih kecil lip-sync). Perlu uji dengar. Suara >2 kanal tidak diproses.
30. Jalur khusus buatan sendiri (gambar jalur kustom) dan tempo/pitch dari Musikku tidak ada di UI; tempo/pitch memakai kontrol YMusic.
31. Antrean unduhan hanya di memori: tertutup paksa = antrean hilang (berkas sementara .part tersisa di cache, tak dilanjutkan otomatis). Berkas SELESAI tetap tersimpan di MediaStore.
32. Hasil unduhan scraper belum muncul di tab Galeri (itu milik unduhan YMusic); terlihat di antrean tab Pengunduh dan, untuk audio, di Pustaka setelah pemindaian MediaStore.
33. Android 9 ke bawah: berkas masuk folder khusus aplikasi (tidak tampil di galeri/Pustaka). Android 12+: layanan latar tidak bisa dimulai jika unduhan dimulai dari latar belakang.
34. Header unduhan hanya User-Agent; sebagian CDN dapat menolak tanpa Referer khusus. Belum diuji.
35. Updater hanya melihat rilis yang DIPUBLIKASIKAN (bukan draf/pra-rilis). Workflow membuat draf: publikasikan manual di GitHub. Tag harus `vMAJOR.MINOR.PATCH` (mis. v0.2.0) dan APK per-ABI memuat nama ABI di antara tanda "-" (mis. CuteFetcher-0.2.0-arm64-v8a-release.apk); bila AGP menamai lain, sesuaikan PenguraiRilisGitHub/archivesName.
36. Keamanan update: hash datang dari GitHub (asal yang sama dengan APK). Perlindungan sebenarnya = Android hanya memasang APK dengan tanda tangan sama. WAJIB memakai SATU keystore yang sama untuk semua rilis (secrets CUTE_KEYSTORE_*); APK debug-signed tidak bisa memperbarui APK release dan sebaliknya.
37. versionCode sekarang dari versionName; jangan menurunkan versi. Pembaruan dari build uji (versionCode lama = 1) ke rilis pertama berjalan normal karena kodenya lebih besar.
38. Server config YMusic dimatikan (REMOTE_CONFIG_URL kosong): pembaruan bundel mesin YouTube (player-js / engine dinamis) dari server mereka tidak ada. Bila YouTube berubah dan ekstraksi/streaming rusak, perbaikannya harus lewat rilis baru aplikasi.
39. Belum dilakukan sengaja (butuh build nyata untuk ukur): pengecilan APK (modul iOS/desktop tak dipakai, bahasa), profil startup, uji baterai WebView. Lakukan setelah build pertama hijau.
40. Hal yang TIDAK bisa diaudit tanpa kompilator: tipe/overload Kotlin, API Media3 (setAudioProcessors, onFlush(StreamMetadata)), resolusi Gradle. Itu tugas build pertama (Tahap 10).
41. (v0.2.0) Penyebab pasti kegagalan Downloader v0.1.0 belum diketahui; ditambahkan perbaikan yang masuk akal (cookie jar, galat jaringan jelas, redirect) + panel debug untuk menemukannya. Server pihak ketiga tetap bisa berubah/mati.
42. Server Scrapr yang butuh puppeteer (tiktok savetik/tikdownloader, facebook fdown, instagram snapinsta) TIDAK bisa diport ke HP; sengaja dilewati. Server Nimiyo di APK yang kamu kirim identik dengan yang sudah dipakai (tidak ada server baru di sana).
43. Port Scrapr (savetwt/spotmate/rapidsave) belum pernah dijalankan di perangkat; cek lewat Panel debug > Uji semua server.
44. Diagnosa: jika SEMUA server gagal dengan "jaringan gagal (...)", penyebabnya jaringan/SSL OkHttp; jika "HTTP 403/503" dengan cuplikan HTML Cloudflare, situs memblokir klien non-browser.
