# PSS TAK / ПСР — клиенты

Полевые Android-клиенты и пакеты настроек для **системы ПСР** (поиск и спасение / situational awareness) на сервере OpenTAKServer:

**https://fts.plasmadancer.ru**

Этот репозиторий — не сам сервер, а **клиентская сторона**: готовые приложения и конфиги, уже привязанные к ПСР, плюс свой F-Droid для раздачи APK.

## Что это за сервис в целом

| Компонент | Роль |
|-----------|------|
| **OpenTAKServer** на `fts.plasmadancer.ru` | Сервер TAK: карта, контакты, миссии/операции, файлы, живое видео (MediaMTX), enrollment сертификатов |
| **Веб-UI** | Диспетчерская: обзор, карта, тревоги, операции (в т.ч. чат миссии), пользователи, приложения для телефонов |
| **Клиенты на телефоне** | Подключение полевых по SSL CoT `:8089`, enrollment `:8446`; видео — RTSP/RTSPS |
| **Этот GitHub-репозиторий** | Сборка и раздача клиентов ПСР (форки открытых проектов + datapackage для ATAK CIV) |

Рекомендуемый полевой клиент: **ПСР TAK** (карта/CoT/чат + встроенная трансляция).  
Подключение к серверу — **персональная ссылка штаба**, хост в APK не зашит.  
Тяжёлый HQ / плагины ATAK: **ATAK CIV** + zip из `packages/atak-config` (опция).

## Что внутри монорепо

| Путь | Что это | Зачем |
|------|---------|--------|
| [`apps/pss-tak`](apps/pss-tak/) | APK **ПСР TAK** (`ru.plasmadancer.psr.tak`) | Полевой клиент: карта/CoT/чат/задания + трансляция камеры |
| [`apps/opentak-icu`](apps/opentak-icu/) | APK **ПСР Видео** (`ru.plasmadancer.psr.icu`) | Стрим камеры/экрана на MediaMTX ПСР + CoT на OTS |
| [`packages/atak-config`](packages/atak-config/) | ZIP для **ATAK CIV** | Data package / field kit: хост, порты, truststore — без сборки самого ATAK |
| [`fdroid/`](fdroid/) | Свой F-Droid | Раздача обоих APK через GitHub Pages |

Откуда взяты оригиналы и **какие правки сделаны** — в [SOURCES.md](SOURCES.md).  
Для пользователей (что умеет сервис и когда пригодится) — [FEATURES.md](FEATURES.md).  
Планы расширения ПСР/SAR — [ROADMAP.md](ROADMAP.md).

## Свой F-Droid

- Сайт: https://reinethernal.github.io/pss-tak/
- URL для F-Droid / Neo Store / Obtainium:

`https://reinethernal.github.io/pss-tak/fdroid/repo?fingerprint=061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`

- Fingerprint: `061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`
- Подробнее: [fdroid/README.md](fdroid/README.md)

В репозитории два приложения:

| packageId | Назначение |
|-----------|------------|
| `ru.plasmadancer.psr.tak` | ПСР TAK |
| `ru.plasmadancer.psr.icu` | ПСР Видео |

Все APK подписаны **одним upload-ключом** (тот же сертификат, что на `fts.plasmadancer.ru/downloads`).  
Обновление возможно только при совпадении **package id + подпись + больший versionCode**.

## CI

**APK собираются только GitHub Actions** (`.github/workflows/ci.yml`). Локальный `./gradlew` / `scripts/build-*.sh` для публикации не используются.

На **каждый push** — `assemblePsrDebug` обоих приложений → Artifacts.  
На push в `main` — F-Droid на GitHub Pages (2 APK).  
Секрет `PSR_UPLOAD_KEYSTORE_B64` (тот же сертификат, что раньше на `fts.plasmadancer.ru/downloads`).

Готовые файлы: [Actions](https://github.com/reinethernal/pss-tak/actions) и F-Droid выше.  
На штабной `/downloads` APK копируются скриптом `scripts/sync-apks-from-ci.sh` (скачивает артефакты CI, Gradle не запускает).

## F-Droid.org

См. `apps/pss-tak/docs/FDROID.md`. Официальный каталог потребует FOSS flavor без Play Services.
