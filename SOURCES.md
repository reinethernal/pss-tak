# Источники исходников (PSS TAK monorepo)

Все клиенты в этом репозитории — **форки / производные** открытых проектов с правками под OpenTAKServer ПСР (`fts.plasmadancer.ru`).

| Каталог | Что собираем | Upstream (откуда взято) | Лицензия upstream |
|---------|--------------|-------------------------|-------------------|
| [`apps/pss-tak`](apps/pss-tak/) | APK **PSS TAK** (основной лёгкий клиент) | [engindearing-projects/OmniTAK-Android](https://github.com/engindearing-projects/OmniTAK-Android) | Apache-2.0 |
| [`apps/opentak-icu`](apps/opentak-icu/) | APK **OpenTAK ICU** (живое видео) | [brian7704/OpenTAK_ICU](https://github.com/brian7704/OpenTAK_ICU) | см. LICENSE в каталоге |
| [`packages/atak-config`](packages/atak-config/) | ZIP настроек ATAK CIV (не APK) | генерируется скриптом под OTS; клиент — [TAK-Product-Center/atak-civ](https://github.com/TAK-Product-Center/atak-civ) (APK CIV в CI **не** собирается: NDK/Conan) | TAK / upstream atak-civ |

## Наши правки ПСР

- **PSS TAK (`apps/pss-tak`)**: при пустом списке серверов добавляется `fts.plasmadancer.ru:8089` TLS; отображаемое имя «PSS TAK».
- **OpenTAK ICU (`apps/opentak-icu`)**: defaults RTSP/CoT на `fts.plasmadancer.ru`, порт CoT `8089` SSL, класс `PsrServerDefaults`; CA `truststore-root.p12` в git **не** кладётся (только на сервере сборки).

## Что CI собирает при каждом push

1. `apps/pss-tak` → `assembleDebug`
2. `apps/opentak-icu` → `assembleDebug`

Артефакты APK загружаются в GitHub Actions Artifacts.

## Локальные зеркала на сервере ПСР

На build-хосте также лежат полные рабочие копии:

- `/opt/psr-client-build/OmniTAK-Android`
- `/opt/psr-client-build/OpenTAK_ICU`
- `/opt/psr-client-build/atak-civ`
