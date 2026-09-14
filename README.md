# Сканер «Своё вино»

Сервис распознаёт российское вино по фото этикетки и сразу открывает одну карточку каталога «Своё вино» (без экрана вариантов). Модуль рассчитан на мобильный браузер у полки.

Репозиторий — скелет: инфраструктура, контракты API и слои пайплайна. Поиск по изображению ещё не обучен.

## Требования

- Docker Compose v2
- GNU Make
- GPU опционален: без него инференс/обучение на CPU или внешнем API (ТЗ)

## Быстрый старт

```bash
make dev
make install-app
```

`make dev` собирает образы без `npm install`. Зависимости Nuxt ставятся в контейнере (`node_modules` в томе) и переживают перезапуск, пока не сделали `make ddown`.

В DEV исходники смонтированы с хоста: правки в `app/`, `server/` и `worker/` подхватываются без пересборки и без `docker compose restart`. Nuxt — HMR, Python-воркер перезапускает процесс через `watchfiles`. Данные Postgres, MinIO и RabbitMQ в именованных томах.

| Что | URL |
| --- | --- |
| UI | http://localhost:3000 |
| Ready | http://localhost:3000/api/ready |
| Scan (заглушка) | `POST http://localhost:3000/api/scan` |
| Eval (заглушка) | `POST http://localhost:3000/api/eval` → `{"slug":"unknown"}` |
| Карточка | `GET /api/wines/:slug` |
| RabbitMQ UI | http://localhost:15673 — `user` / `password` |
| MinIO UI | http://localhost:9007 — `minioadmin` / `minioadmin` |

Пример вызова контракта оценки:

```bash
chmod +x scripts/eval.example.sh
./scripts/eval.example.sh path/to/label.jpg
```

После смены образа Postgres на pgvector нужен новый том: `make ddown && make dev` (один раз, сотрёт локальную БД).

## Переменные окружения

Шаблон: `.env.example`. В compose уже прописаны значения для Docker-сети.

| Переменная | Назначение |
| --- | --- |
| `DATABASE_URL` | PostgreSQL (внутри сети хост `db`) |
| `S3_ENDPOINT` | MinIO `host:port` |
| `S3_BUCKET_NAME` | бакет эталонов, по умолчанию `storage` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | ключи MinIO |
| `RABBITMQ_HOST` / `PORT` / `USER` / `PASS` / `VHOST` | очередь |
| `RABBITMQ_QUEUE_SCAN` | очередь CV, по умолчанию `scan` |
| `BACKEND_URL` | health API для воркера (`http://app:3000/api/ready`) |

## Makefile

| Команда | Действие |
| --- | --- |
| `make dev` / `make prod` | поднять DEV (Vite/Nuxt :3000) / PROD |
| `make up` / `make down` / `make ddown` | без пересборки / стоп / стоп с томами |
| `make install-app` | `npm install` в контейнере `app` (после `make dev`) |
| `make test` | vitest в `champions-app` |
| `make logs` / `make restart` | логи / пересборка DEV |

## Стек

Nuxt 4.5 (UI + HTTP), PostgreSQL 16 + pgvector, MinIO, RabbitMQ, Python 3.12 воркер (SigLIP 2). Каталог в проде платформы — Strapi; здесь дамп импортируется в Postgres. Подробнее: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), этапы: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Ограничения

- Локальная демонстрация, внешний деплой не требуется.
- Каталог 1500–2000 SKU, много near-duplicates (год/сезон/категория).
- Eval ждёт один slug в плоском JSON; F1 топ-1 и топ-5 — в `POST /api/scan`, не обязательно в UI.
- SLA ответа — до 3 секунд (плюс к оценке, не база).
- Цель по совпадениям на контроле — 90–100%.
- Пока модель не подключена, `slug` в eval равен `unknown`.
