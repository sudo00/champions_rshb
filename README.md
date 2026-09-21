# Своё вино — распознавание этикеток

Android-приложение, FastAPI и GPU-worker для поиска вина по фотографии. Runtime: SAM 3 → виды бутылки/этикетки → SigLIP2 + PaddleOCR GPU → гибридный поиск по 2 103 проверенным карточкам.

Весь ML-код, включая геометрию этикеток, находится в `worker/pipeline/`.
Интеграция с очередью — `backend/worker/`; точка входа — `recognition.run(...)`.
`scripts/` содержит эксперименты и служебные команды и не требуется worker-образу.

## Начало работы

1. Получить `weights/wine-recognizer-v5-worker-layout-release/` с весами и индексом.
2. Распаковать `catalog-images-v1.tar` в `data/deployment/catalog-images/`.
3. На Linux с NVIDIA GPU и Container Toolkit выполнить `make env`, затем `make dev`.
4. Дождаться `http://localhost:8000/ready`; Swagger — `http://localhost:8000/docs`.

Полные команды, контракты, ограничения и скрипт проверки организаторов: [docs/API_RECOGNITION.md](docs/API_RECOGNITION.md). Установка зависимостей: [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).

Передача коллегам: [docs/BACKEND_HANDOFF.md](docs/BACKEND_HANDOFF.md) — API, ресурсы, запуск на новой машине и оставшиеся задачи.

## Документация в Git

В `.gitignore` задан явный список общих документов из `docs/`:

- `ARCHITECTURE.md`, `architecture.mmd`: устройство системы и схема.
- `API_RECOGNITION.md`, `openapi.yaml`, `examples/scan-response.json`: контракт и пример ответа.
- `BACKEND_HANDOFF.md`: приёмка интеграции и оставшиеся задачи.
- `DEPENDENCIES.md`, `DEVELOPMENT.md`: установка, запуск и тестирование.
- `API_INTEGRATION_VALIDATION.md`: результаты проверки интеграции и ограничения.

Заметки по данным, исследованиям и экспериментам остаются локальными. Новые
документы по умолчанию игнорируются; для общего документа добавить точное
исключение в `.gitignore`. Уже отслеживаемый файл не перестаёт отслеживаться
после добавления правила игнорирования.

## Структура

- `backend/api`: HTTP, карточки каталога, задания и результаты.
- `backend/worker/recognition.py`: `run(image: bytes, includeAlternatives: bool = True)`.
- `worker/pipeline`: проверенное ядро распознавания и поиска.
- `backend/catalog`: исправленный каталог и контрольная сумма.
- `scripts`: эксперименты, упаковка ресурсов, проверка API.
- `mobile_app/WineApp`: Android-клиент коллег; адаптация polling описана в документации.
- `backend/api/tests`, `backend/worker/tests`: проверки HTTP-контрактов и адаптера.
- `weights/`: локальные веса и готовый ML-bundle; исключены из Git.
- `data/`: фотографии, исходные данные и отчёты; исключены из Git.

ML-тесты в `tests/` сохранены локально и исключены из Git.

## Проверка фотографии

```bash
python3 scripts/scan_api.py photo.jpg --output result.json
```

`includeAlternatives=true` возвращает до пяти кандидатов; `false` — одного. Результат включает OCR и области изображения. `score` — оценка ранжирования, не вероятность; отказ для отсутствующего в каталоге вина пока не откалиброван.

## Тесты

```bash
# Без GPU, весов, базы и запущенного стека:
make test-api

# В локальном Python-окружении API:
.venv-api/bin/python -m pytest -q
```

Паритет с сохранёнными результатами магазинных снимков: `python3 scripts/check_recognition_api.py` при работающем API и наличии локальных данных.

## Как API вызывает ML

```text
Телефон / scripts/scan_api.py
  → POST /v1/wines/scan → фото в MinIO, задание в RabbitMQ
  → backend/worker/main.py::process_scan
  → backend/worker/recognition.py::run(image, includeAlternatives)
  → worker/pipeline/wine_recognizer.py::WineRecognizer.predict
  → результат в PostgreSQL
  → GET /v1/wines/scan/{scanId} → slug, карточки, OCR, области
```

Точка запуска worker: `python -m backend.worker.main` (обычно запускается Docker).
API работает отдельно; модели принадлежат worker. Бэкендер без GPU запускает
`make test-api` локально, а настоящее распознавание проверяет запросами к общему
GPU-серверу через `scripts/scan_api.py --base <API_URL> photo.jpg`.
Подробности и границы локального тестирования: [docs/BACKEND_HANDOFF.md](docs/BACKEND_HANDOFF.md).
