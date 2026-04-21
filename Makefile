# AI-CAD — common dev tasks
#
# Kasuta: `make <target>`
# Abiks:   `make help`

.PHONY: help install test test-backend test-frontend test-worker test-slicer \
        lint format build up down logs clean e2e load-smoke ci-local \
        migrate-up migrate-info docker-build

.DEFAULT_GOAL := help

## ─── General ──────────────────────────────────────────────────────────

help:  ## Näita seda abi
	@awk 'BEGIN {FS = ":.*##"; printf "\nMakefile targets:\n"} \
	 /^[a-zA-Z_-]+:.*##/ {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

install:  ## Install kõik deps (pre-commit, npm, pip)
	pre-commit install
	pre-commit install --hook-type commit-msg
	cd frontend && npm install --legacy-peer-deps
	cd worker && pip install -r requirements.txt
	cd slicer && pip install -r requirements.txt

## ─── Test ─────────────────────────────────────────────────────────────

test: test-backend test-frontend test-worker test-slicer  ## Kõik testid

test-backend:  ## Backend unit + integration testid
	cd backend && gradle test jacocoTestReport --no-daemon

test-frontend:  ## Frontend unit testid (Karma headless)
	cd frontend && npx ng test --watch=false --browsers=ChromeHeadless

test-worker:  ## Worker pytest
	cd worker && pytest -v --cov=.

test-slicer:  ## Slicer pytest
	cd slicer && pytest -v --cov=.

e2e:  ## Playwright E2E (eeldab et backend + frontend jookseb)
	cd frontend && npx playwright test

load-smoke:  ## k6 smoke test localhost:8080 vastu
	k6 run load-tests/smoke.js

## ─── Lint / format ────────────────────────────────────────────────────

lint:  ## Lint kõik (ei muuda, ainult raporteerib)
	pre-commit run --all-files

format:  ## Parandada formaat (Spotless + Prettier + Ruff)
	cd backend && gradle spotlessApply --no-daemon
	cd frontend && npx prettier --write "src/**/*.{ts,html,scss,json}"
	ruff format worker slicer
	ruff check --fix worker slicer

## ─── Build & run ──────────────────────────────────────────────────────

build:  ## Build kõik artefaktid
	cd backend && gradle build --no-daemon
	cd frontend && npm run build

up:  ## Käivita kogu stack (Docker Compose)
	docker compose -f docker-compose.yml -f docker-compose.infra.yml up -d --build

up-obs:  ## Käivita stack + observability
	docker compose -f docker-compose.yml -f docker-compose.infra.yml -f docker-compose.observability.yml up -d --build

down:  ## Peata kõik
	docker compose -f docker-compose.yml -f docker-compose.infra.yml -f docker-compose.observability.yml down

logs:  ## Jälgi backend'i log'e
	docker compose logs -f backend

## ─── Flyway ───────────────────────────────────────────────────────────

migrate-up:  ## Jookse Flyway migratsioonid
	cd backend && gradle flywayMigrate --no-daemon

migrate-info:  ## Näita Flyway migratsioonide staatust
	cd backend && gradle flywayInfo --no-daemon

## ─── Docker ───────────────────────────────────────────────────────────

docker-build:  ## Build kõik Docker image'id
	docker build -t cad-backend ./backend
	docker build -t cad-frontend ./frontend
	docker build -t cad-worker ./worker
	docker build -t cad-slicer ./slicer

## ─── CI simulatsioon ─────────────────────────────────────────────────

ci-local:  ## Simuleeri CI local'is (pre-commit + test + build)
	pre-commit run --all-files
	$(MAKE) test
	$(MAKE) build

## ─── Cleanup ─────────────────────────────────────────────────────────

clean:  ## Kustuta build artefaktid ja cache'id
	cd backend && gradle clean --no-daemon
	rm -rf frontend/dist frontend/.angular
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name .pytest_cache -exec rm -rf {} + 2>/dev/null || true
