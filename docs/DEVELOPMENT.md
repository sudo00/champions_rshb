# Разработка

Приложение Android находится в `mobile_app/WineApp`, FastAPI — в `backend/api`, очередь — в `backend/worker`. Проверенный ML-код и эксперименты сохранены в `worker/pipeline` и `scripts`.

Настоящий каталог, Top-5, OCR, области изображения и оценочный multipart API подключены. Подробности запуска и контрактов — [API_RECOGNITION.md](API_RECOGNITION.md), зависимости — [DEPENDENCIES.md](DEPENDENCIES.md), результаты проверки — [API_INTEGRATION_VALIDATION.md](API_INTEGRATION_VALIDATION.md).

Для проверки API без GPU, весов и работающей инфраструктуры выполнить `make test-api`. Команда собирает тестовый Docker-образ и запускает проверки без сети.

Использовать отдельные среды проекта: `.venv`, `.venv-ocr-gpu`, `.venv-api`. Для API в локальном окружении выполнить `.venv-api/bin/python -m pytest -q`; для адаптера — `.venv/bin/python -m unittest backend.worker.tests.test_recognition -q`. Тесты самого ML в корневой `tests/` остаются локально и не входят в Git.

Для сравнения с локальными результатами 76 магазинных снимков при запущенном API: `python3 scripts/check_recognition_api.py`. Снимки и результаты не входят в Git. При изменении численного ML-кода нужен новый согласованный bundle, иначе проверка SHA-256 при запуске отклонит его.
