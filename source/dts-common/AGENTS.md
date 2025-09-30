# Repository Guidelines

## Project Structure & Module Organization
- Base package: `com.yuzhi.dts.common`.
- Source code: `src/main/java/...` (packages: `config`, `domain`, `security`, etc.).
- Resources: `src/main/resources` (Spring `config/*.yml`, `templates/`, `static/`).
- Tests: `src/test/java/...` mirror source packages; resources in `src/test/resources`.
- Docker & services: `src/main/docker` (e.g., `services.yml`, `postgresql.yml`, `keycloak.yml`, `app.yml`).

## Build, Test, and Development Commands
- Run in dev: `./mvnw` (default goal `spring-boot:run`).
- All tests + coverage: `./mvnw verify` or `npm run backend:unit:test`.
- Checkstyle/NoHTTP: `npm run backend:nohttp:test`.
- Javadoc: `npm run backend:doc:test`.
- Prod build (jar): `./mvnw -Pprod clean verify` then `java -jar target/*.jar`.
- Start dependencies: `docker compose -f src/main/docker/services.yml up -d`.
- Build & run Docker image: `npm run java:docker` then `docker compose -f src/main/docker/app.yml up -d`.

## Coding Style & Naming Conventions
- Indentation: 4 spaces for `.java`; 2 spaces elsewhere (`.editorconfig`).
- Formatting: Prettier (`.prettierrc`, with Java plugin). Example: `npx prettier -w .`.
- Maven plugins: Checkstyle, Spotless, Modernizer run via Maven lifecycle.
- Naming: packages lower-case; classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.

## Testing Guidelines
- Frameworks: JUnit 5, Spring Boot Test, ArchUnit, Testcontainers; coverage via JaCoCo.
- Naming: unit tests `*Test` (e.g., `WebConfigurerTest`); integration tests `*IT` (e.g., `HibernateTimeZoneIT`).
- Place tests under `src/test/java` mirroring source packages; put test configs under `src/test/resources`.
- Run: `./mvnw verify` before pushing.

## Commit & Pull Request Guidelines
- Messages: concise, imperative (Conventional Commits welcome). Example: `fix(config): correct CORS settings`.
- PRs: one focused change; include description, linked issues, and any config/docker updates.
- Requirements: update/add tests, run `./mvnw verify` and `npm run backend:nohttp:test`, and ensure build is green.

## Security & Configuration Tips
- Profiles: `dev` (default) and `prod`; configure in `src/main/resources/config/*.yml`.
- OAuth2/OIDC: local Keycloak via `docker compose -f src/main/docker/keycloak.yml up`.
- Never commit secrets; prefer environment variables and Spring config placeholders.

