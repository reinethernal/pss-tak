# PSS TAK

Полевой TAK-клиент для ПСР на базе [OmniTAK-Android](https://github.com/engindearing-projects/OmniTAK-Android) (Apache 2.0).

Сервер при первом запуске **не зашит**. Подключение — персональная ссылка штаба (enrollment QR / `tak://`).

Контекст сервиса ПСР и список отличий от OmniTAK: [../../README.md](../../README.md), [../../SOURCES.md](../../SOURCES.md).

## Сборка

APK собирает **только GitHub Actions** (workflow `CI` в корне монорепо).  
Локальный `./gradlew` для публикации не используется.

## CI

На **каждый push** GitHub Actions:

1. Собирает `assemblePsrDebug`
2. Кладёт APK в **Artifacts** (14 дней)
3. На `main` публикует F-Droid на GitHub Pages

Ручной запуск: Actions → CI → Run workflow.


## F-Droid

Официальный f-droid.org потребует FOSS flavor без `play-services-location` / ML Kit — см. `docs/FDROID.md`.
Свой F-Droid-репозиторий на сервере ПСР можно подключить отдельно.

## Лицензия

Apache License 2.0 (как upstream OmniTAK) + правки ПСР.
