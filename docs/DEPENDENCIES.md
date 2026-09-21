# Зависимости и воспроизведение окружения

Источник версий Python-пакетов — lock-файлы в корне репозитория. Dockerfile устанавливает их и дополнительно описывает ОС, Python и системные библиотеки. Доступ к GPU задаётся при запуске контейнера. Это разные части настройки.

## Какие файлы использовать

| Файл | Назначение |
| --- | --- |
| `requirements-vision.lock.txt` | Полный зафиксированный набор для `.venv`: PyTorch, SAM/SigLIP через Transformers, геометрия, поиск и библиотеки worker |
| `requirements-ocr-gpu.lock.txt` | Полный набор для `.venv-ocr-gpu`: PaddlePaddle GPU, PaddleOCR и их зависимости |
| `backend/api/requirements.lock.txt` | Зафиксированная среда `.venv-api`: FastAPI, PostgreSQL/RabbitMQ/MinIO clients, Pillow и тесты |
| `requirements-ocr.lock.txt` | Архивный CPU OCR для контрольных прогонов, не нужен для обычного GPU-запуска |
| `requirements-vision.txt` | Короткий список основных библиотек экспериментов; не заменяет полный lock-файл при передаче прототипа |
| `backend/worker/requirements.txt` | Зависимости инфраструктурного worker; сам по себе не устанавливает распознаватель |

Lock-файлы здесь — обычные requirements-файлы pip с точными версиями, включая зависимости библиотек. Расширение `.lock.txt` обозначает их назначение, отдельный менеджер пакетов не требуется.

На 21.09.2026 обе рабочие среды проверены: **70 закреплённых пакетов vision и 74 GPU OCR**, все версии совпали с установленными, `pip check` прошёл. Единственный установленный пакет сверх этих списков — сам `pip`.

## Проверенная платформа

- Linux x86_64, Python **3.12.3** в локальных окружениях; инструкции рассчитаны на Python 3.12. Wheel Paddle привязан к CPython 3.12 / Linux x86_64, это не универсальная установка для Windows/macOS.
- PyTorch **2.12.1**, Transformers **5.16.1**, CUDA-библиотеки ветки **13.0** в `.venv`.
- PaddlePaddle GPU **3.2.2**, PaddleOCR **3.3.2**, PaddleX **3.3.13**, CUDA-библиотеки **12.6** в `.venv-ocr-gpu`.
- RTX 3060 12 ГБ; сквозные GPU-замеры выполнены с драйвером NVIDIA **580.178.04**. Другую комбинацию драйвера и GPU нужно проверять отдельно.

PyTorch и Paddle работают в разных процессах и используют отдельные наборы библиотек. Устанавливать оба lock-файла в одно окружение нельзя: в них различаются, например, версии NumPy и Hugging Face Hub. Системный NVIDIA-драйвер устанавливается на хосте; виртуальное окружение его не содержит.

## Установка с нуля

Из корня репозитория, в новые окружения:

```bash
python3.12 -m venv .venv
.venv/bin/python -m pip install -r requirements-vision.lock.txt
.venv/bin/python -m pip check

python3.12 -m venv .venv-ocr-gpu
.venv-ocr-gpu/bin/python -m pip install -r requirements-ocr-gpu.lock.txt
.venv-ocr-gpu/bin/python -m pip check

python3.12 -m venv .venv-api
.venv-api/bin/python -m pip install -r backend/api/requirements.lock.txt
.venv-api/bin/python -m pip check
```

Если окружение уже заполнено другими пакетами, установка requirements не удалит лишнее. Для воспроизведения релиза создавайте чистые среды. Каталоги `.venv*` не переносят на другую машину и не включают в Git или контекст Docker-сборки.

Проверки на машине с доступом к GPU:

```bash
nvidia-smi
.venv/bin/python -c 'import torch; print(torch.__version__, torch.version.cuda); assert torch.cuda.is_available()'
.venv-ocr-gpu/bin/python -c 'import paddle; assert paddle.is_compiled_with_cuda(); paddle.utils.run_check()'
```

Зависимости — только часть поставки. Для инференса также нужен согласованный пакет весов, каталога и индекса с `manifest.json`. Текущий пакет, пример вызова класса и CLI: [API_RECOGNITION.md](API_RECOGNITION.md). Их целостность проверяется отдельно от Python-пакетов.

## Что делает Docker

`build_env/worker/Dockerfile.gpu` использует Python 3.12.6 на Debian Bookworm и устанавливает системные `libglib2.0-0`, `libgl1`, `libgomp1`. Внутри созданы `/opt/vision` и `/opt/ocr` из соответствующих lock-файлов. Код `worker/`, `backend/worker/` и геометрические helpers сохранены по ожидаемым путям.

`docker-compose.gpu.yml` подключает GPU, монтирует bundle в `/models` и изображения карточек в API. Make включает этот override для DEV и PROD. Worker стартует как модуль `backend.worker.main`. Контейнер API устанавливает собственный lock-файл и не загружает ML-модели.

Веса и индекс не скачиваются при старте: коллегам нужен отдельный согласованный bundle. Драйвер NVIDIA и Container Toolkit устанавливаются на хосте. Базовый образ закреплён тегом, но не digest; системные apt-пакеты не имеют полного lock. Фиксация Python-версий не означает побайтовую воспроизводимость всей ОС.

Команды запуска и проверок: [API_RECOGNITION.md](API_RECOGNITION.md). Результаты фактической проверки контейнеров и API: [API_INTEGRATION_VALIDATION.md](API_INTEGRATION_VALIDATION.md).

## Как обновлять версии

Обновляйте нужную среду отдельно. Сначала сохраните текущий lock-файл, затем получите кандидат через `pip freeze`, просмотрите изменения и выполните `pip check`, тесты и сравнение ответов/VRAM/времени на сохранённых фото. Только после проверки заменяйте lock-файл и выпускайте новый пакет. Для CPU/GPU OCR и vision нужны отдельные снимки зависимостей.

У Paddle GPU в lock-файле закреплены официальный URL wheel и SHA-256. При обновлении не заменяйте их случайным локальным `file:///...`, который может появиться в `pip freeze`. У остальных пакетов сейчас закреплены версии, но нет полного набора хешей wheel. Это фиксация версий, а не полностью автономная или побайтово воспроизводимая поставка: установка зависит от доступности индексов. Для более строгого релиза можно добавить хеши всех пакетов и подготовить wheelhouse — каталог установочных файлов для нужной платформы. [Уровни воспроизводимости pip](https://pip.pypa.io/en/stable/topics/repeatable-installs/).
