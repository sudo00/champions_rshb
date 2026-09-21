# Architecture

Актуальная схема распознавания, API-контракты, ресурсы и запуск описаны в [API_RECOGNITION.md](API_RECOGNITION.md).

- Android: `mobile_app/WineApp`; клиенту ещё требуется подключить polling и nullable-поля.
- FastAPI: `backend/api`; очередь заданий, настоящий каталог, оценочный multipart endpoint.
- RabbitMQ: задания `scan`; MinIO: загруженные фотографии; PostgreSQL: состояния, результаты и копия карточек.
- GPU-worker: `backend/worker`; адаптер вызывает сохранённое ML-ядро `worker/pipeline`.
- SAM 3 выделяет бутылку и этикетку; геометрия строит виды; SigLIP2 и PaddleOCR GPU дают визуальные и текстовые признаки; гибридное ранжирование возвращает Top-5.
- Каталог и индекс версионируются вместе. Поиск использует индекс bundle, не pgvector.

OpenAPI: `/docs`, `/openapi.json`, [openapi.yaml](openapi.yaml).
