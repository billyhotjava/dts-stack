# Repository Guidelines

## Project Structure & Module Organization
- Bootstrap scripts live at the root: `init.sh` for first-run setup, `start.sh`/`stop.sh` and `dev-up.sh`/`dev-stop.sh` for runtime control.
- Infrastructure assets sit in `services/`, mirroring Compose keys (`services/dts-trino/`, `services/dts-proxy/`) with `conf/`, `init/`, `data/`, and optional `dynamic/` content per service.
- Application code is in `source/` (e.g., `source/dts-platform`, `source/dts-admin-webapp`) with shared helpers in `source/dts-common`.
- Compose manifests at the root define environments: `docker-compose.yml` (foundation), `docker-compose-app.yml` (application images), and `docker-compose.dev.yml` (hot-reload dev mode).

## Build, Test, and Development Commands
- Run `./init.sh single 'Strong@2025!' dts.local` once to seed `.env`, issue certificates, and start core services.
- Use `./dev-up.sh --mode local` for source-mounted development and `./dev-stop.sh --mode local` to halt.
- For packaged runs, execute `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d`; pair with `down` or targeted `up -d <service>` after rebuilds.
- Monitor health via `docker compose ps`, follow key logs with `docker compose logs -f dts-trino`, and spot-check using `docker compose exec dts-trino wget -qO- http://localhost:8080/v1/info` or `curl -k https://sso.${BASE_DOMAIN}`.

## Coding Style & Naming Conventions
- Bash scripts begin with `set -euo pipefail`, use 2-space indents, `lower_snake_case` functions, and `UPPER_SNAKE_CASE` environment variables.
- YAML keeps 2-space indentation, quotes values containing `:` or templating, and uses kebab-case directory names aligned with Compose keys.
- Follow framework defaults within `source/`; centralize reusable logic in `source/dts-common`.

## Testing Guidelines
- Rely on Compose health: `docker compose ps`, targeted logs, and service endpoints (Trino info probe, SSO curl) instead of heavy integration suites.
- Document manual verification steps alongside code changes and ensure fixtures carry only sanitized sample values.

## Commit & Pull Request Guidelines
- Use Conventional Commit prefixes (`feat:`, `fix:`, `chore:`, `docs:`) and add scopes when helpful (e.g., `feat(dts-proxy): ...`).
- PRs should list affected services, tested modes (`single|ha2|cluster`), configuration or env var changes, and include relevant logs or screenshots.
- When topology shifts, attach `docker compose -f docker-compose.yml config --services` output and highlight updates to `imgversion.conf` or new directories.

## Security & Configuration Tips
- Keep secrets out of version control; regenerate `.env` with `./init.sh` and replace `services/certs/` with CA-signed assets for production.
- Manage image tags in `imgversion.conf`; bump versions there and rerun `./init.sh` to refresh dependent settings.
- Update `BASE_DOMAIN` in `.env` before rerunning `./init.sh` so routes and certificates stay aligned.
