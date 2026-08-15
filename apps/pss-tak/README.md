# PSS TAK

Полевой TAK-клиент для ПСР на базе [OmniTAK-Android](https://github.com/engindearing-projects/OmniTAK-Android) (Apache 2.0).

Сервер по умолчанию при первом запуске: **fts.plasmadancer.ru:8089** (TLS). Нужна enroll логином OpenTAKServer.

Контекст сервиса ПСР и список отличий от OmniTAK: [../../README.md](../../README.md), [../../SOURCES.md](../../SOURCES.md).

## Сборка

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

JDK 17, Android SDK (platform 35), желательно NDK `28.2.13676358`.

## CI

На **каждый push** в любую ветку GitHub Actions (workflow `CI`):

1. Собирает debug APK  
2. Кладёт его в **Artifacts** (хранить 14 дней)  
3. Гоняет unit-тесты  

Ручной запуск: Actions → CI → Run workflow.  
Теги дополнительно публикуют APK в GitHub Release.


## F-Droid

Официальный f-droid.org потребует FOSS flavor без `play-services-location` / ML Kit — см. `docs/FDROID.md`.
Свой F-Droid-репозиторий на сервере ПСР можно подключить отдельно.

## Лицензия

Apache License 2.0 (как upstream OmniTAK) + правки ПСР.
