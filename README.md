# Сканер «Своё вино»

Сервис распознаёт российское вино по фото этикетки. Клиент — Android в `mobile_app/`, HTTP и CV — Python в `backend/`.

## Требования

- Docker Compose v2
- GNU Make
- JDK 17+ только если собираете приложение

## Быстрый старт (backend)

```bash
make setup
```

| Что | URL |
| --- | --- |
| API | http://localhost:3000 |
| Swagger UI | http://localhost:3000/docs |
| ReDoc | http://localhost:3000/redoc |
| OpenAPI JSON | http://localhost:3000/openapi.json |
| Спека в репозитории | [docs/openapi.yaml](docs/openapi.yaml) |
| RabbitMQ | http://localhost:15673 — `user` / `password` |
| MinIO | http://localhost:9007 — `minioadmin` / `minioadmin` |

Контракты совпадают с `ApiService` в приложении. JSON — camelCase.

| Метод | Путь |
| --- | --- |
| GET | `/health` |
| POST | `/v1/wines/scan` — постановка в очередь, ответ `scanId` |
| GET | `/v1/wines/scan/{scanId}` — polling результата (~1 с) |
| GET | `/v1/wines/search` |
| GET | `/v1/wines/{id}` |
| POST | `/v1/sommelier/chat` |

История сканов хранится в Room на телефоне.

## Makefile

| Команда | Действие |
| --- | --- |
| `make setup` | собрать образы, поднять контейнеры, дождаться рабочего API |
| `make dev` / `make prod` | DEV (:3000→8000) / PROD без ожидания health |
| `make up` / `make down` / `make ddown` | без пересборки / стоп / стоп с томами |
| `make test` | pytest в контейнере `api` |
| `make logs` / `make restart` | логи / пересборка DEV |

В DEV смонтированы `backend/api` и `backend/worker`.

## Стек

FastAPI, PostgreSQL 16 + pgvector, MinIO, RabbitMQ, Python-воркер (SigLIP 2), Android (Jetpack Compose). Архитектура: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
