# PSS TAK

Монорепозиторий полевых Android-клиентов для ПСР / OpenTAKServer (`fts.plasmadancer.ru`).

**Откуда код:** см. [SOURCES.md](SOURCES.md).

## Приложения

| Путь | APK | Upstream |
|------|-----|----------|
| `apps/pss-tak` | PSS TAK | [OmniTAK-Android](https://github.com/engindearing-projects/OmniTAK-Android) |
| `apps/opentak-icu` | OpenTAK ICU | [OpenTAK_ICU](https://github.com/brian7704/OpenTAK_ICU) |
| `packages/atak-config` | ZIP для ATAK CIV | скрипт + [atak-civ](https://github.com/TAK-Product-Center/atak-civ) (APK CIV вне CI) |

## CI

На **каждый push** GitHub Actions собирает **оба** APK (`pss-tak` и `opentak-icu`) и кладёт их в Artifacts.

## Локальная сборка

```bash
# PSS TAK
cd apps/pss-tak && ./gradlew assembleDebug

# OpenTAK ICU
cd apps/opentak-icu && ./gradlew assembleDebug
```

JDK 17, Android SDK platform 35.

## F-Droid

См. `apps/pss-tak/docs/FDROID.md`. Официальный f-droid.org потребует FOSS flavor без Play Services.
