# План разработки

21.09.2026: [v4 — освобождение GPU-кеша](MEMORY_OPTIMIZATION_V4.md), без изменения моделей и вычислений. [Теоретический план сомелье](SOMMELIER_CAPACITY.md); LLM не запускалась.

21.09.2026: [v3 — выбор целой бутылки](TARGET_SELECTION_V3.md). [Состав моделей и план уменьшения VRAM](VRAM_PLAN.md). LightGlue отложен до новых примеров ошибок.

21.09.2026: отдельный Python-компонент `WineRecognizer` переведён на GPU OCR. [Инструкция интеграции и пакет ресурсов](RECOGNIZER_HANDOFF.md): SAM 3 + SigLIP 2 + PaddleOCR на одной RTX 3060; медиана полного запроса 3,08 с вместо 10,65 с. [Проверка всех 76 фото](GPU_OCR_EXPERIMENT.md) сохранила первые кандидаты и метрики. Текущие HTTP/очередь и `pipeline.run` ещё не подключены к этому компоненту.

Кейс: сканер российских вин для «Своё вино». Оценка: достоверность 50, retention-фича 20, архитектура 15, дизайн 10, скорость 5.

## Локальное Python-окружение

Запускайте CV-эксперименты из отдельного `.venv` этого репозитория (Python 3.12):

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements-vision.txt -r worker/requirements.txt
python -m pip check
```

Без активации используйте `.venv/bin/python`. В IDE выберите тот же интерпретатор. Основные версии CV-зависимостей закреплены в `requirements-vision.txt`; полный проверенный набор для Python 3.12/Linux, включая воркер, — в `requirements-vision.lock.txt` (`python -m pip install -r requirements-vision.lock.txt`). Окружение и веса моделей исключены из Git. Установка PyTorch включает большие CUDA-пакеты, но доступность GPU проверяется отдельно через `torch.cuda.is_available()`.

Не используйте окружения соседних проектов. Для GPU PaddleOCR используется отдельное `.venv-ocr-gpu` с `requirements-ocr-gpu.lock.txt`; `.venv-ocr` сохранено для CPU-контроля. [Установка и запуск](GPU_OCR_EXPERIMENT.md). Зависимости Docker-воркера по-прежнему задаются в `worker/requirements.txt`.

## Этап 0 — скелет (сейчас)

- [x] Nuxt fullstack (`app` + `server`), Docker, Python-воркер
- [x] Postgres + pgvector, MinIO, RabbitMQ
- [x] Контракты API и очереди без CV
- [x] README / ARCHITECTURE / этот файл

## Этап 1 — каталог

- Импорт дампа (JSON/CSV, 1500–2000 позиций) в `wines`
- Эталоны в MinIO, ключ `image_key`
- Миграция pgvector, размерность под SigLIP 2
- Near-duplicates: сезон/год/категория при похожей этикетке — заложить в схему (серия, vintage)

## Этап 2 — CV end-to-end

- `worker/pipeline/normalize.py` — кроп, свет, геометрия
- `embed.py` — SigLIP 2 (дообучение по необходимости; GPU свой)
- `search.py` — топ-1 для UX, топ-5 для F1
- `POST /api/scan` и `POST /api/eval` отдают реальный slug
- Цель: 90–100% совпадений на публичном сете, отрыв 1-го от 2-го, SLA &lt; 3 с

## Этап 3 — интерфейс

- Mobile-first загрузка (камера/галерея)
- Карточка в стилистике «Своё Вино»: производитель, регион, сорт, описание, рейтинг, к чему подать
- Без экрана «похожие», если F1 топ-1 стабилен
- Нет в каталоге: аналоги или честное «не найдено»

## Этап 4 — retention (20 баллов)

После поиска: «цифровой сомелье» (вопросы → рекомендация) или аналоги других виноделен. Не блокирует eval-скрипт.

## Этап 5 — сдача

- Прогон публичного датасета: F1 топ-1/топ-5, время, доля совпадений
- Видео до 7 минут без склеек (поиск вживую)
- Совместимость со скриптом кейсодержателя
- Питч: концепция, пайплайн, демо, метрики, фича, выводы
- Финал (топ-10): воспроизводимый Docker + README + ARCHITECTURE, новая фича после поиска

## Не делаем без нужды

- Нативные iOS/Android
- Оптимизация переиндексации (~50 SKU/день)
- Обязательный OCR (только если поднимет F1 без провала SLA)
- Внешний прод-деплой: показ локально

## Текстовый поиск и чтение бутылки

Локальный эксперимент описан в [TEXT_SEARCH_PILOT.md](TEXT_SEARCH_PILOT.md). Он использует `.venv` для SAM 3/поиска и `.venv-ocr` для PaddleOCR. Установить актуальные `requirements-vision.txt`; новая зависимость поиска — RapidFuzz.

```bash
.venv/bin/python scripts/catalog_text_search.py query --text "МАССАНАРА МУСКАТЕЛЬ БЕЛЫЙ" --limit 5
.venv/bin/python scripts/catalog_text_search.py report
.venv/bin/python -m unittest discover -s tests -p "test_text_search.py"
.venv/bin/python -m unittest discover -s tests -p "test_bottle_ocr_pilot.py"
```

Новые фотографии обрабатываются командами `prepare` и `ocr` из руководства; для `prepare` требуется доступ к GPU. HTTP-контракты текущего скелета пока сохраняются.
