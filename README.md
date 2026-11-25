# MiniKV API

Учебный проект на Java 17: хранилище ключей и значений с локальным HTTP API.
Поддерживаются типы `string`, `list`, `set` и TTL. Время передаётся через
`Clock`, чтобы проверять истечение срока без ожидания в тестах. Операция над
значением несовместимого типа возвращает HTTP 409.

В проекте нет сохранения на диск, аутентификации и полного набора команд Valkey.

## Запуск и тесты

```bash
./run.sh test
./run.sh server
```

Требуется JDK 17+. Зависимостей и сетевых запросов для сборки нет.

## API

```bash
curl -X PUT http://127.0.0.1:8082/keys/greeting --data 'hello'
curl -X POST http://127.0.0.1:8082/keys/greeting/append --data ' world'
curl http://127.0.0.1:8082/keys/greeting
curl -X POST http://127.0.0.1:8082/keys/greeting/expire --data '60'
```

Сервер хранит данные в памяти и слушает только localhost; аутентификации нет.
