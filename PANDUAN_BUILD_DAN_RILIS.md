# Panduan Build dan Rilis Cute Fetcher

Repo: https://github.com/Gandzar/CuteFetcher

> Rekomendasi: jadikan repo **publik**. Repo publik memakai GitHub Actions tanpa batas menit, sedangkan repo privat
> di akun Free dibatasi kuota menit bulanan (cek angka terbarunya di halaman Billing GitHub). Build pertama berat
> karena ffmpeg dikompilasi untuk 4 arsitektur.

## 1. Buat keystore (sekali seumur proyek)
Keystore = kunci tanda tangan APK. **Semua rilis harus memakai keystore yang sama**, kalau tidak, Android menolak update.

Di Termux (HP) atau terminal komputer yang punya Java:

```
pkg install openjdk-17        # hanya di Termux
keytool -genkeypair -v -keystore release.jks -alias cutefetcher \
  -keyalg RSA -keysize 4096 -validity 10000
```
Isi password (catat!), nama, dll. Lalu ubah ke base64 satu baris:

```
base64 -w0 release.jks > release.jks.b64
```

**Cadangkan `release.jks` dan passwordnya** di tempat aman (mis. password manager atau penyimpanan pribadi).
Kalau hilang, pengguna tidak bisa memperbarui aplikasi, harus pasang ulang. Jangan upload ke repo.

## 2. Isi GitHub Secrets
> Secrets milik **tiap repo**: kalau repo pindah akun, isi ulang di repo baru (Gandzar/CuteFetcher).

Repo → Settings → Secrets and variables → Actions → New repository secret. Buat 4 secret:

| Nama | Isi |
|---|---|
| CUTE_KEYSTORE_BASE64 | seluruh isi file `release.jks.b64` |
| CUTE_KEYSTORE_PASSWORD | password keystore |
| CUTE_KEY_ALIAS | `cutefetcher` |
| CUTE_KEY_PASSWORD | password key (sama dengan keystore jika kamu tekan Enter saat ditanya) |

## 3. Unggah proyek ke repo
Isi zip harus berada di **akar repo** (folder `.github`, `androidApp`, `settings.gradle.kts` sejajar).
Unggah lewat web tidak cocok (ribuan berkas, tidak mengekstrak zip). Pakai git, mis. di Termux:

```
pkg install git unzip
unzip CuteFetcher-tahap9.zip
cd CuteFetcher
git init -b main
git add .
git commit -m "Cute Fetcher: kerangka awal"
git remote add origin https://github.com/Gandzar/CuteFetcher.git
git push -u origin main
```
Saat diminta password, pakai **Personal Access Token** (GitHub → Settings → Developer settings → Tokens, izin `repo`).
Jika repo sudah punya isi, tambahkan `--force` hanya bila kamu yakin boleh menimpanya.

## 4. Jalankan build
Tab **Actions** → workflow **build** → **Run workflow** (atau otomatis berjalan setelah push).
- Run pertama lama (ffmpeg 4 arsitektur). Run berikutnya lebih cepat karena cache.
- Hasil: artifact **cute-fetcher-apk** di bawah halaman run (APK per arsitektur; kebanyakan HP memakai `arm64-v8a`).

## 5. Jika ada langkah merah
Buka langkah yang merah dan salin **baris-baris error pertama** (cari awalan `e:` untuk Kotlin atau `FAILED`/`What went wrong`).
Kirim ke saya; saya perbaiki sekaligus supaya run berikutnya hemat.
Jangan menjalankan ulang berkali-kali sebelum errornya dibaca.

## 6. Membuat rilis (agar update dalam aplikasi bekerja)
```
git tag v0.1.0
git push origin v0.1.0
```
Workflow membuat **rilis draf** berisi APK dan `SHA256SUMS.txt`. Buka Releases → draf → **Publish release**.
Updater hanya melihat rilis yang sudah dipublikasikan. Format tag wajib `vMAJOR.MINOR.PATCH`.
Sebelum rilis berikutnya, naikkan `ymusic.versionName` di `gradle.properties` agar sesuai dengan tag.

## 7. Memasang
Unduh APK dari Releases → buka di HP → izinkan "Pasang aplikasi tidak dikenal" jika diminta.
