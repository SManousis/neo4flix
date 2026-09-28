.DEFAULT_GOAL := verify
.PHONY: verify verify-all test test-integration security dev-up dev-down seed-demo seed-audit seed-load

verify:
	pwsh -NoProfile -File scripts/verify.ps1

verify-all:
	pwsh -NoProfile -File scripts/verify.ps1
	docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml build
	docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --wait --wait-timeout 600
	pwsh -NoProfile -File scripts/smoke-compose.ps1 -EnvFile .env
	npm.cmd --prefix frontend run e2e
	pwsh -NoProfile -File scripts/security.ps1
	docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 -v "$(CURDIR)/scripts/k6:/scripts:ro" grafana/k6:0.53.0 run --vus 1 --duration 15s /scripts/smoke.js

test:
	pwsh -NoProfile -File scripts/verify.ps1 -TestOnly

test-integration:
	pwsh -NoProfile -File scripts/verify.ps1 -Integration

security:
	pwsh -NoProfile -File scripts/security.ps1

dev-up:
	docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600

dev-down:
	docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml down

seed-demo:
	pwsh -NoProfile -File scripts/seed.ps1 demo

seed-audit:
	pwsh -NoProfile -File scripts/seed.ps1 audit

seed-load:
	pwsh -NoProfile -File scripts/seed.ps1 load
