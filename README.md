# Своё вино — распознавание этикеток

Android-приложение, FastAPI и GPU-worker для поиска вина по фотографии. Runtime: SAM 3 → виды бутылки/этикетки → SigLIP2 + PaddleOCR GPU → гибридный поиск по 2 103 проверенным карточкам.

## Начало работы

1. Получить `data/deployment/wine-recognizer-v4-memory-release/` с весами и индексом.
2. Распаковать `catalog-images-v1.tar` в `data/deployment/catalog-images/`.
3. На Linux с NVIDIA GPU и Container Toolkit выполнить `make env`, затем `make dev`.
4. Дождаться `http://localhost:8000/ready`; Swagger — `http://localhost:8000/docs`.

Полные команды, контракты, ограничения и скрипт проверки организаторов: [docs/API_RECOGNITION.md](docs/API_RECOGNITION.md). Установка зависимостей: [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).

Передача коллегам: [docs/BACKEND_HANDOFF.md](docs/BACKEND_HANDOFF.md) — API, ресурсы, запуск на новой машине и оставшиеся задачи.

## Структура

- `backend/api`: HTTP, карточки каталога, задания и результаты.
- `backend/worker/recognition.py`: `run(image: bytes, includeAlternatives: bool = True)`.
- `worker/pipeline`: проверенное ядро распознавания и поиска.
- `backend/catalog`: исправленный каталог и контрольная сумма.
- `scripts`: эксперименты, упаковка ресурсов, проверка API.
- `mobile_app/WineApp`: Android-клиент коллег; адаптация polling описана в документации.
- `tests`, `backend/api/tests`: проверки ML-компонента и HTTP-контрактов.

## Проверка фотографии

```bash
python3 scripts/scan_api.py photo.jpg --output result.json
```

`includeAlternatives=true` возвращает до пяти кандидатов; `false` — одного. Результат включает OCR и области изображения. `score` — оценка ранжирования, не вероятность; отказ для отсутствующего в каталоге вина пока не откалиброван.

## Тесты

```bash
.venv-api/bin/python -m pytest -q
.venv/bin/python -m unittest tests.test_worker_adapter tests.test_wine_recognizer -q
```

Паритет с сохранёнными результатами магазинных снимков: `python3 scripts/check_recognition_api.py` при работающем API и наличии локальных данных.
