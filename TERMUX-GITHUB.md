# Push ForktHubX MLBB ke GitHub dari Termux

## 1. Siapkan Termux

```bash
pkg update -y && pkg upgrade -y
pkg install git openssh unzip -y
termux-setup-storage
```

## 2. Ekstrak source

Sesuaikan lokasi ZIP jika berada di folder Download:

```bash
cd ~/storage/downloads
unzip yuyu-master.zip
cd yuyu-master
```

Jika nama folder hasil ekstrak berbeda, masuk ke folder yang berisi `pubspec.yaml`.

## 3. Atur identitas Git

```bash
git config --global user.name "SukitoVone"
git config --global user.email "EMAIL_GITHUB_KAMU"
```

Gunakan email yang terhubung ke akun GitHub.

## 4. Buat repository GitHub

Buat repository baru melalui github.com, misalnya `ForktHubX-MLBB`. Jangan centang opsi untuk membuat README, `.gitignore`, atau license karena source sudah memilikinya.

Kemudian jalankan:

```bash
git init
git add .
git commit -m "ForktHubX MLBB 5.2.0"
git branch -M main
git remote add origin https://github.com/USERNAME/ForktHubX-MLBB.git
git push -u origin main
```

Ganti `USERNAME` dengan username GitHub kamu. Saat diminta password HTTPS, gunakan GitHub Personal Access Token, bukan password akun.

Untuk update berikutnya:

```bash
git add .
git commit -m "Update ForktHubX MLBB"
git push
```

## 5. Build APK melalui GitHub

1. Buka repository GitHub.
2. Masuk ke tab **Actions**.
3. Pilih **Build & Release APK**.
4. Tekan **Run workflow** dan pilih branch `main`.
5. Tunggu workflow selesai berstatus hijau.
6. Buka hasil run dan unduh artifact `forkthubx-mlbb-apk`.
7. Ekstrak artifact ZIP untuk mendapatkan APK `ForktHubX-MLBB-5.2.0-arm64.apk`.

Workflow menjalankan `flutter analyze`, `flutter test`, lalu membangun APK release untuk ARM64.

## Catatan signing

Jika repository secrets signing tidak diisi, workflow menggunakan debug signing agar build tetap bisa diuji. APK tersebut tidak dapat menimpa instalasi lama yang ditandatangani dengan keystore berbeda. Untuk update tanpa uninstall, kamu harus memakai keystore release asli dan mengisi secrets `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, serta `ANDROID_KEY_PASSWORD` pada Settings → Secrets and variables → Actions.

Jangan pernah mengunggah file keystore atau password ke repository.
