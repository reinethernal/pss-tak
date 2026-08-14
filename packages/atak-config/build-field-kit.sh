#!/usr/bin/env bash
# Единый полевой комплект ПСР: инструкция + пакет настроек ATAK под этот сервер.
set -euo pipefail
HOST="${OTS_HOST:-fts.plasmadancer.ru}"
WEB_DIR="${WEB_DIR:-/var/www/html/opentakserver/downloads}"
OUT_DIR="${OUT_DIR:-/opt/psr-client-build/out}"
KIT_DIR="${OUT_DIR}/psr-field-kit"
mkdir -p "$KIT_DIR"

# Ensure datapackage is fresh
/opt/psr-client-build/scripts/build-atak-datapackage.sh

cp -f "${WEB_DIR}/psr-atak-config.zip" "${KIT_DIR}/01-настройки-сервера.zip"
cp -f "${WEB_DIR}/psr-connect.txt" "${KIT_DIR}/порты-и-пароли.txt" 2>/dev/null || true

cat > "${KIT_DIR}/НАЧНИТЕ-ЗДЕСЬ.txt" <<EOF
ПСР — одно полевое приложение
=============================

Главное приложение: ATAK CIV (максимум возможностей).
Этот сервер уже прописан в файле настроек — не нужно вручную вбивать хост.

Шаг 1. Установите ATAK CIV
  • Google Play / tak.gov — «ATAK CIV» (гражданская версия)
  • Или получите APK у администратора ПСР

Шаг 2. Импортируйте настройки сервера (один раз)
  • Откройте файл: 01-настройки-сервера.zip
  • В ATAK: Import Manager → выберите этот zip
  • Подключитесь: логин и пароль от веб-кабинета
    https://${HOST}

Шаг 3. (По желанию) Живое видео
  • Установите плагин OpenTAK ICU из раздела «Приложения для телефонов»
    или файл OpenTAK_ICU-*.apk с сервера
  • Видео: ${HOST}:8554 (логин OTS)

Что умеет ATAK на этом сервере
  • карта и свои/чужие позиции
  • операции (Data Sync), точки, файлы, фото
  • чат операции (GeoChat)
  • тревоги, эвакуация, группы доступа

Запасной простой клиент (меньше функций): OmniTAK-PSR-debug.apk
  Сервер ПСР уже зашит; всё равно нужна регистрация сертификата (enroll)
  логином OTS.

Админ-панель: https://${HOST}
EOF

# Also publish standalone start guide
cp -f "${KIT_DIR}/НАЧНИТЕ-ЗДЕСЬ.txt" "${WEB_DIR}/НАЧНИТЕ-ЗДЕСЬ.txt"
cp -f "${KIT_DIR}/НАЧНИТЕ-ЗДЕСЬ.txt" "${WEB_DIR}/psr-start.txt"

ZIP="${OUT_DIR}/psr-field-kit.zip"
rm -f "$ZIP"
( cd "${KIT_DIR}" && zip -qr "$ZIP" . )
cp -f "$ZIP" "${WEB_DIR}/psr-field-kit.zip"
chown www-data:www-data "${WEB_DIR}/psr-field-kit.zip" "${WEB_DIR}/psr-start.txt" "${WEB_DIR}/НАЧНИТЕ-ЗДЕСЬ.txt" 2>/dev/null || true
chmod 644 "${WEB_DIR}/psr-field-kit.zip" "${WEB_DIR}/psr-start.txt" "${WEB_DIR}/НАЧНИТЕ-ЗДЕСЬ.txt" 2>/dev/null || true
echo "OK https://${HOST}/downloads/psr-field-kit.zip"
ls -lh "${WEB_DIR}/psr-field-kit.zip" "${WEB_DIR}/psr-start.txt"
