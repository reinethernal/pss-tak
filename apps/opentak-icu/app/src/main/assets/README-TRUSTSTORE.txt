На CI и в публичном репозитории truststore-root.p12 не хранится.
При серверной сборке скопируйте CA:
  cp /home/ots/ots/ca/truststore-root.p12 app/src/main/assets/truststore-root.p12
Хост fts.plasmadancer.ru всё равно зашит в Preferences / PsrServerDefaults.
