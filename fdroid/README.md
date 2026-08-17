# Свой F-Droid репозиторий ПСР

Публикуется на GitHub Pages:

- Сайт: https://reinethernal.github.io/pss-tak/
- Repo URL: `https://reinethernal.github.io/pss-tak/fdroid/repo?fingerprint=061cac831dec49c4c6dfd7c49f2d6f075e2bbbe4ca623e7765ed99a9187609c8`

Раздаёт клиенты для OpenTAKServer ПСР (`fts.plasmadancer.ru`). Контекст сервиса и отличия от upstream — в корневом [README.md](../README.md) и [SOURCES.md](../SOURCES.md).

## Клиенты

| packageId | Приложение |
|-----------|------------|
| `ru.plasmadancer.psr.tak` | **ПСР TAK** — единый полевой клиент (карта, метки, задания, чат) |
| `ru.plasmadancer.psr.icu` | **ПСР Видео** — трансляция камеры/экрана на MediaMTX |

Старые package id (`soy.engindearing.omnitak.mobile`, `io.opentakserver.opentakicu.debug`) больше не публикуются: это отдельные приложения, обновление поверх старых APK не сработает — нужна новая установка из этого репозитория.

CI на каждый push собирает APK, обновляет индекс `fdroid update` и выкладывает на `gh-pages`.
