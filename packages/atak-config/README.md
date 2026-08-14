# Конфиг ATAK под сервер ПСР (не APK)

Скрипты собирают data package (zip) для официального **ATAK CIV**.

## Upstream клиента

- Исходники ATAK CIV: https://github.com/TAK-Product-Center/atak-civ  
- Полная сборка APK ATAK в CI этого репозитория **не выполняется** (нужны NDK r12b, Conan и т.д.).

## Скрипты

- `build-atak-datapackage.sh` — `psr-atak-config.zip` (config.pref + truststore)
- `build-field-kit.sh` — комплект с инструкцией

Запускать на сервере OTS, где есть `/home/ots/ots/ca/`.
