# План разработки

Кейс: сканер российских вин для «Своё вино».

## Этап 0 — скелет (сейчас)

- [x] FastAPI под контракты Android `ApiService`
- [x] Postgres + pgvector, MinIO, RabbitMQ
- [x] Python-воркер, очередь `scan`
- [x] README / ARCHITECTURE / OpenAPI

## Этап 1 — каталог

- Импорт дампа в `wines`
- Эталоны в MinIO
- pgvector под SigLIP 2

## Этап 2 — CV

- `backend/worker/pipeline/*`
- `POST /v1/wines/scan` отдаёт реальный `WineDto`

## Этап 3 — сомелье

- LLM вместо rule-based заглушки в `POST /v1/sommelier/chat`
