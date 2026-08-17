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

Рекомендуемый полевой клиент: **ПСР TAK** (лёгкий + нужный функционал ПСР).  
Подключение к серверу — **персональная ссылка штаба**, хост в APK не зашит.  
Живое видео: **ПСР Видео** (та же ссылка или кнопка «Стрим» из TAK).  
Тяжёлый HQ / плагины ATAK: **ATAK CIV** + zip из `packages/atak-config` (опция).

## Что внутри монорепо

| Путь | Что это | Зачем |
|------|---------|--------|
| [`apps/pss-tak`](apps/pss-tak/) | APK **ПСР TAK** (`ru.plasmadancer.psr.tak`) | Единый полевой клиент: карта/CoT/чат/задания/метки |
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

В репозитории **четыре** package id (два приложения × новый + legacy), чтобы F-Droid мог **обновить** уже установленные APK без переустановки:

| packageId | Назначение |
|-----------|------------|
| `ru.plasmadancer.psr.tak` | ПСР TAK — новые установки |
| `soy.engindearing.omnitak.mobile` | legacy TAK — обновление старого OmniTAK/PSS |
| `ru.plasmadancer.psr.icu` | ПСР Видео — новые установки |
| `io.opentakserver.opentakicu.debug` | legacy ICU — обновление старого OpenTAK ICU PSR |

Все APK подписаны **одним upload-ключом** (тот же сертификат, что на `fts.plasmadancer.ru/downloads`).  
Обновление возможно только при совпадении **package id + подпись + больший versionCode**.

## CI

На **каждый push** собираются debug APK (`assemblePsrDebug` + `assembleCompatDebug` для обоих приложений) → Artifacts.  
На push в `main` дополнительно обновляется F-Droid на GitHub Pages (4 APK).  
CI требует секрет `PSR_UPLOAD_KEYSTORE_B64` (base64 от `~/.android/debug.keystore` build-сервера).

## Локальная сборка

```bash
cd apps/pss-tak && ./gradlew assemblePsrDebug assembleCompatDebug
cd apps/opentak-icu && ./gradlew assemblePsrDebug assembleCompatDebug
```

JDK 17, Android SDK platform 35.

## F-Droid.org

См. `apps/pss-tak/docs/FDROID.md`. Официальный каталог потребует FOSS flavor без Play Services.
