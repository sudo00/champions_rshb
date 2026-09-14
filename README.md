# champions_rshb

Скелет сервиса: один JS-фронтенд, HTTP-бэкенд с Postgres / RabbitMQ / MinIO (S3) и отдельный Python-воркер. Только инициализация подключений, без бизнес-логики.

## Стек и версии

| Компонент | Технология | Версия |
| --- | --- | --- |
| Frontend | React | 18.3.1 |
| Frontend bundler | Vite | 5.4.8 |
| Frontend runtime (контейнер) | Node.js | 22.9.0 |
| Prod static server | nginx | 1.27.2 |
| Backend API | Python | 3.12.6 |
| Backend framework | Flask | 3.0.3 |
| Backend WSGI (prod) | Gunicorn | 23.0.0 |
| Worker | Python | 3.12.6 |
| RabbitMQ client | pika | 1.3.2 |
| S3 client | minio | 7.2.9 |
| HTTP client (worker) | requests | 2.32.3 |
| PostgreSQL | postgres | 16.4-alpine |
| Object storage | MinIO (`quay.io/minio/minio`) | RELEASE.2024-10-02T17-50-41Z |
| Очередь | RabbitMQ | 3.13.7-management-alpine |
| Оркестрация | Docker Compose | v2 |
| Сборка команд | GNU Make | 3.81+ / 4.x |

## Структура

```
build_env/          Dockerfiles, nginx, .env.dev / .env.prod
frontend/           React + Vite
backend/api/        Flask API
backend/worker/     Python worker
docker-compose.yml
Makefile
```

При старте API проверяет Postgres, MinIO и RabbitMQ и создаёт bucket `storage`, если его ещё нет. Worker ждёт API, S3 и RabbitMQ и держит соединение. Очередей, таблиц и обработчиков сообщений нет.

UI: http://localhost:3000 (dev) или http://localhost (prod).  
API: http://localhost:4000  
RabbitMQ UI: http://localhost:15673 (user / password)  
MinIO UI: http://localhost:9007 (minioadmin / minioadmin)

На хост проброшены нестандартные порты, чтобы не пересекаться с локальными Postgres/RabbitMQ/MinIO: `5433`, `5673`, `15673`, `9006`, `9007`. Внутри Docker-сети сервисы ходят на стандартные `5432` / `5672` / `9000`.

## Makefile

Нужны Docker Compose v2 и Make. Зависимости ставятся **внутри образов** при `build` / `dev`.

| Команда | Что делает |
| --- | --- |
| `make help` | список команд |
| `make install` | собрать Docker-образы (`npm ci` / `pip install` внутри) |
| `make install-frontend` | `npm install` локально в `frontend/` |
| `make install-backend` | `pip install` локально для api и worker |
| `make build` | то же, что `install` |
| `make dev` | DEV: `up -d --build` (Vite на порту 3000) |
| `make prod` | PROD: nginx на порту 80, gunicorn для API |
| `make up` | поднять DEV без пересборки |
| `make down` | остановить контейнеры проекта |
| `make ddown` | остановить контейнеры и удалить тома |
| `make logs` | логи всех сервисов |
| `make restart` | down + dev-сборка |
| `make test` | `pytest` в контейнере `champions-backend` |

Первый запуск: `make dev`, затем `make test`.
