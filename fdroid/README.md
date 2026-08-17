# Свой F-Droid репозиторий ПСР

Публикуется на GitHub Pages:

- Сайт: https://reinethernal.github.io/pss-tak/
- Repo URL: `https://reinethernal.github.io/pss-tak/fdroid/repo?fingerprint=061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`

Раздаёт клиенты для OpenTAKServer ПСР. Контекст сервиса — в корневом [README.md](../README.md).

## Клиенты (4 package id — для обновления старых установок)

| packageId | Приложение |
|-----------|------------|
| `ru.plasmadancer.psr.tak` | **ПСР TAK** (новые установки) |
| `soy.engindearing.omnitak.mobile` | **PSR TAK legacy** — обновление поверх старого OmniTAK/PSS |
| `ru.plasmadancer.psr.icu` | **ПСР Видео** (новые установки) |
| `io.opentakserver.opentakicu.debug` | **ICU legacy** — обновление поверх старого OpenTAK ICU PSR |

Все четыре APK подписаны **одним upload-ключом** (тот же сертификат, что APK на `fts.plasmadancer.ru/downloads`).

CI: `assemblePsrDebug` + `assembleCompatDebug` → `fdroid update --delete-unknown` → GitHub Pages.
