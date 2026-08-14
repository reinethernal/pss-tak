#!/usr/bin/env bash
# Rebuild ATAK/iTAK config data package for current OTS (ПСР).
set -euo pipefail

HOST="${OTS_HOST:-fts.plasmadancer.ru}"
SSL_PORT="${OTS_SSL_PORT:-8089}"
ENROLL_PORT="${OTS_ENROLL_PORT:-8446}"
CA_PASS="${OTS_CA_PASSWORD:-atakatak}"
CA_DIR="${OTS_CA_DIR:-/home/ots/ots/ca}"
OUT_DIR="${OUT_DIR:-/opt/psr-client-build/out}"
WEB_DIR="${WEB_DIR:-/var/www/html/opentakserver/downloads}"
PKG_NAME="psr-atak-config"
DESC="ПСР / ${HOST}"

WORKDIR="${OUT_DIR}/${PKG_NAME}"
rm -rf "${WORKDIR}"
mkdir -p "${WORKDIR}/cert" "${WORKDIR}/MANIFEST"

TRUSTSTORE="${CA_DIR}/truststore-root.p12"
CA_PEM="${CA_DIR}/ca.pem"
[[ -f "${TRUSTSTORE}" ]] || { echo "missing ${TRUSTSTORE}"; exit 1; }
cp -f "${TRUSTSTORE}" "${WORKDIR}/cert/truststore-root.p12"
cp -f "${CA_PEM}" "${WORKDIR}/cert/ca.pem"

# Soft-cert style: connect + enroll with trust (user/password at connect time)
cat > "${WORKDIR}/config.pref" <<EOF
<?xml version='1.0' encoding='ASCII' standalone='yes'?>
<preferences>
  <preference version="1" name="cot_streams">
    <entry key="count" class="class java.lang.Integer">1</entry>
    <entry key="description0" class="class java.lang.String">${DESC}</entry>
    <entry key="enabled0" class="class java.lang.Boolean">true</entry>
    <entry key="connectString0" class="class java.lang.String">${HOST}:${SSL_PORT}:ssl</entry>
    <entry key="enrollForCertificateWithTrust0" class="class java.lang.Boolean">true</entry>
    <entry key="useAuth0" class="class java.lang.Boolean">true</entry>
    <entry key="cacheCreds0" class="class java.lang.String">Cache credentials</entry>
    <entry key="caLocation0" class="class java.lang.String">cert/truststore-root.p12</entry>
    <entry key="caPassword0" class="class java.lang.String">${CA_PASS}</entry>
  </preference>
  <preference version="1" name="com.atakmap.app_preferences">
    <entry key="displayServerConnectionWidget" class="class java.lang.Boolean">true</entry>
    <entry key="caLocation" class="class java.lang.String">cert/truststore-root.p12</entry>
    <entry key="caPassword" class="class java.lang.String">${CA_PASS}</entry>
    <entry key="locationCallsign" class="class java.lang.String">ПСР</entry>
    <entry key="locationTeam" class="class java.lang.String">Cyan</entry>
    <entry key="atakRoleType" class="class java.lang.String">Team Member</entry>
  </preference>
</preferences>
EOF

UID_PKG="$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"
cat > "${WORKDIR}/MANIFEST/manifest.xml" <<EOF
<MissionPackageManifest version="2">
  <Configuration>
    <Parameter name="uid" value="${UID_PKG}"/>
    <Parameter name="name" value="${PKG_NAME}"/>
    <Parameter name="onReceiveDelete" value="false"/>
  </Configuration>
  <Contents>
    <Content ignore="false" zipEntry="config.pref"/>
    <Content ignore="false" zipEntry="cert/truststore-root.p12"/>
    <Content ignore="false" zipEntry="cert/ca.pem"/>
  </Contents>
</MissionPackageManifest>
EOF

ZIP="${OUT_DIR}/${PKG_NAME}.zip"
rm -f "${ZIP}"
( cd "${WORKDIR}" && zip -qr "${ZIP}" MANIFEST config.pref cert )
mkdir -p "${WEB_DIR}"
cp -f "${ZIP}" "${WEB_DIR}/${PKG_NAME}.zip"
chown www-data:www-data "${WEB_DIR}/${PKG_NAME}.zip" 2>/dev/null || true
chmod 644 "${WEB_DIR}/${PKG_NAME}.zip" 2>/dev/null || true

# Human-readable connect note for ICU / operators
cat > "${OUT_DIR}/psr-connect.txt" <<EOF
ПСР OpenTAKServer — подключение клиентов
========================================
Web UI:     https://${HOST}
ATAK SSL:   ${HOST}:${SSL_PORT} (ssl)
Enrollment: ${HOST}:${ENROLL_PORT}
Marti:      ${HOST}:8443
CA P12 pass: ${CA_PASS}

Data package: https://${HOST}/downloads/${PKG_NAME}.zip
  Import in ATAK: Import Manager / Local SD / the zip.

OpenTAK ICU (live video):
  RTSP:  ${HOST}:8554   path=callsign   user/pass = OTS login
  RTSPS: ${HOST}:8322   + truststore-root.p12 (pass ${CA_PASS})

Rebuild this package:
  ${BASH_SOURCE[0]:-$0}
EOF
cp -f "${OUT_DIR}/psr-connect.txt" "${WEB_DIR}/psr-connect.txt"
chown www-data:www-data "${WEB_DIR}/psr-connect.txt" 2>/dev/null || true

echo "OK ${ZIP}"
echo "URL https://${HOST}/downloads/${PKG_NAME}.zip"
ls -lh "${ZIP}"
