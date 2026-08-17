# Источники и отличия от оригиналов

Все клиенты здесь — **форки / производные** открытых проектов.  
Цель правок: клиенты **без зашитого хоста**; подключение через персональную ссылку штаба (`tak://` / `opentakicu://`).

## Таблица upstream

| Каталог | Что собираем | Upstream | Лицензия |
|---------|--------------|----------|----------|
| [`apps/pss-tak`](apps/pss-tak/) | APK **PSS TAK** | [engindearing-projects/OmniTAK-Android](https://github.com/engindearing-projects/OmniTAK-Android) | Apache-2.0 |
| [`apps/opentak-icu`](apps/opentak-icu/) | APK **OpenTAK ICU** | [brian7704/OpenTAK_ICU](https://github.com/brian7704/OpenTAK_ICU) | см. LICENSE в каталоге |
| [`packages/atak-config`](packages/atak-config/) | ZIP настроек ATAK CIV (не APK) | скрипт под OTS; клиент — [TAK-Product-Center/atak-civ](https://github.com/TAK-Product-Center/atak-civ) (APK CIV в CI **не** собирается) | TAK / upstream atak-civ |

---

## PSS TAK ← OmniTAK-Android

**Зачем:** лёгкий полевой TAK-клиент (не ATAK), уже смотрящий на сервер ПСР.

### Изменения относительно upstream

| Изменение | Зачем |
|-----------|--------|
| Имя приложения **«PSS TAK»**, описание под ПСР (`app_assets/.../strings.xml`) | Отличать сборку от стокового OmniTAK |
| При пустом списке серверов ничего не добавляется — подключение через invite (`tak://…/enroll?host=…`) | APK можно раздавать публично; сервер задаёт штаб ссылкой |
| CI / monorepo layout (`apps/pss-tak`), артефакты APK | Сборка вместе с ICU и публикация в свой F-Droid |

Функционально это тот же OmniTAK (карта, CoT, мессенджер, Meshtastic и т.д.) — без претензии заменить ATAK CIV по возможностям.

---

## OpenTAK ICU ← OpenTAK_ICU

**Зачем:** стрим видео/аудио с телефона на MediaMTX ПСР и отправка CoT на OpenTAKServer.

### Изменения относительно upstream

| Изменение | Зачем |
|-----------|--------|
| Хост в Preferences / XML **пустой** по умолчанию | Не привязывать APK к одному OTS |
| **`InviteConfig`**: `opentakicu://import` пишет RTSP/CoT и качает CA по `truststore_url` | Ссылка штаба (или кнопка из ПСР TAK) настраивает приложение |
| `PsrServerDefaults` больше не заполняет хост | Нет baked-in сервера |
| **`truststore-root.p12` в git не коммитится** (только на build-хосте / в assets при локальной сборке с CA) | Не светить CA в публичном репозитории |
| Убраны Firebase / Crashlytics / `google-services.json` | Секрет Google API key утёк в git; аналитика для ПСР не нужна; CI собирается без файла |
| Явные `versionCode` / `versionName` вместо git-tag плагина app-versioning | Плагин ломался в monorepo и отдавал пустой versionCode (ломало F-Droid) |
| Опциональный `keystore.properties` | Debug-сборка в CI без release-подписи |

Поведение стриминга (кодеки, RTSP/RTMP/SRT и т.д.) — как у upstream OpenTAK ICU.

---

## packages/atak-config ← не форк APK

**Зачем:** официальный **ATAK CIV** — основной «тяжёлый» клиент ПСР; в этом репо только **пакет настроек**.

| Что сделано | Зачем |
|-------------|--------|
| `build-atak-datapackage.sh` → `psr-atak-config.zip` (config.pref + truststore под OTS) | Импорт в ATAK → enrollment на `fts.plasmadancer.ru` |
| `build-field-kit.sh` → комплект с инструкцией | Единый старт для полевых |
| APK ATAK CIV **не** собирается в CI | Нужны NDK r12b / Conan; брать CIV с tak.gov / Play |

---

## Инфраструктура репозитория (нет в upstream-приложениях)

- Monorepo + GitHub Actions: сборка обоих APK на каждый push  
- Self-hosted F-Droid на GitHub Pages (`fdroid/`, QR на лендинге)  
- Документация: этот файл, корневой [README.md](README.md)

## Что CI собирает при каждом push

1. `apps/pss-tak` → `assembleDebug`  
2. `apps/opentak-icu` → `assembleDebug`  

На `main` — ещё `fdroid update` → Pages.

## Локальные зеркала на build-хосте ПСР

- `/opt/psr-client-build/OmniTAK-Android`
- `/opt/psr-client-build/OpenTAK_ICU`
- `/opt/psr-client-build/atak-civ`
