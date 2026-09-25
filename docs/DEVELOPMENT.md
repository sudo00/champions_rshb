# Разработка

Android — `mobile_app/WineApp`, FastAPI — `backend/api`, очередь — `backend/worker`, ML-пайплайн — `worker/pipeline`. В `scripts/` находятся самостоятельные CLI; приложение их не импортирует.

Запуск и контракты — [API_RECOGNITION.md](API_RECOGNITION.md), зависимости — [DEPENDENCIES.md](DEPENDENCIES.md). Использовать отдельные среды проекта: `.venv`, `.venv-ocr-gpu`, `.venv-api`.

Автоматические проверки API, worker и Android сохранены только локально и исключены из Git. В свежем клоне нет тестов, `pytest.ini`, целей `make test` / `make test-api` и тестовой стадии Docker. При наличии локальных файлов проверки запускаются явно: `.venv-api/bin/python -m pytest backend/api/tests -q` и `.venv/bin/python -m pytest backend/worker/tests -q`.

Для проверки общего сервиса без локальной GPU: `python3 scripts/scan_api.py photo.jpg --base http://localhost:8000 --output result.json`. Адрес заменить на доступный URL сервера. Перед проверкой запросить `/ready`.

`python3 scripts/check_recognition_api.py` сравнивает выдачу с локальными сохранёнными результатами, которые не входят в Git. Изменения каталога, индекса или численного кода требуют согласованной версии bundle и контрольных сумм.
