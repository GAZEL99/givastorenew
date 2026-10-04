cd public
cd giva
python -m http.server 8080
apk add python3
apk update && apk upgrade
apk add   php   php-fpm   php-mysqli   php-pdo   php-pdo_mysql   php-curl   php-mbstring   php-openssl   php-json   php-xml   php-zip   php-fileinfo   php-session   nodejs   npm   git   curl   wget   unzip   nano   bash   nginx
cd givastore
pwd
cd ~
rm -rf .git
ls ~
cd ~/givastore
pwd
git init
git branch -M main
cat > .gitignore << 'EOF'
node_modules/
.DS_Store
*.log
EOF

git add .
git config --global user.email "gilangar940@gmail.com"
git config --global user.name "GAZEL99"
git commit -m "update givastore"
https://github.com/GAZEL99/givastore.git
https://github.com/GAZEL99/givastorenew
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -u origin main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -u origin main
cd /public/givastore
git remote -v
git push -u origin main
git push -u origin main
git push -u origin main
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git push
cd givastore
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git push
ls -la
git init
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
cd ~/givastore
git init
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
cd ~/givastore
git add vercel.json
git commit -m "Fix vercel.json: remove invalid handle filesystem entry"
git push origin main
cd ~/givastore
sed -i '/class="brand-mark"/d' index.html kontak.html pay.html testimoni.html cara-order.html lacak-pesanan.html templates/product-template.html product/*/index.html
grep -rl 'class="brand-mark"' *.html templates/*.html product/*/index.html
cat > ~/givastore/vercel.json << 'EOF'
{
  "rewrites": [
    { "source": "/pay", "destination": "/pay.html" },
    { "source": "/product/:slug", "destination": "/api/product-page?slug=:slug" },
    { "source": "/product/:slug/", "destination": "/api/product-page?slug=:slug" }
  ],
  "headers": [
    {
      "source": "/assets/(.*)",
      "headers": [{ "key": "Cache-Control", "value": "public, max-age=604800" }]
    },
    {
      "source": "/video/(.*)",
      "headers": [{ "key": "Cache-Control", "value": "public, max-age=2592000" }]
    },
    {
      "source": "/(.*)",
      "headers": [
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "X-Frame-Options", "value": "SAMEORIGIN" },
        { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" }
      ]
    }
  ]
}
EOF

cd ~/givastore
git add .
git commit -m "Hapus logo video G, fix 404 di /pay"
git push origin main
cd givastore
git add . && git commit -m "fix wa dobel" && git push origin main
git init
git add .
git commit -m "fix wa dobel + fix data email hilang"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
git init
git add .
git commit -m "fix wa dobel + fix data email hilang"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
cd givastorenew
pwd
cd givastore-admin-android
git remote add origin https://github.com/GAZEL99/GIVAHEAD.git
git push -u origin main
# 1. Masukkan semua file dan folder ke Git
git add -A
# 2. Buat commit perubahan
git commit -m "feat: tambah file proyek android native dan github workflow"
# 3. Upload (push) langsung ke GitHub
git push origin main
# 1. Pindahkan file dari subfolder ke root
mv givastore-admin-android/* .
mv givastore-admin-android/.* . 2>/dev/null || true
rm -rf givastore-admin-android
# 2. Simpan perubahan ke Git
git add -A
git commit -m "fix: pindahkan struktur proyek ke root repository"
# 3. Kirim ke GitHub
git push origin main
# 1. Masuk ke folder proyek Anda
cd ~/givastore-admin-android
# 2. Pindahkan folder .github dan file-file lainnya ke luar (root)
cp -r .github .. 2>/dev/null || true
cp -r * .. 2>/dev/null || true
# 3. Keluar ke folder utama repositori
cd ..
# 4. Hapus folder duplikat yang di dalam
rm -rf givastore-admin-android
# 5. Simpan dan kirim ke GitHub
git add -A
git commit -m "fix: pindahkan workflow ke root"
git push origin main
# 1. Perbaiki versi Gradle di workflow (.github/workflows/build-apk.yml)
cat << 'EOF' > .github/workflows/build-apk.yml
name: Build GivaStore Admin APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout Source Code
      uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Setup Gradle 8.4
      uses: gradle/actions/setup-gradle@v3
      with:
        gradle-version: '8.4'

    - name: Build Debug APK with Gradle
      run: gradle assembleDebug --stacktrace

    - name: Upload APK ke Download Artifact
      uses: actions/upload-artifact@v4
      with:
        name: GivaStore-Admin-Debug-APK
        path: app/build/outputs/apk/debug/*.apk
        retention-days: 7
EOF

# 2. Perbaiki AndroidManifest.xml (gunakan ikon standar OS agar tidak mencari file mipmap)
cat << 'EOF' > app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.givastore.admin">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="Giva Admin"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".MyFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

    </application>
</manifest>
EOF

# 3. Commit dan push perbaikan ke GitHub
git add -A
git commit -m "fix: kunci gradle ke versi 8.4 dan perbaiki manifest"
git push origin main
