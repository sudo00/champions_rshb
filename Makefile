ENV_DEV := build_env/.env.dev
ENV_PROD := build_env/.env.prod
COMPOSE_DEV := docker compose --env-file $(ENV_DEV) -f docker-compose.yml -f docker-compose.dev.yml
COMPOSE_PROD := docker compose --env-file $(ENV_PROD) -f docker-compose.yml
API_HEALTH := http://127.0.0.1:3000/health

.PHONY: help setup wait-api install install-api install-worker build dev prod up down ddown logs restart test

help:
	@echo "make setup             — собрать образы, поднять стек, дождаться рабочего API"
	@echo "make install           — pip install внутри api и worker"
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

setup:
	$(COMPOSE_DEV) build
	$(COMPOSE_DEV) up -d --wait db rabbitmq
	$(COMPOSE_DEV) up -d s3
	$(COMPOSE_DEV) up -d --no-deps api
	$(COMPOSE_DEV) exec -T api pip install --no-cache-dir -r api/requirements.txt
	$(COMPOSE_DEV) up -d --no-deps worker
	$(COMPOSE_DEV) exec -T worker pip install --no-cache-dir -r requirements.txt
	$(COMPOSE_DEV) restart api worker
	$(MAKE) wait-api
	@echo ""
	@echo "Backend готов:"
	@echo "  API      $(API_HEALTH)"
	@echo "  Swagger  http://localhost:3000/docs"
	@echo "  RabbitMQ http://localhost:15673  user / password"
	@echo "  MinIO    http://localhost:9007  minioadmin / minioadmin"

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

install: install-api install-worker

build:
	$(COMPOSE_DEV) build

install-api:
	$(COMPOSE_DEV) up -d db s3 rabbitmq
	$(COMPOSE_DEV) up -d --no-deps api
	$(COMPOSE_DEV) exec -T api pip install --no-cache-dir -r api/requirements.txt
	$(COMPOSE_DEV) restart api

install-worker: up
	$(COMPOSE_DEV) exec -T worker pip install --no-cache-dir -r requirements.txt

dev:
	$(COMPOSE_DEV) up -d --build

prod:
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
