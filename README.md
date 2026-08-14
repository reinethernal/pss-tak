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

На push в `main` дополнительно обновляется **свой F-Droid** на GitHub Pages.

## Свой F-Droid

- Сайт: https://reinethernal.github.io/pss-tak/
- URL для F-Droid / Neo Store / Obtainium:

`https://reinethernal.github.io/pss-tak/fdroid/repo?fingerprint=061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`

- Fingerprint: `061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`
- Подробнее: [fdroid/README.md](fdroid/README.md)

## Локальная сборка

```bash
cd apps/pss-tak && ./gradlew assembleDebug
cd apps/opentak-icu && ./gradlew assembleDebug
```

JDK 17, Android SDK platform 35.

## F-Droid.org

См. `apps/pss-tak/docs/FDROID.md`. Официальный каталог потребует FOSS flavor без Play Services.
