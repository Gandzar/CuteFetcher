# Daftar Uji di HP (Cute Fetcher v0.2.0)

Pasang APK `arm64-v8a` dari artifact `cute-fetcher-apk` (ekstrak zip-nya dulu). Catat hasil tiap nomor: OK / GAGAL + apa yang terjadi.

## A. Dasar (lakukan dulu)
1. Aplikasi terbuka tanpa crash. Layar awal = lima tab di bawah (Pengunduh, Pustaka, Mixer, Galeri, Pengaturan).
2. Geser antar tab jalan mulus. Di Pengaturan, coba kelima gaya transisi.
3. Dari tab pertama, geser dari tepi kiri: drawer terbuka? (kemungkinan konflik gestur)

## B. Pengunduh (butuh internet)
4. Tempel tautan TikTok publik -> muncul lencana "Terdeteksi: TikTok" -> tekan Analisis -> kartu hasil muncul (judul, thumbnail, format).
5. Ulangi dengan Instagram, YouTube, SoundCloud, Facebook, X. Catat platform mana yang gagal dan pesan gagalnya.
6. Pilih format -> Unduh -> muncul di antrean dengan progres + notifikasi.
7. Coba Jeda lalu Lanjutkan. Coba Batalkan. Matikan internet saat mengunduh lalu nyalakan lagi.
8. Setelah selesai: cek berkas di galeri HP (video/gambar) atau aplikasi musik (audio), folder "CuteFetcher".

## C. Mixer DSP / 8D (pakai headphone)
9. Putar lagu lokal. Tab Mixer -> nyalakan "Aktifkan efek" -> pilih preset. Terdengar berubah? Bola suara bergerak?
10. Matikan efek: suara kembali normal (tanpa desis/pecah).
11. Putar video/musik YouTube (streaming). Nyalakan efek: terdengar berubah? Video dan suara masih sinkron?
12. Geser EQ, bass, reverb, rotasi. Ada bunyi klik/pecah/lag?

## D. Lain-lain
13. Pustaka: lagu lokal terbaca, putar, mini-player jalan.
14. Pengaturan: buka "Periksa pembaruan" (belum ada rilis -> "sudah terbaru" atau gagal dengan pesan).
15. Tutup paksa aplikasi saat mengunduh -> buka lagi (antrean hilang adalah perilaku yang diketahui).
16. Pemakaian baterai/panas setelah 10 menit di tab Pengunduh.

## Yang dikirim ke saya
Daftar nomor yang GAGAL/aneh + tangkapan layar atau pesan error. Untuk crash: Pengaturan -> Umpan balik (bagikan log) bila tersedia.

## E. Panel debug (baru di v0.2.0)
17. Pengaturan -> tombol "Panel debug". Muncul log? Coba "Uji koneksi": mana yang merah?
18. Di Panel debug, tempel satu tautan TikTok -> "Uji semua server": catat tiap server OK/GAGAL beserta pesannya.
19. Tombol "Lapor ke pengembang": terbuka aplikasi email dengan log terlampir? (log juga otomatis disalin ke papan klip)
20. Setelah mencoba Analisis yang gagal, tekan "Lihat detail (panel debug)" di kartu galat.
21. Mixer: pilih jalur "Gambar sendiri", gambar bentuk dengan jari, nyalakan efek: bola mengikuti gambarmu?
22. Mixer + YouTube streaming: buka aplikasi baru (tutup paksa dulu), LANGSUNG putar YouTube tanpa memutar lagu lokal, nyalakan efek: terdengar?
