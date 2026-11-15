# Taksi — ishga tushirish

Tarkibi:
- `server/` — Node.js serveri (kutubxonasiz): telefon+kod bilan kirish, buyurtmalar, jonli joylashuv, naqd/karta to'lov holati, haydovchi statistikasi.
- `android/` — Kotlin (Jetpack Compose) ilovasi. Bitta ilovada ikki rol: **Yo'lovchi** va **Haydovchi**. Xarita — OpenStreetMap (osmdroid), API kalit shart emas.

## 1. Serverni ishga tushirish
```bash
cd server
node server.js            # http://localhost:3000
# tekshiruv (boshqa oynada):  PORT=3999 DB_FILE=/tmp/t.json node server.js  va  node test.js
```

## 2. APK yig'ish (kompyuterda)
1. Android Studio'ni o'rnating va `android/` papkasini oching (Open).
2. Sync tugagach: **Build → Build APK(s)**.
3. APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## 3. Telefonda sinash
- Emulyatorda server manzili `http://10.0.2.2:3000` (tayyor).
- Haqiqiy telefonda: kompyuter va telefon bitta Wi‑Fi'da bo'lsin, ilovadagi "Server manzili" ga `http://<kompyuter IP>:3000` yozing.
- Ikkita telefon oling: birida **Yo'lovchi**, ikkinchisida **Haydovchi** bilan kiring. Joylashuv ruxsatini bering.

## Xarita va joylashuv
- **Yo'lovchi:** xaritaga bosib "Qayerdan" va "Qayerga" nuqtalarini belgilaydi, yoki "Mening joyimdan", yoki manzil bo'yicha qidiradi. Haydovchi qabul qilgach xaritada haydovchini (🟠) jonli ko'radi.
- **Haydovchi:** ochiq buyurtmalar xaritada (🟢) va yo'lovchigacha masofa bilan ko'rinadi. Qabul qilgach yo'lovchining turgan joyini (🟠) ko'radi, "Navigatorda ochish" tugmasi bilan Google Maps/Yandex/2GIS'ni ochadi.
- Joylashuv ikki tomonga faqat qabul qilingandan safar tugaguncha ko'rinadi (server shuni ta'minlaydi).
- Emulyatorda joylashuvni Extended controls → Location orqali bering.

## To'lov
- **Naqd:** yo'lovchi tanlaydi; haydovchi "Yakunlash (naqd oldim)" ni bossa, buyurtma "to'langan" bo'ladi.
- **Karta:** yo'lovchi safar tugagach "Karta bilan to'lash" ni bosadi.
  **Hozir bu SINOV rejimi** (`PAY_DEV`): hech qanday pul yechilmaydi, to'lov shunchaki "to'landi" deb belgilanadi.
  Haqiqiy to'lov uchun `server.js` dagi `TODO` joyiga Payme / Click / Uzum Merchant API ulanadi (ular bilan shartnoma va merchant akkaunt kerak) va `PAY_DEV=0` bilan ishga tushiriladi. Karta raqamlari hech qachon bizning serverda saqlanmaydi — provayderning to'lov sahifasi orqali o'tadi.

## Ishlab chiqarishdan oldin qilinadigan ishlar
- **SMS:** `server.js` dagi `TODO` joyiga SMS provayder (Eskiz, Playmobile) ulang va `DEV_SMS=0` bilan ishga tushiring.
- **HTTPS:** serverni domen va sertifikat bilan joylang; `AndroidManifest.xml` dagi `usesCleartextTraffic` ni o'chiring.
- **Orqa fonda joylashuv:** hozir joylashuv ilova ochiq turganda yuboriladi. Haydovchi ekranni o'chirib qo'yganda ham yuborilishi uchun foreground service kerak.
- **Ma'lumotlar bazasi:** hozir `db.json` fayli; ko'p foydalanuvchi uchun PostgreSQL'ga o'ting.
- **Xarita qidiruvi:** manzil qidirish qurilmadagi Geocoder'ga tayanadi, ba'zi telefonlarda ishlamasligi mumkin — shunda xaritaga bosib belgilash ishlaydi.
- Shahar nomi `Api.kt` dagi `CITY`, narx (`BASE`, `PER_KM`, `MIN_FARE`) `server.js` ichida.
