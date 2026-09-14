ENV_DEV := build_env/.env.dev
ENV_PROD := build_env/.env.prod
COMPOSE := docker compose

.PHONY: help install install-frontend install-backend build dev prod up down ddown logs restart test

help:
	@echo "make install           — собрать образы (зависимости ставятся внутри Docker)"
	@echo "make install-frontend  — npm install локально в ./frontend"
	@echo "make install-backend   — pip install локально для api и worker"
	@echo "make dev               — поднять DEV окружение (сборка + up -d)"
	@echo "make prod              — поднять PROD окружение (сборка + up -d)"
	@echo "make up                — поднять DEV без пересборки"
	@echo "make down              — остановить контейнеры проекта"
	@echo "make ddown             — остановить контейнеры и удалить тома"
	@echo "make logs              — логи всех сервисов"
	@echo "make restart           — перезапустить DEV"
	@echo "make test              — pytest в контейнере backend"

install: build

build:
	$(COMPOSE) --env-file $(ENV_DEV) build

install-frontend:
	cd frontend && npm install

install-backend:
	python3 -m pip install -r backend/api/requirements.txt
	python3 -m pip install -r backend/worker/requirements.txt

dev:
	$(COMPOSE) --env-file $(ENV_DEV) up -d --build

prod:
	$(COMPOSE) --env-file $(ENV_PROD) up -d --build

up:
	$(COMPOSE) --env-file $(ENV_DEV) up -d

down:
	$(COMPOSE) --env-file $(ENV_DEV) down

ddown:
	$(COMPOSE) --env-file $(ENV_DEV) down -v

logs:
	$(COMPOSE) --env-file $(ENV_DEV) logs -f

restart:
	$(COMPOSE) --env-file $(ENV_DEV) down
	$(COMPOSE) --env-file $(ENV_DEV) up -d --build

test:
	$(COMPOSE) --env-file $(ENV_DEV) exec backend pytest -q
