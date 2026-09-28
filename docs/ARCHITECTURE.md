# Architecture

Текст для слайдов — [PRESENTATION.md](PRESENTATION.md). Актуальная схема распознавания, API-контракты, ресурсы и запуск описаны в [API_RECOGNITION.md](API_RECOGNITION.md).

- Android: `mobile_app/WineApp`; polling подключён; результат, рекомендации и подтверждение описаны в [MOBILE_HANDOFF.md](MOBILE_HANDOFF.md).
- FastAPI: `backend/api`; очередь заданий, настоящий каталог, оценочный multipart endpoint.
- RabbitMQ: задания `scan`; MinIO: загруженные фотографии; PostgreSQL: состояния, результаты и копия карточек.
- GPU-worker: `backend/worker`; адаптер вызывает сохранённое ML-ядро `worker/pipeline`.
- SAM 3 выделяет бутылку и этикетку; геометрия строит виды; SigLIP2 и PaddleOCR GPU дают визуальные и текстовые признаки; гибридное ранжирование возвращает Top-5.
- Каталог и индекс версионируются вместе. Поиск использует индекс bundle, не pgvector.

OpenAPI: `/docs`, `/openapi.json`, [openapi.yaml](openapi.yaml).

Локальный YOLO26n в Android обрабатывает preview, рисует рамки и может запускать автосъёмку. Он не заменяет серверный ML. Пользовательский выбор сохраняется в JSONB скана отдельно от Top-5; API подбирает рекомендации по выбранной карточке или по OCR при отсутствии вина.
