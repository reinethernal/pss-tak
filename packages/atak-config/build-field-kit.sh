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

Фото на карте (улика / находка)
  • Quick Pic — быстрое фото → точка на карте с привязкой к месту
  • Или: метка → Attachments → фото → отправить
  • Так фото видно и на карте, и в файлах операции — не теряется в чате

Запасной простой клиент: PSS TAK (F-Droid / APK с сервера)
  Сервер ПСР уже зашит; нужна регистрация сертификата (enroll) логином OTS.
  Фото-метка: долгое нажатие на карте → Photo marker.
  Точки ПСР (русские названия): Tools → «ПСР точки» / «Точка ПСР».
  Секторы штаб рисует на основной карте: https://${HOST}/map
  Задания и состав выезда: /downloads/psr-operation.html?mission=ИМЯ
  В PSS TAK: Mission Sync → тап по миссии (задания + состав).

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
