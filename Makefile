ENV_DEV := build_env/.env.dev
ENV_PROD := build_env/.env.prod
COMPOSE_DEV := docker compose --env-file $(ENV_DEV) -f docker-compose.yml -f docker-compose.dev.yml
COMPOSE_PROD := docker compose --env-file $(ENV_PROD) -f docker-compose.yml

.PHONY: help install install-app install-frontend install-backend install-worker build dev prod up down ddown logs restart test

help:
	@echo "make install           — npm/pip install внутри контейнеров app и worker"
	@echo "make install-app       — npm install в контейнере app (не в образе)"
	@echo "make install-frontend  — то же, что install-app"
	@echo "make install-backend   — то же, что install-app"
	@echo "make install-worker    — pip install в контейнере worker"
	@echo "make build             — собрать Docker-образы"
	@echo "make dev               — собрать образы (без npm) и поднять контейнеры"
	@echo "make prod              — поднять PROD окружение (сборка + up -d)"
	@echo "make up                — поднять DEV без пересборки"
	@echo "make down              — остановить контейнеры проекта"
	@echo "make ddown             — остановить контейнеры и удалить тома"
	@echo "make logs              — логи всех сервисов"
	@echo "make restart           — перезапустить DEV"
	@echo "make test              — vitest в контейнере app"

install: install-app install-worker

build:
	$(COMPOSE_DEV) build

install-app:
	$(COMPOSE_DEV) up -d db s3 rabbitmq
	$(COMPOSE_DEV) up -d --no-deps app
	$(COMPOSE_DEV) exec -T app npm install
	$(COMPOSE_DEV) restart app
	-$(COMPOSE_DEV) restart worker

install-frontend: install-app

install-backend: install-app

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
	$(COMPOSE_DEV) exec -T app npm test
