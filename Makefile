ENV_DEV := build_env/.env.dev
ENV_PROD := build_env/.env.prod
COMPOSE_DEV := docker compose --env-file $(ENV_DEV) -f docker-compose.yml -f docker-compose.gpu.yml -f docker-compose.dev.yml
COMPOSE_PROD := docker compose --env-file $(ENV_PROD) -f docker-compose.yml -f docker-compose.gpu.yml
API_HEALTH := http://127.0.0.1:8000/health
TEST_BUILD_NETWORK ?= default

.PHONY: help setup env wait-api install install-api install-worker build dev prod up down ddown logs restart test test-api import-catalog download-weights

help:
	@echo "make setup             — скопировать env, скачать веса, собрать образы, поднять стек, дождаться API"
	@echo "make env               — создать .env и build_env/.env.* из шаблонов, если их нет"
	@echo "make download-weights  — скачать веса и картинки с Google Диска (build_env/gdrive.env)"
	@echo "make install           — скачать веса, затем pip install внутри api и worker"
	@echo "make install-api       — pip install в контейнере api"
	@echo "make install-worker    — pip install в контейнере worker"
	@echo "make build             — собрать Docker-образы"
	@echo "make dev               — DEV: сборка + up -d"
	@echo "make prod              — PROD: сборка + up -d"
	@echo "make up                — поднять DEV без пересборки"
	@echo "make down              — остановить контейнеры"
	@echo "make ddown             — остановить контейнеры и удалить тома"
	@echo "make logs              — логи"
	@echo "make restart           — перезапустить DEV"
	@echo "make test              — pytest в контейнере api"
	@echo "make test-api          — автономные API-тесты в Docker, без GPU и сервисов"
	@echo "make import-catalog    — скачать vino-svoe.ru в Postgres и MinIO"

setup: env download-weights
	$(COMPOSE_DEV) up -d --build
	$(MAKE) wait-api
	@echo "Веса скачаны. Дождитесь загрузки моделей в GPU: GET /ready"

wait-api:
	@echo "Ждём API $(API_HEALTH) ..."
	@n=0; \
	until curl -sf "$(API_HEALTH)" >/dev/null; do \
		n=$$((n+1)); \
		if [ $$n -ge 45 ]; then \
			echo "API не поднялся за 90 с. Логи: make logs"; \
			exit 1; \
		fi; \
		sleep 2; \
	done
	@curl -s "$(API_HEALTH)"; echo
	@echo "API готов"

env:
	@if [ ! -f .env ]; then cp .env.example .env && echo "создан .env"; else echo ".env уже есть"; fi
	@if [ ! -f $(ENV_DEV) ]; then cp build_env/env.dev.example $(ENV_DEV) && echo "создан $(ENV_DEV)"; else echo "$(ENV_DEV) уже есть"; fi
	@if [ ! -f $(ENV_PROD) ]; then cp build_env/env.prod.example $(ENV_PROD) && echo "создан $(ENV_PROD)"; else echo "$(ENV_PROD) уже есть"; fi
	@if [ ! -f build_env/gdrive.env ]; then cp build_env/gdrive.env.example build_env/gdrive.env && echo "создан build_env/gdrive.env"; else echo "build_env/gdrive.env уже есть"; fi

install: download-weights install-api install-worker

download-weights:
	python3 scripts/download_gdrive_weights.py $(if $(FORCE),--force,)

build:
	$(COMPOSE_DEV) build

install-api:
	$(COMPOSE_DEV) up -d db s3 rabbitmq
	$(COMPOSE_DEV) up -d --no-deps api
	$(COMPOSE_DEV) exec -T api pip install --no-cache-dir -r api/requirements.lock.txt
	$(COMPOSE_DEV) restart api

install-worker:
	$(COMPOSE_DEV) build worker
	$(COMPOSE_DEV) up -d worker

dev: download-weights
	$(COMPOSE_DEV) up -d --build

prod: download-weights
	$(COMPOSE_PROD) up -d --build

up:
	$(COMPOSE_DEV) up -d

down:
	$(COMPOSE_DEV) down

ddown:
	$(COMPOSE_DEV) down -v

logs:
	$(COMPOSE_DEV) logs -f

restart:
	$(COMPOSE_DEV) down
	$(COMPOSE_DEV) up -d --build

test:
	$(COMPOSE_DEV) exec -T -e PYTHONPATH=/app api pytest -q /app/api/tests

test-api:
	docker build --network=$(TEST_BUILD_NETWORK) --target api-tests -f build_env/api/Dockerfile -t wine-api-tests .
	docker run --rm --network=none wine-api-tests

import-catalog:
	$(COMPOSE_DEV) exec -T api python -m api.import_vino_svoe
