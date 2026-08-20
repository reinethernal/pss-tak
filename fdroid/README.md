# Свой F-Droid репозиторий ПСР

Публикуется на GitHub Pages:

- Сайт: https://reinethernal.github.io/pss-tak/
- Repo URL: `https://reinethernal.github.io/pss-tak/fdroid/repo?fingerprint=061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`

Раздаёт клиенты для OpenTAKServer ПСР. Контекст сервиса — в корневом [README.md](../README.md).

## Клиенты

| packageId | Приложение |
|-----------|------------|
| `ru.plasmadancer.psr.tak` | **ПСР TAK** |
| `ru.plasmadancer.psr.icu` | **ПСР Видео** |

Оба APK подписаны **одним upload-ключом** (тот же сертификат, что APK на `fts.plasmadancer.ru/downloads`).

CI: `assemblePsrDebug` → `fdroid update --delete-unknown` → GitHub Pages.

В `repo/` не должно быть ничего кроме APK (никаких `.gitkeep`): F-Droid индексирует посторонние файлы как фейковые пакеты и клиент **не обновляет репозиторий**.
