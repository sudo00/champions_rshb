# Разработка

Android — `mobile_app/WineApp`, FastAPI — `backend/api`, очередь — `backend/worker`, ML-пайплайн — `worker/pipeline`. В `scripts/` находятся самостоятельные CLI; приложение их не импортирует.

Запуск и контракты — [API_RECOGNITION.md](API_RECOGNITION.md), зависимости — [DEPENDENCIES.md](DEPENDENCIES.md). Использовать отдельные среды проекта: `.venv`, `.venv-ocr-gpu`, `.venv-api`.

Тесты API, worker и модульные тесты Android входят в Git. Для проверки API без GPU и работающей инфраструктуры: `make test-api`; в локальном окружении: `.venv-api/bin/python -m pytest -q`. Проверки worker: `.venv/bin/python -m pytest backend/worker/tests -q`. Исследовательские ML-тесты в корневой `tests/` остаются локальными и исключены из Git.

Для проверки общего сервиса без локальной GPU: `python3 scripts/scan_api.py photo.jpg --base http://localhost:8000 --output result.json`. Адрес заменить на доступный URL сервера. Перед проверкой запросить `/ready`.

`python3 scripts/check_recognition_api.py` сравнивает выдачу с локальными сохранёнными результатами, которые не входят в Git. Изменения каталога, индекса или численного кода требуют согласованной версии bundle и контрольных сумм.
