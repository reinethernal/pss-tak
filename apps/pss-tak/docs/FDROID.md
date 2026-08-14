# F-Droid

## Официальный каталог (f-droid.org)

Сейчас приложение **не готово** к включению as-is:

- `com.google.android.gms:play-services-location` — запрещено
- Google ML Kit barcode — обычно отклоняют

Нужен product flavor `foss`: GPS через Android LocationManager, QR через ZXing, без GMS.

После этого:

1. Fastlane/triple-t metadata в репозитории
2. MR в [fdroiddata](https://gitlab.com/fdroid/fdroiddata) с YAML recipe
3. Ревью мейнтейнерами F-Droid

## Свой репозиторий F-Droid

Можно раздавать APK из CI через `fdroidserver` на `https://fts.plasmadancer.ru/fdroid/` — пользователи добавляют URL в приложение F-Droid / Obtainium.

CI уже собирает APK на каждый push (см. `.github/workflows/build-apk.yml`).
